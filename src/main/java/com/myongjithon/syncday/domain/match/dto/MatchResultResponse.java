package com.myongjithon.syncday.domain.match.dto;

import com.myongjithon.syncday.domain.match.Gate2Decision;
import com.myongjithon.syncday.domain.match.Match;
import com.myongjithon.syncday.domain.match.MatchStatus;

import java.util.UUID;

/**
 * 매칭 폴링용 상태 응답. FE 유저 플로우의 각 화면을 status 하나로 그리도록,
 * 매칭 미수락·미성사를 404 에러가 아니라 정상 상태로 내려준다.
 *
 * <ul>
 *   <li>NOT_REQUESTED: 게이트1 미결정 (match == null). FE는 참여 확인(2b1).</li>
 *   <li>PENDING: 게이트1 수락, 상대 없음 (match == null). FE는 매칭 대기(2b2) 폴링.</li>
 *   <li>MATCHED: 매칭 성사·상대 공개, 내 게이트2 미결정 (match != null). FE는 매칭 발견(2b3).</li>
 *   <li>AWAITING_PARTNER: 나 수락·상대 미결정 (match != null). FE는 상대 응답 대기(2b4) 폴링.</li>
 *   <li>CONNECTED: 양쪽 수락, 유사도·근거 공개 (match != null). FE는 매칭 완료(2c). F5 채팅 오픈 신호.</li>
 *   <li>ENDED: 게이트2에서 누군가 거부 (match != null). FE는 매칭 종료(2d).</li>
 *   <li>DECLINED: 게이트1 거부 (match == null).</li>
 * </ul>
 */
public record MatchResultResponse(
        MatchStatus status,
        MatchResponse match
) {

    /**
     * 매칭 행이 존재할 때(MATCHED 이후) 뷰어 기준으로 게이트2 상태를 계산해 응답을 만든다.
     * 유사도·근거는 CONNECTED 일 때만 채워진다.
     */
    public static MatchResultResponse fromMatch(Match match, UUID viewerId) {
        MatchStatus status = gate2Status(
                match.chatDecisionOf(viewerId),
                match.partnerChatDecisionOf(viewerId)
        );
        boolean scoresRevealed = status == MatchStatus.CONNECTED;
        return new MatchResultResponse(status, MatchResponse.of(match, viewerId, scoresRevealed));
    }

    /** (내 결정, 상대 결정) → 화면 상태. 거부가 우선(종료), 다음 양쪽 수락(연결), 내 수락(상대 대기), 나머지 발견. */
    private static MatchStatus gate2Status(Gate2Decision mine, Gate2Decision partner) {
        if (mine == Gate2Decision.REJECTED || partner == Gate2Decision.REJECTED) {
            return MatchStatus.ENDED;
        }
        if (mine == Gate2Decision.ACCEPTED && partner == Gate2Decision.ACCEPTED) {
            return MatchStatus.CONNECTED;
        }
        if (mine == Gate2Decision.ACCEPTED) {
            return MatchStatus.AWAITING_PARTNER;
        }
        return MatchStatus.MATCHED;
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
