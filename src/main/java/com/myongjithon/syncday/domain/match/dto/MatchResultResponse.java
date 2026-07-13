package com.myongjithon.syncday.domain.match.dto;

import com.myongjithon.syncday.domain.match.MatchStatus;

/**
 * 매칭 폴링용 상태 응답. FE 유저 플로우("수락/거부 → 매칭중 → 매칭완료")를 자연스럽게 그리도록,
 * 매칭 미수락·미성사를 404 에러가 아니라 정상 상태로 내려준다. match 는 MATCHED 일 때만 채워진다.
 *
 * <ul>
 *   <li>NOT_REQUESTED: 아직 매칭 수락/거부 전 (match == null). FE는 수락/거부 화면.</li>
 *   <li>PENDING: 수락했지만 아직 상대가 없어 대기 중 (match == null). FE는 폴링 유지.</li>
 *   <li>MATCHED: 매칭 성사 (match != null).</li>
 *   <li>DECLINED: 매칭 거부 (match == null).</li>
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

    public static MatchResultResponse notRequested() {
        return new MatchResultResponse(MatchStatus.NOT_REQUESTED, null);
    }

    public static MatchResultResponse declined() {
        return new MatchResultResponse(MatchStatus.DECLINED, null);
    }
}
