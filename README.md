# Sync.day
<img width="545" height="261" alt="스크린샷 2026-07-19 18 19 00" src="https://github.com/user-attachments/assets/9a68315d-75c7-4741-b281-7a4060f1b238" />


명지톤 2026 | 인문캠 x 자연캠 연합 해커톤 | 🦁 멋쟁이사조 4조

[Syncday_발표자료.pdf](https://github.com/user-attachments/files/30163232/Syncday_.pdf)

---

## 소개

두 캠퍼스에서 오늘 가장 비슷한 하루를 보낸 학생을, AI가 사진 기반으로 찾아 연결해주는 서비스입니다.

인문캠과 자연캠은 같은 학교지만 물리적으로 멀리 떨어져 있어 서로의 일상을 알 기회가 거의 없습니다. Sync.day는 학생들이 오늘 찍은 일상 사진을 AI가 분석해, 지금 이 순간 가장 닮은 하루를 보낸 반대 캠퍼스 학생을 찾아 보여줍니다. 대화나 자기소개 없이도 "우리 오늘 비슷한 하루를 보냈네요"라는 사실 하나만으로 교류가 시작됩니다.

**핵심 가설**: 교류는 "말을 걸어야 하는 부담"이 아니라 "이미 닮아 있던 것을 발견하는 즐거움"에서 더 쉽게 시작된다.

---

## 서비스 흐름

1. **온보딩**: 닉네임 + 캠퍼스 선택 (회원가입 없이 UUID 발급) 
2. **사진 업로드**: 오늘 하루 중 찍은 사진 최소 3장 업로드 (프라이버시 모드 기본 ON, 얼굴만 자동 모자이크 · AWS Rekognition)
3. **오늘의 나를 분석하기**: Gemini Vision이 사진들을 분석해 하루의 특징을 추출·요약
4. **게이트 1 — 매칭 참여 의사 확인**: 반대 캠퍼스 학생과의 매칭에 참여할지 선택 (opt-in), 참여 시 유사도 최상위 상대와 자동 매칭
5. **게이트 2 — 상대 공개 및 채팅 수락**: 매칭된 상대의 닉네임·캠퍼스·오늘 사진(모자이크 처리)을 먼저 보여주고, 대화를 시작할지 양쪽이 각자 선택
6. **연결 완료**: 양쪽 모두 수락하면 유사도(%)·AI가 생성한 한 줄 코멘트가 공개되고 채팅이 열림

---

## 기술 스택

| 영역 | 기술 |
|---|---|
| Frontend | React |
| Backend | Spring Boot 3.5 (Java 21, Spring Data JPA) |
| AI Service | Python FastAPI (독립 컨테이너, BE와 REST로 통신) |
| Database | PostgreSQL |
| Infra | AWS Lightsail (Ubuntu 24.04), Docker Compose, Caddy (자동 HTTPS) |
| 이미지 저장 | AWS S3 (`userId/randomUUID.ext` 키 구조) |
| 얼굴 감지/모자이크 | AWS Rekognition |
| AI 이미지 분석 · 설명 생성 | Gemini 2.5 Flash |

### 아키텍처
<img width="1335" height="756" alt="스크린샷 2026-07-19 18 16 17" src="https://github.com/user-attachments/assets/17a05e20-4108-45e3-bc8b-53fb49364267" />

---

## 팀 구성

| 담당 | 이름 | 기능 |
|---|---|---|
| BE (인프라 리드) | 박찬 | F1 — 사진 업로드/S3/프라이버시, F7 — 스트릭, 서버 인프라 전반 |
| FE,BE | 김재현 | F0 - 게스트 프로필 입력, F3 — 유사도 매칭, 전반적인 UI/UX |
| BE | 윤채영 | F5 — 매칭 발견·양방향 공개, 채팅 |
| AI | 강지원 | F2 + F4 — 특징 추출, 설명 생성 · F6 — 아이스브레이킹 |
| FE | 박지훈 | 업로드/대기/분석 결과 화면 |

---

## 기능 목록

| No. | 기능명 | 상태 |
|---|---|---|
| F0 | 게스트 프로필 입력 | ✅ 완료 |
| F1 | 사진 업로드 & 프라이버시 처리 | ✅ 완료 |
| F2 | AI 사진 특징 분석 | ✅ 완료 |
| F3 | 유사도 매칭 (게이트 1: 참여 의사 확인) | ✅ 완료 |
| F4 | AI 감성 설명 카드 생성 | ✅ 완료 |
| F5 | 매칭 발견 · 양방향 공개 (게이트 2) | ✅ 완료 |
| F6 | 아이스브레이킹 질문 생성 | ✅ 완료 |
| F7 | 스트릭(연속 참여) 기록 | ✅ 완료 |
| F8 | 유사도 근거 상세 보기 | ⏸ 미구현 |
| F9 | 친구 신청 | ⏸ 미구현 |

핵심 플로우(F0 → F1 → F2 → F3 → F4 → F5)는 데모 당일 기준 end-to-end로 정상 동작을 확인했습니다. 

---
## 로컬 실행

```bash
# 1. Backend + AI Service + DB 전체 실행
docker compose up --build

# 2. AI Service만 단독 실행 (개발 중)
cd ai-service
python -m venv .venv
source .venv/Scripts/activate   # windows git-bash
pip install -r requirements.txt
cp .env.example .env            # GEMINI_API_KEY 채워넣기
uvicorn app.main:app --reload --port 8000
```

> IntelliJ `bootRun`으로 BE만 로컬 실행 시, AI Service 호출 URL을 `http://ai-service:8000`이 아닌 `http://localhost:8000`으로 설정해야 합니다 (Docker 내부 서비스명은 Compose 네트워크 안에서만 resolve됩니다).

---

## 협업 규칙

- 브랜치 전략: `dev` 직접 작업 금지 → 기능별 브랜치(`feat/기능명`) → PR → 팀원 리뷰 후 병합

```bash
git checkout dev
git pull origin dev
git checkout -b feat/기능이름
# 작업 후
git add .
git commit -m "feat: 기능 설명"
git push origin feat/기능이름
# GitHub에서 PR 생성 후 리뷰 → merge
```

---

## 채널

- Discord: 공지, 팀 소통
- GitHub (Myongjithon): 팀별 코드 저장소
- Notion: 문서, 일정 관리
