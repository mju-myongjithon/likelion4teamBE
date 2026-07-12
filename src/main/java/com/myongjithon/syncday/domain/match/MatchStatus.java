package com.myongjithon.syncday.domain.match;

/**
 * 매칭 폴링 상태. FE의 "분석중 → 매칭대기 → 매칭완료" 로딩 흐름을 위해,
 * 하루 매칭 특성상 아직 상대가 없는 것은 에러가 아니라 대기(PENDING) 정상 상태로 표현한다.
 */
public enum MatchStatus {

    /** 분석은 끝났지만 아직 반대 캠퍼스 상대가 없어 대기 중. FE는 "매칭중..." 유지 후 폴링. */
    PENDING,

    /** 매칭 성사. 응답의 match 필드에 결과가 담긴다. */
    MATCHED
}
