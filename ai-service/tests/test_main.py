from fastapi.testclient import TestClient

from app.main import app
from app.schemas import DayFeatures

client = TestClient(app)

SAMPLE_FEATURES_A = DayFeatures(
    scene=[{"category": "도서관", "detail": "학교 중앙도서관"}, {"category": "벤치", "detail": "캠퍼스 벤치"}],
    timeOfDay=["아침", "저녁"],
    mood=["차분함"],
    color=["주황 계열"],
    activity=[{"category": "공부", "detail": "시험 공부"}, {"category": "휴식", "detail": "산책"}],
    summary="아침 햇살로 시작해 도서관에서 마무리한 하루",
)

SAMPLE_FEATURES_B = DayFeatures(
    scene=[{"category": "도서관", "detail": "동네 작은 도서관"}, {"category": "학식당", "detail": "학생식당"}],
    timeOfDay=["아침", "저녁"],
    mood=["여유로움"],
    color=["주황 계열"],
    activity=[{"category": "공부", "detail": "과제"}, {"category": "식사", "detail": "학식"}],
    summary="공부와 식사로 채워진 하루",
)


def test_health():
    res = client.get("/health")
    assert res.status_code == 200
    assert res.json() == {"status": "ok"}


def test_extract_features_requires_min_three_images():
    res = client.post(
        "/api/v1/features",
        json={"userId": "u1", "date": "2026-07-06", "imageUrls": ["a.jpg", "b.jpg"]},
    )
    assert res.status_code == 422


def test_extract_features(monkeypatch):
    monkeypatch.setattr(
        "app.main.extract_day_features", lambda urls: SAMPLE_FEATURES_A
    )
    res = client.post(
        "/api/v1/features",
        json={
            "userId": "u1",
            "date": "2026-07-06",
            "imageUrls": ["a.jpg", "b.jpg", "c.jpg"],
        },
    )
    assert res.status_code == 200
    body = res.json()
    assert body["userId"] == "u1"
    assert body["features"]["scene"][0]["category"] == "도서관"
    assert body["features"]["scene"][0]["detail"] == "학교 중앙도서관"


def test_description(monkeypatch):
    monkeypatch.setattr(
        "app.main.generate_description", lambda a, b, score: "두 분 모두 도서관에서 공부하며 하루를 마무리했어요."
    )
    res = client.post(
        "/api/v1/description",
        json={
            "similarityScore": 82.5,
            "userA": SAMPLE_FEATURES_A.model_dump(),
            "userB": SAMPLE_FEATURES_B.model_dump(),
        },
    )
    assert res.status_code == 200
    assert "도서관" in res.json()["description"]


def test_icebreaker(monkeypatch):
    monkeypatch.setattr(
        "app.main.generate_icebreaker", lambda a, b: "오늘 두 분 다 도서관에 가셨네요! 무슨 공부 하셨나요?"
    )
    res = client.post(
        "/api/v1/icebreaker",
        json={
            "userA": SAMPLE_FEATURES_A.model_dump(),
            "userB": SAMPLE_FEATURES_B.model_dump(),
        },
    )
    assert res.status_code == 200
    assert res.json()["question"].endswith("?")


def test_debug_similarity():
    res = client.post(
        "/api/v1/_debug/similarity",
        json={
            "userA": SAMPLE_FEATURES_A.model_dump(),
            "userB": SAMPLE_FEATURES_B.model_dump(),
        },
    )
    assert res.status_code == 200
    score = res.json()["score"]
    assert 0 < score < 100
