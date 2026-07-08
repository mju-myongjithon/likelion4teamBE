from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from app.schemas import (
    DescriptionRequest,
    DescriptionResponse,
    FeatureExtractRequest,
    FeatureExtractResponse,
    IcebreakerRequest,
    IcebreakerResponse,
    SimilarityRequest,
    SimilarityResponse,
)
from app.services.gemini_client import (
    extract_day_features,
    generate_description,
    generate_icebreaker,
)
from app.services.similarity import calculate_similarity

app = FastAPI(title="Sync.day AI Service", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/api/v1/features", response_model=FeatureExtractResponse)
def extract_features(req: FeatureExtractRequest):
    """F2: 사진 3장 이상을 입력받아 오늘 하루의 특징(장소/시간대/분위기/색감/활동)을 추출한다."""
    try:
        features = extract_day_features(req.imageUrls)
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"특징 추출 실패: {e}")
    return FeatureExtractResponse(userId=req.userId, date=req.date, features=features)


@app.post("/api/v1/description", response_model=DescriptionResponse)
def describe_match(req: DescriptionRequest):
    """F4: 매칭된 두 학생의 특징과 유사도 점수를 받아, 왜 닮았는지 자연어 설명을 생성한다."""
    try:
        description = generate_description(req.userA, req.userB, req.similarityScore)
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"설명 생성 실패: {e}")
    return DescriptionResponse(description=description)


@app.post("/api/v1/icebreaker", response_model=IcebreakerResponse)
def icebreaker(req: IcebreakerRequest):
    """F6: 매칭된 두 학생의 공통점을 기반으로 아이스브레이킹 질문을 생성한다."""
    try:
        question = generate_icebreaker(req.userA, req.userB)
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"아이스브레이킹 질문 생성 실패: {e}")
    return IcebreakerResponse(question=question)


@app.post("/api/v1/_debug/similarity", response_model=SimilarityResponse)
def debug_similarity(req: SimilarityRequest):
    """F3(BE 담당)를 흉내내는 로컬 데모/테스트용 엔드포인트. 실제 매칭에는 사용하지 않는다."""
    score = calculate_similarity(req.userA, req.userB)
    return SimilarityResponse(score=score)


# 로컬 브라우저 테스트용 정적 페이지. API 라우트들 뒤에 마운트해야 경로 충돌이 없다.
app.mount("/", StaticFiles(directory="app/static", html=True), name="static")
