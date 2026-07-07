# F3 ← F2 핸드오프 문서

> **목적**: F3(유사도 매칭)가 F2(AI 하루 분석)로부터 확정받아야 하는 데이터 계약을 정리한다.
> F3는 아래 항목들을 잠정 가정하고 선행 개발 중이며, F2 확정 시 표시된 위치만 수정하면 되도록 설계했다.
>
> - F2 담당: 강지원 · F3 담당: 김재현
> - 대상 테이블: `daily_analysis`(F2 산출) → `match`(F3 산출)

---

## 1. 한눈에 보기 — F2에게 요청하는 것

`daily_analysis`의 각 필드에 대해 아래를 확정해 주세요.

| 필드 | 타입 | F2에게 묻는 것 |
|---|---|---|
| `scene_tags` | text[] | 고정 어휘 목록? 표기 규약(언어/대소문자/단복수)? 최대 개수? |
| `activity_tags` | text[] | 위와 동일 |
| `time_of_day` | varchar | 가능한 값 집합? (예: `아침/오후/저녁/밤`) 순서 개념 있는지? |
| `mood` | varchar | 가능한 값 목록과 개수? |
| `dominant_color` | varchar | **색 이름**인지 **hex**인지? 단일 값인지? |
| (전체) | — | **"분석 완료"를 무엇으로 판정**하게 할지 (status 컬럼 추가 여부) |
| (전체) | — | 분석 저장 후 **매칭을 어떻게 트리거**할지 |

---

## 2. F3가 현재 두고 있는 가정 (확정 시 수정 지점 포함)

### 🔴 고위험 — 값/규약 확정 전엔 정확도·정합성 미보장

**A. `dominant_color` = 색 이름 문자열**
색을 `"블루"` 같은 이름으로 준다고 가정하고 **완전일치**로 비교합니다. hex(`#3A7BD5`)로 오면 완전일치가 거의 항상 0이 되어 이 차원이 무력화됩니다. 그 경우 색거리 계산으로 교체해야 합니다.
- 수정 지점: `SimilarityCalculator.exactSim()` → 색거리 함수

**B. `scene_tags` / `activity_tags` vocabulary 미통제**
고정 어휘가 아니어도 자카드(겹침)로 계산합니다. 어휘가 통제되지 않으면 의미상 같은 태그(`"카페"` vs `"커피숍"`)도 문자열이 달라 유사도가 낮게 나옵니다. **매칭 정확도를 좌우하는 가장 큰 변수**입니다.
- 수정 지점: 동의어 매핑/정규화 레이어 추가 여부

**C. "분석 완료" 판단 = row 존재**
`daily_analysis`에 **status 컬럼이 없어** row가 있으면 완료로 보고 후보에 넣는다고 가정합니다. F2가 status 컬럼을 추가할지, `raw_ai_response` non-null로 판정할지 확정이 필요합니다. **매칭 후보 쿼리의 블로커.**
- 수정 지점: `MatchService` 후보 조회 쿼리 조건

**D. `campus` = 정확히 2종**
`campus <> targetCampus`로 반대 캠퍼스를 판정한다고 가정합니다. 실제 값(`인문/자연` vs 영문)은 미확인입니다.
- 수정 지점: `MatchService` 후보 조회 WHERE 절

### 🟠 저위험 — 방어됐거나 근거가 강함

**E. `time_of_day` = 한글 4단계 순서**
`아침 → 오후 → 저녁 → 밤` 순서를 가정하고 인접할수록 유사하게 계산합니다. 순서표에 없는 값은 완전일치로만 폴백해 크래시는 없습니다. 값이 다르면 맵 한 곳만 교체합니다.
- 수정 지점: `SimilarityCalculator.TIME_ORDER`

**F. 태그 정규화 = F3가 방어**
F2가 정규화를 안 해줄 수 있다고 보고 trim+소문자로 이중 방어합니다. `" Cafe "`와 `"cafe"`는 매칭되지만 동의어 병합은 못 합니다(→ B 참조).
- 조치 완료: `SimilarityCalculator.normalize()`

**G. 분석 단위 = 유저 × 날짜 1건**
사진 여러 장이 하나의 `daily_analysis`로 묶이는 하루 단위를 가정합니다. ERD가 `user_id + date` 구조라 근거가 강합니다.
- 반영: `AnalysisFeatures`가 하루 단위 5필드로 설계됨

---

## 3. 필드별 결측 규약 요청

| 상황 | F3 기대 동작 | F2 확정 필요 |
|---|---|---|
| 태그 없음 | 빈 배열 `[]` (null도 방어함) | `[]` vs `null` 중 무엇으로? |
| mood/time/color 못 뽑음 | null → sim 0 | `null` vs `"unknown"` 문자열? |
| 분석 실패 | 후보에서 제외 | 실패 row를 남기는지, status로 구분하는지 |

---

## 4. 매칭 트리거 (택1 협의 필요)

- **(권장) 이벤트/후크**: F2가 `daily_analysis` 저장 완료 후 `MatchService.createMatchForUser(userId, date)` 호출
- **온디맨드**: FE가 "매칭하기" 클릭 → `POST /api/matches`
- **배치**: 스케줄러가 미매칭 유저 주기 스윕

> F3는 세 방식 모두를 수용할 수 있게 서비스 메서드를 공개할 예정. F2/FE와 합의만 하면 됨.

---

## 5. F3가 F2 방향으로 넘기는 계약 (참고)

F3 산출물 `match.score_breakdown`(jsonb)은 F4(감성 설명)·F8(근거 상세)이 소비합니다. 구조는 아래 형태입니다.

```json
{
  "totalScore": 78,
  "dimensions": {
    "scene":    { "sim": 0.67, "weight": 0.30, "contribution": 20, "commonTags": ["카페","야외"] },
    "activity": { "sim": 1.00, "weight": 0.30, "contribution": 30, "commonTags": ["공부"] },
    "mood":     { "sim": 1.00, "weight": 0.20, "contribution": 20, "valueA": "차분함", "valueB": "차분함" },
    "time":     { "sim": 0.67, "weight": 0.10, "contribution": 7,  "valueA": "오후", "valueB": "저녁" },
    "color":    { "sim": 0.00, "weight": 0.10, "contribution": 0,  "valueA": "블루", "valueB": "베이지" }
  }
}
```

---

## 6. 통합 시 주의

- **`DailyAnalysis` 엔티티는 F3가 잠정 정의한 읽기 모델**입니다. 실제 소유는 F2이므로, F2 구현 병합 시 하나로 **일원화**해야 합니다(중복 `@Entity` 매핑 충돌 방지).
- 값 규약이 확정되면 이 문서의 🔴 항목부터 코드 수정 지점을 갱신하세요.
