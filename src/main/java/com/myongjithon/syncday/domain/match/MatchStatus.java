package com.myongjithon.syncday.domain.match;

/**
 * 매칭 폴링 상태. FE 유저 플로우의 각 화면과 1:1로 대응된다(뷰어 기준으로 계산됨).
 * 매칭 미성사·미수락은 에러가 아니라 정상 상태로 표현해, FE가 한 흐름의 로딩/선택 화면을 자연스럽게 그리게 한다.
 *
 * <pre>
 * NOT_REQUESTED ─(수락)─▶ PENDING ─▶ MATCHED ─▶ AWAITING_PARTNER ─▶ CONNECTED ─▶ [F5 채팅]
 *       │                              │
 *       └─(게이트1 거부)─▶ DECLINED     └─(게이트2 거부)─▶ ENDED
 * </pre>
 *
 * MATCHED 이후(MATCHED/AWAITING_PARTNER/CONNECTED/ENDED)는 모두 매칭 행이 존재하므로 match 필드가 채워진다.
 */
public enum MatchStatus {

    /** 분석은 끝났지만 아직 매칭 수락/거부를 안 함(게이트1). FE는 "매칭 수락/거부" 화면(2b1). */
    NOT_REQUESTED,

    /** 매칭 수락 후 아직 반대 캠퍼스 상대가 없어 대기 중. FE는 "매칭중..."(2b2) 유지 후 폴링. */
    PENDING,

    /** 매칭 성사·상대 공개, 아직 내 게이트2 결정 전. FE는 "매칭 발견"(2b3)에서 채팅 수락/거부를 받는다. */
    MATCHED,

    /** 나는 채팅 수락(게이트2), 상대는 아직 미결정. FE는 "상대 응답 대기"(2b4)에서 폴링. */
    AWAITING_PARTNER,

    /** 양쪽 다 채팅 수락 → 연결됨. match 에 유사도·근거가 공개되고, F5가 채팅방을 연다. FE는 "매칭 완료"(2c). */
    CONNECTED,

    /** 게이트2에서 누군가 거부 → 그날 매칭 종료. FE는 "매칭 종료"(2d). */
    ENDED,

    /** 매칭 거부(게이트1). 오늘은 매칭에 참여하지 않는다. */
    DECLINED
}
