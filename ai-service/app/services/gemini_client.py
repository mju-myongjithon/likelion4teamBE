import base64
import json
import logging
import re
import time
from typing import List, Optional

import httpx
from google import genai
from google.genai import errors as genai_errors
from google.genai import types
from pydantic import BaseModel

logger = logging.getLogger(__name__)

from app.config import settings
from app.prompts import (
    DESCRIPTION_SYSTEM_PROMPT,
    FEATURE_EXTRACTION_SYSTEM_PROMPT,
    ICEBREAKER_SYSTEM_PROMPT,
)
from app.schemas import ActivityCategoryTag, ColorTag, DayFeatures, MoodTag, SceneCategoryTag, TimeOfDayTag


class _SceneEntrySchema(BaseModel):
    category: SceneCategoryTag  # 고정 목록만 허용 (F3 매칭용, 100% 강제)
    detail: str  # 자유 표현 (F4/F6 설명용, 예: "홍대 감성 카페")
    photoIndex: int  # 몇 번째 사진(0-based)에서 관찰됐는지


class _ActivityEntrySchema(BaseModel):
    category: ActivityCategoryTag
    detail: str
    photoIndex: int


class _FeatureExtractionSchema(BaseModel):
    """Gemini에게 강제하는 내부용 스키마.

    timeOfDay/mood/color는 app/schemas.py의 고정 목록 중에서만 나오도록 enum으로 강제한다.
    scene/activity는 category(고정 목록)+detail(자유 표현) 이중구조로 강제해서,
    매칭 정확도(category)와 설명 구체성(detail)을 동시에 확보한다.
    """

    scene: List[_SceneEntrySchema]
    timeOfDay: List[TimeOfDayTag]
    mood: List[MoodTag]
    color: List[ColorTag]
    activity: List[_ActivityEntrySchema]
    summary: str

_client: Optional[genai.Client] = None

_DATA_URI_RE = re.compile(r"^data:(?P<mime>[^;]+);base64,(?P<data>.+)$", re.DOTALL)

_RETRY_BACKOFF_SECONDS = (1, 3, 8)

_MARKDOWN_EMPHASIS_RE = re.compile(r"\*\*(.+?)\*\*")


def _strip_markdown(text: str) -> str:
    return _MARKDOWN_EMPHASIS_RE.sub(r"\1", text).strip()


def get_client() -> genai.Client:
    global _client
    if _client is None:
        if not settings.gemini_api_key:
            raise RuntimeError("GEMINI_API_KEY가 설정되지 않았습니다. .env 파일을 확인하세요.")
        _client = genai.Client(api_key=settings.gemini_api_key)
    return _client


_RETRYABLE_CODES = {429, 500, 503, 504}


def _generate_with_retry(**kwargs):
    """레이트리밋(429)이나 구글 쪽 일시적 장애(5xx)에 걸렸을 때 잠깐 쉬었다가 재시도한다.

    실제 Gemini 호출 횟수를 로그로 남겨서, 할당량이 예상보다 빨리 줄어들 때
    원인(재시도 증폭)을 추적할 수 있게 한다.
    """
    client = get_client()
    last_error = None
    for attempt, delay in enumerate((*_RETRY_BACKOFF_SECONDS, None), start=1):
        logger.info("Gemini API 호출 시도 %d/%d (model=%s)", attempt, len(_RETRY_BACKOFF_SECONDS) + 1, kwargs.get("model"))
        try:
            return client.models.generate_content(**kwargs)
        except genai_errors.APIError as e:
            last_error = e
            if e.code not in _RETRYABLE_CODES or delay is None:
                raise
            time.sleep(delay)
    raise last_error


def _load_image_part(url: str) -> types.Part:
    """S3 등 외부 http(s) URL과 브라우저에서 만든 base64 data URI를 모두 지원한다."""
    match = _DATA_URI_RE.match(url)
    if match:
        data = base64.b64decode(match.group("data"))
        mime_type = match.group("mime")
    else:
        response = httpx.get(url, timeout=20)
        response.raise_for_status()
        data = response.content
        mime_type = response.headers.get("content-type", "image/jpeg")
    return types.Part.from_bytes(data=data, mime_type=mime_type)


def _sanitize_photo_indices(features: DayFeatures, photo_count: int) -> DayFeatures:
    """Gemini에게 photoIndex를 정확히 채우라고 프롬프트로 지시해도, 스키마가 강제하는 건
    "정수 하나"일 뿐 그 값이 실제 사진 개수 범위 안인지는 보장 못 한다(모델이 가끔
    범위 밖 값을 낼 수 있음). 여기서 걸러내지 않으면 FE가 photos[photoIndex]로 접근할 때
    엉뚱한 사진이 매칭되는 문제가 다른 경로로 재발한다. 범위 밖이면 그 항목의 photoIndex만
    비워서(None) FE가 이미 갖고 있는 폴백(순서대로 돌려붙이기)으로 안전하게 넘어가게 한다."""

    def clean(entry):
        if entry.photoIndex is not None and not (0 <= entry.photoIndex < photo_count):
            return entry.model_copy(update={"photoIndex": None})
        return entry

    features.scene = [clean(s) for s in features.scene]
    features.activity = [clean(a) for a in features.activity]
    return features


def extract_day_features(image_urls: List[str]) -> DayFeatures:
    parts = [
        types.Part.from_text(
            text="다음은 한 학생이 오늘 하루 동안 찍은 사진들입니다. 모두 종합해서 하루의 특징을 분석해주세요. "
            "각 사진에는 '사진 N'이라는 번호(0부터 시작)를 붙였으니, scene/activity의 photoIndex를 채울 때 이 번호를 그대로 쓰세요."
        )
    ]
    for i, url in enumerate(image_urls):
        parts.append(types.Part.from_text(text=f"사진 {i}:"))
        parts.append(_load_image_part(url))

    generation_kwargs = dict(
        model=settings.vision_model,
        contents=parts,
        config=types.GenerateContentConfig(
            system_instruction=FEATURE_EXTRACTION_SYSTEM_PROMPT,
            response_mime_type="application/json",
            response_schema=_FeatureExtractionSchema,
            temperature=0.3,
        ),
    )

    # 1차 호출: 네트워크/레이트리밋(429, 5xx)에 대해서만 백오프 재시도 (최대 4회)
    response = _generate_with_retry(**generation_kwargs)
    try:
        data = json.loads(response.text)
        return _sanitize_photo_indices(DayFeatures(**data), len(image_urls))
    except (json.JSONDecodeError, TypeError, ValueError):
        # 모델이 스키마에서 벗어난 JSON을 내놓는 드문 경우, 백오프 없이 딱 1번만 더 호출한다.
        # (_generate_with_retry를 또 부르면 재시도가 중첩되어 최악의 경우 호출이 크게 늘어남)
        logger.warning("특징 추출 응답 파싱 실패, 추가 호출 1회 시도")
        client = get_client()
        response = client.models.generate_content(**generation_kwargs)
        data = json.loads(response.text)
        return _sanitize_photo_indices(DayFeatures(**data), len(image_urls))


def generate_description(user_a: DayFeatures, user_b: DayFeatures, score: float) -> str:
    user_prompt = (
        f"유사도 점수: {score}%\n"
        f"학생 A 특징: {user_a.model_dump()}\n"
        f"학생 B 특징: {user_b.model_dump()}\n\n"
        "위 두 학생의 오늘 하루가 왜 닮았는지, 공통점을 중심으로 설명해주세요."
    )

    response = _generate_with_retry(
        model=settings.text_model,
        contents=user_prompt,
        config=types.GenerateContentConfig(
            system_instruction=DESCRIPTION_SYSTEM_PROMPT,
            temperature=0.7,
            # 짧은 문장 생성이라 추론(thinking)이 불필요 — 꺼두면 지연시간이 크게 줄어든다
            # (실측: 평균 6.4s -> 1.0s, gemini-2.5-flash 기준).
            thinking_config=types.ThinkingConfig(thinking_budget=0),
        ),
    )
    return _strip_markdown(response.text)


def generate_icebreaker(user_a: DayFeatures, user_b: DayFeatures) -> str:
    user_prompt = (
        f"학생 A 특징: {user_a.model_dump()}\n"
        f"학생 B 특징: {user_b.model_dump()}\n\n"
        "두 학생의 공통 활동/장소 중 가장 구체적이고 흥미로운 것 하나를 골라서 "
        "그것에 대해 물어보는 아이스브레이킹 질문을 만들어주세요."
    )

    response = _generate_with_retry(
        model=settings.text_model,
        contents=user_prompt,
        config=types.GenerateContentConfig(
            system_instruction=ICEBREAKER_SYSTEM_PROMPT,
            temperature=0.8,
            thinking_config=types.ThinkingConfig(thinking_budget=0),
        ),
    )
    return _strip_markdown(response.text)
