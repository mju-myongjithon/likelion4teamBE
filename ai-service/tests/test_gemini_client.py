from app.schemas import ActivityEntry, DayFeatures, SceneEntry
from app.services.gemini_client import _sanitize_photo_indices


def _features(scene_indices, activity_indices):
    return DayFeatures(
        scene=[SceneEntry(category="카페", detail="d", photoIndex=i) for i in scene_indices],
        activity=[ActivityEntry(category="공부", detail="d", photoIndex=i) for i in activity_indices],
    )


def test_keeps_in_range_indices():
    features = _features(scene_indices=[0, 2], activity_indices=[1])

    result = _sanitize_photo_indices(features, photo_count=3)

    assert [s.photoIndex for s in result.scene] == [0, 2]
    assert [a.photoIndex for a in result.activity] == [1]


def test_drops_out_of_range_indices():
    # 사진은 3장(유효 인덱스 0~2)인데 모델이 3, -1처럼 범위 밖 값을 낸 경우.
    features = _features(scene_indices=[0, 3], activity_indices=[-1])

    result = _sanitize_photo_indices(features, photo_count=3)

    assert [s.photoIndex for s in result.scene] == [0, None]
    assert [a.photoIndex for a in result.activity] == [None]


def test_leaves_missing_index_as_none():
    features = _features(scene_indices=[None], activity_indices=[None])

    result = _sanitize_photo_indices(features, photo_count=3)

    assert result.scene[0].photoIndex is None
    assert result.activity[0].photoIndex is None
