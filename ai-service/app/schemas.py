from typing import List, Literal, get_args

from pydantic import BaseModel, Field

# timeOfDay/mood/color는 원래 추상적인 카테고리라 구체성을 잃을 게 거의 없어서,
# Gemini의 response_schema에 enum으로 강제해 100% 이 목록 안에서만 나오게 한다.
TimeOfDayTag = Literal["아침", "오후", "저녁", "밤"]
MoodTag = Literal["차분함", "활기참", "여유로움", "즐거움", "피곤함", "설렘", "무난함"]
ColorTag = Literal["주황 계열", "파란 계열", "초록 계열", "노란 계열", "보라 계열", "흑백/무채색", "다채로움"]

TIME_OF_DAY_TAGS: tuple = get_args(TimeOfDayTag)
MOOD_TAGS: tuple = get_args(MoodTag)
COLOR_TAGS: tuple = get_args(ColorTag)

# scene/activity는 category(고정 목록, F3 매칭용) + detail(자유 표현, F4/F6 설명용) 이중구조.
# "헬스장"/"헬스클럽" 같은 동의어 문제는 category가 흡수하고("체육시설"),
# "수영장"처럼 구체적인 표현은 detail에 그대로 살아남는다.
SceneCategoryTag = Literal[
    "도서관", "학식당", "카페", "강의실", "벤치", "기숙사",
    "체육시설", "동아리방", "캠퍼스 야외", "교통/이동", "식당", "집", "영화관", "기타",
]
ActivityCategoryTag = Literal[
    "공부", "식사", "휴식", "운동", "이동", "대화/모임", "여가활동", "쇼핑", "문화생활", "기타",
]

SCENE_CATEGORY_TAGS: tuple = get_args(SceneCategoryTag)
ACTIVITY_CATEGORY_TAGS: tuple = get_args(ActivityCategoryTag)


class FeatureExtractRequest(BaseModel):
    userId: str
    date: str  # "2026-07-06"
    imageUrls: List[str] = Field(min_length=3)


class SceneEntry(BaseModel):
    category: str  # SCENE_CATEGORY_TAGS 중 하나로 고정됨 (F3 매칭용)
    detail: str  # 자유 표현, 구체적인 디테일 (F4/F6 설명용, 예: "홍대 감성 카페")


class ActivityEntry(BaseModel):
    category: str  # ACTIVITY_CATEGORY_TAGS 중 하나로 고정됨 (F3 매칭용)
    detail: str  # 자유 표현, 구체적인 디테일 (예: "수영")


class DayFeatures(BaseModel):
    """BE/FE로 나가는 공개 스키마."""

    scene: List[SceneEntry] = []
    timeOfDay: List[str] = []
    mood: List[str] = []
    color: List[str] = []
    activity: List[ActivityEntry] = []
    summary: str = ""


class FeatureExtractResponse(BaseModel):
    userId: str
    date: str
    features: DayFeatures


class DescriptionRequest(BaseModel):
    similarityScore: float
    userA: DayFeatures
    userB: DayFeatures


class DescriptionResponse(BaseModel):
    description: str


class IcebreakerRequest(BaseModel):
    userA: DayFeatures
    userB: DayFeatures


class IcebreakerResponse(BaseModel):
    question: str


class SimilarityRequest(BaseModel):
    userA: DayFeatures
    userB: DayFeatures


class SimilarityResponse(BaseModel):
    score: float
