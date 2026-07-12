package com.myongjithon.syncday.domain.match.dto;

import com.myongjithon.syncday.domain.match.MatchStatus;

/**
 * 매칭 폴링용 상태 응답. FE가 "분석중 → 매칭대기 → 매칭완료"로 이어지는 한 화면의
 * 로딩 흐름을 자연스럽게 그리도록, 매칭 미성사를 404 에러가 아니라 정상 상태로 내려준다.
 *
 * <ul>
 *   <li>PENDING: 아직 상대가 없어 대기 중 (match == null). FE는 폴링을 유지한다.</li>
 *   <li>MATCHED: 매칭 성사 (match != null).</li>
 * </ul>
 */
public record MatchResultResponse(
        MatchStatus status,
        MatchResponse match
) {

    public static MatchResultResponse matched(MatchResponse match) {
        return new MatchResultResponse(MatchStatus.MATCHED, match);
    }

    public static MatchResultResponse pending() {
        return new MatchResultResponse(MatchStatus.PENDING, null);
    }
}
