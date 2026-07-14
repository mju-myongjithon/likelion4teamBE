package com.myongjithon.syncday.domain.match.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.myongjithon.syncday.domain.match.Match;
import com.myongjithon.syncday.domain.user.AppUser;
import com.myongjithon.syncday.domain.user.Campus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 매칭 결과 응답. 조회하는 유저(viewer) 관점에서 상대(partner) 정보를 담는다.
 *
 * ※ 공개 게이팅(상대 정보 노출 시점)은 F5 책임이다. 여기서는 revealedToMe 플래그만 전달한다.
 */
public record MatchResponse(
        UUID matchId,
        LocalDate date,
        int similarityScore,
        UUID partnerId,
        String partnerNickname,
        Campus partnerCampus,
        boolean revealedToMe,
        @JsonRawValue String scoreBreakdown
) {
    public static MatchResponse of(Match match, UUID viewerId) {
        boolean viewerIsA = match.getUserA().getUserId().equals(viewerId);
        AppUser partner = viewerIsA ? match.getUserB() : match.getUserA();
        boolean revealedToMe = viewerIsA ? match.isRevealedToA() : match.isRevealedToB();

        return new MatchResponse(
                match.getMatchId(),
                match.getDate(),
                match.getSimilarityScore(),
                partner.getUserId(),
                partner.getNickname(),
                partner.getCampus(),
                revealedToMe,
                match.getScoreBreakdown()
        );
    }
}
