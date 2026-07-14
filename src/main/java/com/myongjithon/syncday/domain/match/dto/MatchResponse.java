package com.myongjithon.syncday.domain.match.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.myongjithon.syncday.domain.match.Match;
import com.myongjithon.syncday.domain.user.AppUser;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 매칭 결과 응답. 조회하는 유저(viewer) 관점에서 상대(partner) 정보를 담는다.
 *
 * <p>공개 단계가 두 단계다:
 * <ul>
 *   <li>MATCHED 부터: 상대 신원(nickname·campus)은 공개된다(2b3 매칭 발견).</li>
 *   <li>CONNECTED 부터: 유사도·근거·AI 코멘트({@code similarityScore}, {@code scoreBreakdown}, {@code aiComment})가 공개된다(2c 매칭 완료).</li>
 * </ul>
 * 연결 전에는 유사도·근거·코멘트를 null 로 내려 FE가 값 자체를 못 받게 한다(서버 사이드 게이팅).
 * {@code revealedToMe} 는 그 공개 여부(= CONNECTED)를 뜻한다.
 */
public record MatchResponse(
        UUID matchId,
        LocalDate date,
        Integer similarityScore,
        UUID partnerId,
        String partnerNickname,
        String partnerCampus,
        boolean revealedToMe,
        @JsonRawValue String scoreBreakdown,
        String aiComment
) {
    /**
     * @param scoresRevealed 유사도·근거·코멘트를 공개할지(= 상태가 CONNECTED 인지). false 면 해당 필드를 null 로 가린다.
     */
    public static MatchResponse of(Match match, UUID viewerId, boolean scoresRevealed) {
        boolean viewerIsA = match.isUserA(viewerId);
        AppUser partner = viewerIsA ? match.getUserB() : match.getUserA();

        return new MatchResponse(
                match.getMatchId(),
                match.getDate(),
                scoresRevealed ? match.getSimilarityScore() : null,
                partner.getUserId(),
                partner.getNickname(),
                partner.getCampus(),
                scoresRevealed,
                scoresRevealed ? match.getScoreBreakdown() : null,
                scoresRevealed ? match.getAiComment() : null
        );
    }
}
