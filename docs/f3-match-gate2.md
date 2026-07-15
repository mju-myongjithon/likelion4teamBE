# F3 매칭 — 게이트2(채팅 참여) 설계

> 상태: **구현 완료, `dev` 병합됨** (F5와 확정할 항목은 "코딩 전 F5와 확정할 열린 항목" 참고)
> 대상 브랜치: `feat/f3-match-chat-optin-api`
> 관련 화면: 2b3 매칭 발견 · 2b4 상대 응답 대기 · 2c 매칭 완료 · 2d 매칭 종료

## 배경

원래 F3는 아래 유저 플로우를 가정하고 구현됐다.

```
사진 업로드 → 분석 → [매칭 수락/거부] → 수락 → 매칭중… → 매칭완료
→ [연락처 교환 수락/거부] → 수락 → 연락처 교환
```

기획안이 수정되어 **"연락처 교환" = "채팅 열기"** 로 확정됐다. 즉 매칭 성사 후
상대를 공개하고 **채팅을 열지 말지 쌍방이 다시 동의**하는 관문이 생겼고, 이를 **게이트2**로 정의한다.

## 게이트1 vs 게이트2

| | 게이트1 | 게이트2 |
|---|---|---|
| 시점 | 매칭 **전** | 매칭 **후** |
| 질문 | 매칭 판에 낄래? | 이 상대와 채팅할래? |
| 상대 정보 | 모름(블라인드) | **봄**(사진·프로필 공개) |
| 성립 | 수락하면 후보 풀 진입 | **양쪽 다** 수락해야 채팅 오픈 |
| 현재 F3 | ✅ 있음 (`MatchDecision`) | ❌ 없음 → 본 문서에서 추가 |

게이트2는 게이트1과 **동형(isomorphic)** 이다: per-user 수락/거부 → 양쪽 ACCEPTED면 전이.
따라서 게이트1 패턴을 그대로 확장해 구현한다.

## 화면별 대조표 (현재 → 개선 방향)

| 디자인 | 필요한 것 | F3 백엔드(현재) | 판정 | 개선 방향 (설계) |
|---|---|---|---|---|
| **2b3 매칭 발견**<br>(상대 공개 + 수락/거절) | ①상대 사진 ②게이트2 수락/거절 엔드포인트 ③reveal 플래그 | ❌ 상대 사진 필드 없음<br>❌ 게이트2 엔드포인트 없음<br>❌ reveal→true 코드 없음 | 🔴 연동 불가 | `MATCHED` = "상대 공개 + 게이트2 대기" 화면으로 사용. **`Match`에 `chatDecisionA/B`(Gate2Decision) 추가.** MATCHED부터 응답에 **상대 신원(nickname·campus·블러 사진 URL·공통태그) 포함**(상대 사진 = 상대의 오늘 F1 사진 재사용). 단 유사도·점수는 아직 숨김. 수락/거절 → **`POST /api/matches/chat/accept`·`/chat/reject?userId=`** |
| **2b4 상대 응답 대기**<br>(내 수락 후 상대 대기) | 양방향 게이트2 상태(내 결정·상대 결정 구분) | ❌ 그런 상태·엔드포인트 없음 | 🔴 연동 불가 | 뷰어 기준 **`AWAITING_PARTNER` 상태 신설**(나 ACCEPTED · 상대 PENDING). FE는 **`GET /api/matches/today` 폴링**으로 `CONNECTED` 전환 감지 — 게이트1의 폴링 패턴 그대로 재사용 |
| **2c 매칭 완료**<br>(유사도 87% + AI 코멘트) | 유사도, scoreBreakdown, AI 코멘트 | ✅ `similarityScore`<br>✅ `scoreBreakdown`<br>✅ `aiComment` | 🟢 연동 완료 | 양쪽 ACCEPTED → **`CONNECTED` 상태 + `connectedAt` 1회 기록**(= F5 채팅 오픈 트리거). **CONNECTED부터 `similarityScore`·`scoreBreakdown`·`aiComment` 공개**(2b3의 "유사도는 대화 시작하면" 규칙 반영). AI 코멘트는 F4(설명 생성)를 재활용해 CONNECTED 시점에 1회 생성하며, 실패해도 매칭 자체는 유지하고 `aiComment`만 `null`로 둔다. "채팅 시작하기"는 **F5 소관**(F3는 방 생성 안 함) |
| **2d 매칭 종료**<br>("대화로 이어지지 않음") | 게이트2 거절로 종료된 상태 | ⚠️ `DECLINED`는 게이트1 거부(매칭 전)라 의미 다름 | 🟡 의미 불일치 | 게이트2 REJECTED(둘 중 누구든) → **`ENDED` 상태 신설**(게이트1 `DECLINED`와 분리). **소진 정책**: 매칭 행 유지 → 기존 1:1 배타 로직이 그날 재매칭을 자동 차단, 후보 풀 코드 변경 불필요 |

## 상태값 → 화면 (뷰어 기준, 1:1 매핑)

| status | 화면 | 의미 |
|---|---|---|
| `NOT_REQUESTED` | 2b1 참여 확인 | 게이트1 미결정 |
| `PENDING` | 2b2 매칭 대기 | 게이트1 수락, 상대 없음(폴링) |
| `MATCHED` | 2b3 매칭 발견 | 쌍 성사·상대 공개, **내 게이트2 미결정** |
| `AWAITING_PARTNER` | 2b4 상대 응답 대기 | 나는 수락, 상대 미결정 |
| `CONNECTED` | 2c 매칭 완료 | **양쪽 수락 = F5 채팅 오픈 신호** |
| `ENDED` | 2d 매칭 종료 | 누군가 게이트2 거부 |
| `DECLINED` | (참여 안 함) | 게이트1 거부 |

## 상태 흐름

```
NOT_REQUESTED ──(수락)──▶ PENDING ──▶ MATCHED(2b3) ──▶ AWAITING_PARTNER(2b4) ──▶ CONNECTED(2c) ──▶ [F5 채팅]
      │                                    │
      └──(게이트1 거부)──▶ DECLINED         └──(게이트2 거부)──▶ ENDED(2d)
```

## 데이터 모델 — `Match` 엔티티 확장

```
+ chatDecisionA : Gate2Decision  (PENDING | ACCEPTED | REJECTED, 기본 PENDING)
+ chatDecisionB : Gate2Decision
+ connectedAt   : LocalDateTime? ← 양쪽 ACCEPTED 되는 순간 1회 기록 (F5 트리거)
  revealedToA/B  → 재해석: '상대 신원 공개'는 MATCHED부터, '유사도/근거'는 CONNECTED부터
```

- 게이트1의 `MatchDecision`을 미러링한 `Gate2Decision`을 **새 enum**으로 둔다(의미가 달라 재사용보다 분리가 안전).
- `chatDecisionA/B`는 `userA/userB`에 묶이고, 뷰어 기준 status는 `MatchResponse.of`가 이미 하는 `viewerIsA` 판정으로 계산한다.

## 전이표 (MATCHED 쌍이 있을 때, 뷰어 기준)

| 내 결정 | 상대 결정 | status |
|---|---|---|
| PENDING | PENDING / ACCEPTED | `MATCHED` (2b3) |
| ACCEPTED | PENDING | `AWAITING_PARTNER` (2b4) |
| ACCEPTED | ACCEPTED | `CONNECTED` (2c) ← `connectedAt` 기록 |
| REJECTED | * | `ENDED` (2d) |
| * | REJECTED | `ENDED` (2d) |

## 엔드포인트

```
기존 유지:
  POST /api/matches?userId=          게이트1 수락 + 매칭 시도(폴링)
  POST /api/matches/decline?userId=  게이트1 거부
  GET  /api/matches/today?userId=    상태 조회(이제 확장된 status 반환)

추가 (게이트1의 수락/거부 2-엔드포인트 스타일과 통일):
  POST /api/matches/chat/accept?userId=   게이트2 수락 → 양쪽 ACCEPTED면 connectedAt 기록
  POST /api/matches/chat/reject?userId=   게이트2 거부 → status ENDED
```

- 2b4(상대 대기)에서 FE는 `GET /today`를 폴링해 `CONNECTED` 전환을 감지한다(게이트1 폴링 패턴 그대로).
- 응답 필드 게이팅: **`MATCHED`부터** partner 신원(nickname·campus·블러 사진·공통태그), **`CONNECTED`부터** `similarityScore`·`scoreBreakdown`·`aiComment`(`revealedToMe`로 공개 여부 표시).

## F3 ↔ F5 계약 (핵심)

- **신호는 딱 하나: `connectedAt`.** 양쪽 게이트2 ACCEPTED 되는 순간 F3가 1회 기록.
- **F5:** `connectedAt != null && 방 없음 → 채팅방 생성`. 멱등, 관찰만. 이 필드 외 F3 내부 상태를 보지 않는다.
- **F3 금지사항:** 채팅방/메시지 엔티티를 **절대 만들지 않는다.** `connectedAt`까지만.
- 요약: F3는 "연결됐다"까지, F5는 "연결을 실현(방 오픈)"까지.

## 해결된 항목

1. **AI 코멘트(2c) 출처** — F4(설명 생성)를 재활용해 CONNECTED 시점에 1회 생성하는 방향으로 결정·구현됨. `Match.aiComment` / `MatchResponse.aiComment` 필드 추가. AI 호출 실패는 매칭 성사를 막지 않고 코멘트만 `null`로 둔다.
2. **거부 후 재매칭 정책(2d)** — 소진 정책(매칭 행 유지 → 기존 1:1 배타 로직이 그날 재매칭을 자동 차단)으로 구현됨. 후보 풀 코드 변경 없었음.

## 코딩 전 F5와 확정할 열린 항목

1. **`connectedAt` vs `revealedToA/B`** — `Match` 엔티티의 `is_revealed_to_a`/`is_revealed_to_b` 컬럼은 여전히 존재하지만 생성자에서 항상 `false`로 세팅된 뒤 어디서도 읽거나 갱신하지 않는 **죽은 컬럼**이다. 실제 공개 여부는 `MatchResponse.revealedToMe`(DB 저장값이 아니라 `status == CONNECTED`로부터 매 응답마다 계산되는 값)로 대체됐다. 다만 지금은 FE도 이 필드를 안 쓰고 `status`만으로 화면을 분기하고 있어서, 죽은 DB 컬럼을 아예 삭제할지·`revealedToMe`를 F5가 실제로 활용할지는 F5와 재합의가 필요하다.
