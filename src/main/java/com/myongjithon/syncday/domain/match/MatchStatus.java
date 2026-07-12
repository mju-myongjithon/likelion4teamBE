package com.myongjithon.syncday.domain.match;

/**
 * 매칭 폴링 상태. FE 유저 플로우의 각 화면과 1:1로 대응된다.
 * 매칭 미성사·미수락은 에러가 아니라 정상 상태로 표현해, FE가 한 흐름의 로딩/선택 화면을 자연스럽게 그리게 한다.
 *
 * <pre>
 * NOT_REQUESTED → (수락) → PENDING → (상대도 수락·성사) → MATCHED
 *              ↘ (거부) → DECLINED
 * </pre>
 */
public enum MatchStatus {

    /** 분석은 끝났지만 아직 매칭 수락/거부를 안 함. FE는 "매칭 수락/거부" 화면을 띄운다. */
    NOT_REQUESTED,

    /** 매칭 수락 후 아직 반대 캠퍼스 상대가 없어 대기 중. FE는 "매칭중..." 유지 후 폴링. */
    PENDING,

    /** 매칭 성사. 응답의 match 필드에 결과가 담긴다. */
    MATCHED,

    /** 매칭 거부. 오늘은 매칭에 참여하지 않는다. */
    DECLINED
}
