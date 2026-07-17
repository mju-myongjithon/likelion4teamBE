package com.myongjithon.syncday.domain.demo;

import com.myongjithon.syncday.domain.match.Gate2Decision;
import com.myongjithon.syncday.domain.match.Match;
import com.myongjithon.syncday.domain.match.MatchRepository;
import com.myongjithon.syncday.domain.match.MatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeedAutoAccepterTest {

    @Mock private MatchRepository matchRepository;
    @Mock private MatchService matchService;
    @Mock private SeedAccountRepository seedAccountRepository;

    private SeedAutoAccepter seedAutoAccepter;

    private final LocalDate today = LocalDate.now();
    private final UUID liveUser = UUID.randomUUID();
    private final UUID seedUser = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        seedAutoAccepter = new SeedAutoAccepter(
                matchRepository, matchService, seedAccountRepository);
    }

    private Match mockMatch(UUID userAId, UUID userBId) {
        var appUserA = mock(com.myongjithon.syncday.domain.user.AppUser.class);
        var appUserB = mock(com.myongjithon.syncday.domain.user.AppUser.class);
        lenient().when(appUserA.getUserId()).thenReturn(userAId);
        lenient().when(appUserB.getUserId()).thenReturn(userBId);

        Match match = mock(Match.class);
        lenient().when(match.getUserA()).thenReturn(appUserA);
        lenient().when(match.getUserB()).thenReturn(appUserB);
        return match;
    }

    @Test
    @DisplayName("상대가 시드면 시드 명의로 게이트2를 자동 수락한다")
    void autoAcceptsWhenPartnerIsSeed() {
        Match match = mockMatch(liveUser, seedUser);
        when(matchRepository.findByDateAndParticipant(today, liveUser))
                .thenReturn(Optional.of(match));
        when(seedAccountRepository.existsById(seedUser)).thenReturn(true);

        seedAutoAccepter.autoAcceptIfPartnerIsSeed(liveUser, today);

        verify(matchService).applyChatDecision(seedUser, today, Gate2Decision.ACCEPTED);
    }

    @Test
    @DisplayName("상대가 실 유저면 자동 수락하지 않는다")
    void doesNotAcceptWhenPartnerIsRealUser() {
        Match match = mockMatch(liveUser, seedUser);
        when(matchRepository.findByDateAndParticipant(today, liveUser))
                .thenReturn(Optional.of(match));
        when(seedAccountRepository.existsById(seedUser)).thenReturn(false);

        seedAutoAccepter.autoAcceptIfPartnerIsSeed(liveUser, today);

        verify(matchService, never()).applyChatDecision(any(), any(), any());
    }

    @Test
    @DisplayName("오늘 매칭이 없으면 아무 일도 하지 않는다")
    void doesNothingWhenNoMatch() {
        when(matchRepository.findByDateAndParticipant(today, liveUser))
                .thenReturn(Optional.empty());

        seedAutoAccepter.autoAcceptIfPartnerIsSeed(liveUser, today);

        verify(matchService, never()).applyChatDecision(any(), any(), any());
    }

    @Test
    @DisplayName("userB가 라이브 유저이고 userA가 시드여도 정상 판별한다 (정규화 무관)")
    void handlesReversedOrder() {
        Match match = mockMatch(seedUser, liveUser);   // 시드가 A쪽
        when(matchRepository.findByDateAndParticipant(today, liveUser))
                .thenReturn(Optional.of(match));
        when(seedAccountRepository.existsById(seedUser)).thenReturn(true);

        seedAutoAccepter.autoAcceptIfPartnerIsSeed(liveUser, today);

        verify(matchService).applyChatDecision(eq(seedUser), eq(today), eq(Gate2Decision.ACCEPTED));
    }
}