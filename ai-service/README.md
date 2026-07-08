# Sync.day — AI Service (F2 / F4 / F6)

강지원 담당: 사진 특징 추출(F2), AI 설명 카드 생성(F4), 아이스브레이킹 질문 생성(F6).
Spring Boot BE가 REST로 호출하는 독립 FastAPI 서비스입니다. (F1 업로드/S3, F3 매칭, F5/F7 공개·스트릭은 BE 담당)

## 실행 방법

```bash
python -m venv .venv
source .venv/Scripts/activate   # windows git-bash
pip install -r requirements.txt
cp .env.example .env            # GEMINI_API_KEY 채워넣기
uvicorn app.main:app --reload --port 8000
```

테스트 (Gemini 키 없이도 동작, 전부 mock):

```bash
pytest -q
```

## API 스펙 (BE 연동용)

베이스 URL: `http://<ai-server-host>:8000`

### F2 — POST `/api/v1/features`

사진(최소 3장, S3 URL) → 오늘 하루 특징 추출.

요청
```json
{
  "userId": "u123",
  "date": "2026-07-06",
  "imageUrls": ["https://s3.../1.jpg", "https://s3.../2.jpg", "https://s3.../3.jpg"]
}
```

응답
```json
{
  "userId": "u123",
  "date": "2026-07-06",
  "features": {
    "scene": [
      { "category": "카페", "detail": "홍대 감성 카페" },
      { "category": "체육시설", "detail": "수영장" }
    ],
    "timeOfDay": ["아침", "저녁"],
    "mood": ["차분함"],
    "color": ["주황 계열"],
    "activity": [
      { "category": "운동", "detail": "수영" },
      { "category": "휴식", "detail": "카페에서 음료 마시며 휴식" }
    ],
    "summary": "아침 수영으로 상쾌하게 시작해 홍대 감성 카페에서 하루를 마무리했어요."
  }
}
```

`features`는 BE가 그대로 DB에 저장했다가 F3(매칭) 계산과 F4/F6 호출 시 다시 넘겨주면 됩니다.

**중요**: 필드마다 값이 나오는 방식이 다릅니다. F3에서 매칭 로직 짤 때 이 차이를 알고 있어야 합니다.

**1) `timeOfDay`/`mood`/`color` — 문자열 배열, 완전 고정 (API 레벨에서 강제, 100% 이 목록 안에서만 나옴)**

| 필드 | 가능한 값 |
| --- | --- |
| timeOfDay | 아침, 오후, 저녁, 밤 |
| mood | 차분함, 활기참, 여유로움, 즐거움, 피곤함, 설렘, 무난함 |
| color | 주황 계열, 파란 계열, 초록 계열, 노란 계열, 보라 계열, 흑백/무채색, 다채로움 |

**2) `scene`/`activity` — `{category, detail}` 객체 배열, 이중구조 (박찬님 제안 반영)**

- `category`: 아래 고정 목록 중 하나로 **100% 강제**됩니다. **F3 매칭은 이 필드만 문자열 비교하면 됩니다.**
- `detail`: 자유 문장/단어로, category보다 구체적인 실제 묘사입니다. **매칭에는 쓰지 말고 F4/F6이 감성 문장 만들 때 참고합니다.**

| 필드 | category 가능한 값 |
| --- | --- |
| scene | 도서관, 학식당, 카페, 강의실, 벤치, 기숙사, 체육시설, 동아리방, 캠퍼스 야외, 교통/이동, 식당, 집, 영화관, 기타 |
| activity | 공부, 식사, 휴식, 운동, 이동, 대화/모임, 여가활동, 쇼핑, 문화생활, 기타 |

이 구조 덕분에 "헬스장"/"헬스클럽"처럼 표현이 갈려도 `category`는 둘 다 "체육시설"로 강제되어 매칭이 깨지지 않고,
동시에 `detail`에는 "수영장"처럼 구체적인 표현이 살아있어서 F4 설명 문장이 밋밋해지지 않습니다.

카테고리를 늘리거나 바꾸고 싶으면 `app/schemas.py`(`SceneCategoryTag`/`ActivityCategoryTag`)와 `app/prompts.py`를 수정하면 됩니다.
이미지 URL은 얼굴 모자이크가 이미 적용된 상태(BE의 F1에서 Rekognition으로 처리)라고 가정합니다.

### F4 — POST `/api/v1/description`

F3에서 매칭이 확정된 두 사용자의 `features` + 유사도 점수 → 감성 설명 문장.

요청
```json
{
  "similarityScore": 82.5,
  "userA": { "scene": [...], "timeOfDay": [...], "mood": [...], "color": [...], "activity": [...], "summary": "..." },
  "userB": { "...": "..." }
}
```

응답
```json
{ "description": "두 분 모두 아침 햇살로 하루를 시작했고, 저녁에는 도서관에서 하루를 마무리했어요." }
```

### F6 — POST `/api/v1/icebreaker`

매칭된 두 사용자의 `features` → 공통점 기반 아이스브레이킹 질문 1개.

요청
```json
{
  "userA": { "...": "..." },
  "userB": { "...": "..." }
}
```

응답
```json
{ "question": "오늘 두 분 다 카페에 가셨네요! 뭐 드셨나요?" }
```

### (참고) POST `/api/v1/_debug/similarity`

F3(BE 담당) 로직을 흉내낸 로컬 데모용 엔드포인트입니다. 기획서 4-3의 가중치(장소 30 / 시간대 20 / 활동 20 / 분위기 20 / 색감 10)로
카테고리 교집합 비율을 계산합니다. BE가 F3를 붙이기 전, F2→F4/F6 흐름을 로컬에서 통으로 데모할 때만 쓰고 실제 매칭에는 쓰지 않습니다.

## 에러 처리

- `imageUrls`가 3장 미만이면 `422 Validation Error`.
- Gemini 호출이 429(레이트리밋)로 실패하면 1초/3초/8초 간격으로 자동 재시도합니다(`_generate_with_retry`). 그래도 실패하거나 다른 에러(이미지 URL 접근 불가 등)면 `502`와 함께 `detail` 메시지 반환.
- 비전 모델이 스키마에서 벗어난 JSON을 내놓는 드문 경우, 파싱 실패 시 한 번 더 재시도합니다.

## 참고

- 비전 분석 + 텍스트 생성 모두 Google Gemini API 사용 (`app/config.py`에서 모델명 조정 가능, 기본 `gemini-2.5-flash`). 신용카드 등록 없이 구글 계정만으로 무료 API 키 발급 가능.
  - `gemini-2.0-flash`는 프로젝트에 따라 무료 할당량이 0으로 잡히는 경우가 있어 `gemini-2.5-flash`로 기본값을 맞춰뒀습니다. 만약 또 할당량 에러가 나면 `.env`의 `AI_VISION_MODEL`/`AI_TEXT_MODEL`을 `gemini-flash-latest` 등으로 바꿔보세요.
  - 무료 티어는 분당/일일 요청 수 제한이 있습니다. 팀 회의/발표 직전에 테스트를 너무 많이 돌리면 정작 필요할 때 막힐 수 있으니, 핵심 흐름만 확인하고 아껴 쓰는 걸 추천합니다.
- `imageUrls`는 `https://` URL과 브라우저에서 만든 base64 data URI(`data:image/jpeg;base64,...`) 둘 다 지원합니다 (`app/services/gemini_client.py`의 `_load_image_part`).
- 응답에 마크다운(`**볼드**` 등)이 섞여 나오는 걸 막기 위해 프롬프트로 금지 + 코드에서 한 번 더 제거합니다(`_strip_markdown`).
- 프롬프트는 `app/prompts.py`에 모아뒀습니다. 결과 카드 문구 톤을 바꾸고 싶으면 여기만 수정하면 됩니다. F4 설명이 너무 추상적으로 나오면(예: "여유로움을 느꼈다") 프롬프트의 좋은 예/나쁜 예 부분을 참고해서 조정하세요.
