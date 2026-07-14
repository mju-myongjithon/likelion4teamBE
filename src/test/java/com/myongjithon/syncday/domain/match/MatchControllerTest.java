package com.myongjithon.syncday.domain.match;

import com.myongjithon.syncday.domain.match.dto.MatchResponse;
import com.myongjithon.syncday.domain.match.dto.MatchResultResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchControllerTest {

    @Mock
    private MatchService matchService;

    @InjectMocks
    private MatchController matchController;

    @Test
    @DisplayName("매칭 실행: 서비스 결과를 그대로 200으로 돌려준다")
    void createMatchReturnsServiceResult() {
        UUID userId = UUID.randomUUID();
        MatchResultResponse pending = MatchResultResponse.pending();
        when(matchService.createMatchForUser(eq(userId), any(LocalDate.class))).thenReturn(pending);

        ResponseEntity<MatchResultResponse> response = matchController.createMatch(userId);

        assertThat(response.getBody()).isSameAs(pending);
        verify(matchService, never()).getMatch(any(), any());
    }

    @Test
    @DisplayName("매칭 실행: 동시 저장 충돌이면 조회로 성사된 매칭을 돌려준다(멱등 복구)")
    void createMatchRecoversFromConcurrentConflict() {
        UUID userId = UUID.randomUUID();
        MatchResultResponse matched = new MatchResultResponse(MatchStatus.MATCHED,
                new MatchResponse(UUID.randomUUID(), LocalDate.now(), 77,
                        UUID.randomUUID(), "상대", "자연", false, "{}", null));

        when(matchService.createMatchForUser(eq(userId), any(LocalDate.class)))
                .thenThrow(new DataIntegrityViolationException("uk_match_pair_date"));
        when(matchService.getMatch(eq(userId), any(LocalDate.class))).thenReturn(matched);

        ResponseEntity<MatchResultResponse> response = matchController.createMatch(userId);

        assertThat(response.getBody()).isSameAs(matched);
        assertThat(response.getBody().status()).isEqualTo(MatchStatus.MATCHED);
        verify(matchService).getMatch(eq(userId), any(LocalDate.class));
    }

    @Test
    @DisplayName("오늘의 매칭 조회: getMatch 결과를 그대로 돌려준다")
    void getTodayMatchDelegates() {
        UUID userId = UUID.randomUUID();
        MatchResultResponse pending = MatchResultResponse.pending();
        when(matchService.getMatch(eq(userId), any(LocalDate.class))).thenReturn(pending);

        ResponseEntity<MatchResultResponse> response = matchController.getTodayMatch(userId);

        assertThat(response.getBody()).isSameAs(pending);
    }

    @Test
    @DisplayName("매칭 거부: declineMatch 결과(DECLINED)를 그대로 돌려준다")
    void declineMatchDelegates() {
        UUID userId = UUID.randomUUID();
        MatchResultResponse declined = MatchResultResponse.declined();
        when(matchService.declineMatch(eq(userId), any(LocalDate.class))).thenReturn(declined);

        ResponseEntity<MatchResultResponse> response = matchController.declineMatch(userId);

        assertThat(response.getBody()).isSameAs(declined);
        assertThat(response.getBody().status()).isEqualTo(MatchStatus.DECLINED);
    }

    @Test
    @DisplayName("채팅 수락(게이트2): applyChatDecision(ACCEPTED) 결과를 그대로 돌려준다")
    void acceptChatDelegates() {
        UUID userId = UUID.randomUUID();
        MatchResultResponse connected = new MatchResultResponse(MatchStatus.CONNECTED,
                new MatchResponse(UUID.randomUUID(), LocalDate.now(), 87,
                        UUID.randomUUID(), "상대", "자연", true, "{}", "두 분 닮았어요"));
        when(matchService.applyChatDecision(eq(userId), any(LocalDate.class), eq(Gate2Decision.ACCEPTED)))
                .thenReturn(connected);

        ResponseEntity<MatchResultResponse> response = matchController.acceptChat(userId);

        assertThat(response.getBody()).isSameAs(connected);
        assertThat(response.getBody().status()).isEqualTo(MatchStatus.CONNECTED);
    }

    @Test
    @DisplayName("채팅 거부(게이트2): applyChatDecision(REJECTED) 결과를 그대로 돌려준다")
    void rejectChatDelegates() {
        UUID userId = UUID.randomUUID();
        MatchResultResponse ended = new MatchResultResponse(MatchStatus.ENDED, null);
        when(matchService.applyChatDecision(eq(userId), any(LocalDate.class), eq(Gate2Decision.REJECTED)))
                .thenReturn(ended);

        ResponseEntity<MatchResultResponse> response = matchController.rejectChat(userId);

        assertThat(response.getBody()).isSameAs(ended);
        assertThat(response.getBody().status()).isEqualTo(MatchStatus.ENDED);
    }
}
