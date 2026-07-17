package com.myongjithon.syncday.domain.demo;

import com.myongjithon.syncday.domain.match.Gate2Decision;
import com.myongjithon.syncday.domain.match.Match;
import com.myongjithon.syncday.domain.match.MatchRepository;
import com.myongjithon.syncday.domain.match.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 데모 전용 — 시드 계정 게이트2 자동 수락.
 * 매칭 상대가 시드면, 그 시드 대신 MatchService.applyChatDecision(ACCEPTED)를 호출해
 * 라이브 유저가 수락하는 순간 바로 CONNECTED 가 되게 한다.
 *
 * MatchService(F3)의 기존 public 메서드를 호출만 하고 수정하지 않는다.
 * 실 유저(seed_account 미등록) 상대에는 아무 일도 일어나지 않는다.
 */
@Component
@RequiredArgsConstructor
public class SeedAutoAccepter {

    private final MatchRepository matchRepository;
    private final MatchService matchService;
    private final SeedAccountRepository seedAccountRepository;

    /** 오늘 이 유저가 속한 매칭에서 상대가 시드면, 시드 명의로 게이트2를 자동 수락한다. */
    public void autoAcceptIfPartnerIsSeed(UUID userId, LocalDate date) {
        matchRepository.findByDateAndParticipant(date, userId).ifPresent(match -> {
            UUID partnerId = match.getUserA().getUserId().equals(userId)
                    ? match.getUserB().getUserId()
                    : match.getUserA().getUserId();
            if (seedAccountRepository.existsById(partnerId)) {
                matchService.applyChatDecision(partnerId, date, Gate2Decision.ACCEPTED);
            }
        });
    }
}