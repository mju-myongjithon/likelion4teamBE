"""F3(유사도 매칭)는 BE(김재현) 담당 기능입니다.

이 모듈은 기획서 4-3의 가중치 계산식을 그대로 옮긴 참고 구현으로,
BE 개발 전 로컬에서 F2->F4/F6 흐름을 통째로 데모/테스트해보기 위한 용도입니다.
실서비스 매칭 로직(반대 캠퍼스 전체 탐색, DB 쿼리 등)은 BE에서 구현합니다.
"""

from app.schemas import DayFeatures

WEIGHTS = {
    "scene": 30,
    "timeOfDay": 20,
    "activity": 20,
    "mood": 20,
    "color": 10,
}


def _overlap_ratio(a: list, b: list) -> float:
    if not a or not b:
        return 0.0
    set_a, set_b = set(a), set(b)
    return len(set_a & set_b) / len(set_a | set_b)


def _categories(entries: list) -> list:
    """scene/activity는 [{category, detail}] 형태라 category만 뽑아서 비교한다."""
    return [entry.category for entry in entries]


def calculate_similarity(a: DayFeatures, b: DayFeatures) -> float:
    score = 0.0
    for field, weight in WEIGHTS.items():
        if field in ("scene", "activity"):
            values_a, values_b = _categories(getattr(a, field)), _categories(getattr(b, field))
        else:
            values_a, values_b = getattr(a, field), getattr(b, field)
        score += _overlap_ratio(values_a, values_b) * weight
    return round(score, 1)
