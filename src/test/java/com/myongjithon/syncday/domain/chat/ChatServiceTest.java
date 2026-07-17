package com.myongjithon.syncday.domain.chat;

import com.myongjithon.syncday.domain.demo.SeedAccountRepository;
import com.myongjithon.syncday.domain.match.Match;
import com.myongjithon.syncday.domain.match.MatchRepository;
import com.myongjithon.syncday.global.exception.MatchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private MatchRepository matchRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private SeedAccountRepository seedAccountRepository;
    @Mock private SeedAutoResponder seedAutoResponder;

    private ChatService chatService;

    private final UUID matchId = UUID.randomUUID();
    private final UUID userA = UUID.randomUUID();
    private final UUID userB = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        chatService = new ChatService(
                matchRepository, chatMessageRepository, seedAccountRepository, seedAutoResponder);
    }

    private Match mockMatch(boolean connected) {
        var appUserA = mock(com.myongjithon.syncday.domain.user.AppUser.class);
        var appUserB = mock(com.myongjithon.syncday.domain.user.AppUser.class);
        lenient().when(appUserA.getUserId()).thenReturn(userA);
        lenient().when(appUserB.getUserId()).thenReturn(userB);

        Match match = mock(Match.class);
        lenient().when(match.getUserA()).thenReturn(appUserA);
        lenient().when(match.getUserB()).thenReturn(appUserB);
        lenient().when(match.isConnected()).thenReturn(connected);
        lenient().when(match.getMatchId()).thenReturn(matchId);
        return match;
    }

    @Test
    @DisplayName("CONNECTED 전에는 메시지 전송이 차단된다 (MATCH_NOT_CONNECTED)")
    void blocksSendBeforeConnected() {
        Match match = mockMatch(false);
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        assertThatThrownBy(() -> chatService.sendMessage(matchId, userA, "안녕하세요"))
                .isInstanceOf(MatchException.class)
                .hasMessageContaining("수락");
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    @DisplayName("매칭 참여자가 아니면 조회가 차단된다 (NOT_MATCH_PARTICIPANT)")
    void blocksNonParticipant() {
        Match match = mockMatch(true);
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        assertThatThrownBy(() -> chatService.getMessages(matchId, stranger, null))
                .isInstanceOf(MatchException.class)
                .hasMessageContaining("참여자");
    }

    @Test
    @DisplayName("CONNECTED 후 메시지 전송이 정상 동작한다")
    void sendMessageWhenConnected() {
        Match match = mockMatch(true);
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(seedAccountRepository.existsById(any())).thenReturn(false);
        ChatMessage message = ChatMessage.of(match, userA, "안녕하세요");
        when(chatMessageRepository.save(any())).thenReturn(message);

        var response = chatService.sendMessage(matchId, userA, "안녕하세요");

        assertThat(response.mine()).isTrue();
        assertThat(response.content()).isEqualTo("안녕하세요");
        verify(chatMessageRepository).save(any());
        verify(seedAutoResponder, never()).replyLater(any(), any());
    }

    @Test
    @DisplayName("afterId 이후 메시지만 조회하고, 생략 시 전체 조회한다")
    void pollsAfterCursor() {
        Match match = mockMatch(true);
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(chatMessageRepository
                .findByMatch_MatchIdAndMessageIdGreaterThanOrderByMessageIdAsc(any(), any()))
                .thenReturn(List.of());

        chatService.getMessages(matchId, userA, 5L);
        verify(chatMessageRepository)
                .findByMatch_MatchIdAndMessageIdGreaterThanOrderByMessageIdAsc(matchId, 5L);

        chatService.getMessages(matchId, userA, null);
        verify(chatMessageRepository)
                .findByMatch_MatchIdAndMessageIdGreaterThanOrderByMessageIdAsc(matchId, 0L);
    }

    @Test
    @DisplayName("mine 플래그는 조회하는 유저 관점으로 계산된다")
    void mineFlagIsViewerRelative() {
        Match match = mockMatch(true);
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        ChatMessage message = ChatMessage.of(match, userA, "hi");
        when(chatMessageRepository
                .findByMatch_MatchIdAndMessageIdGreaterThanOrderByMessageIdAsc(matchId, 0L))
                .thenReturn(List.of(message));

        assertThat(chatService.getMessages(matchId, userA, null).get(0).mine()).isTrue();
        assertThat(chatService.getMessages(matchId, userB, null).get(0).mine()).isFalse();
    }

    @Test
    @DisplayName("상대가 시드 계정이면 첫 메시지에 자동 응답이 트리거된다")
    void triggersSeedReplyOnFirstMessage() {
        Match match = mockMatch(true);
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(chatMessageRepository.save(any())).thenReturn(ChatMessage.of(match, userA, "안녕하세요"));
        when(seedAccountRepository.existsById(userB)).thenReturn(true);
        when(chatMessageRepository.existsByMatch_MatchIdAndSenderId(matchId, userB)).thenReturn(false);

        chatService.sendMessage(matchId, userA, "안녕하세요");

        verify(seedAutoResponder).replyLater(matchId, userB);
    }

    @Test
    @DisplayName("시드가 이미 답장한 매칭에서는 자동 응답이 다시 트리거되지 않는다 (1회 한정)")
    void doesNotTriggerSeedReplyTwice() {
        Match match = mockMatch(true);
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(chatMessageRepository.save(any())).thenReturn(ChatMessage.of(match, userA, "또 보내요"));
        when(seedAccountRepository.existsById(userB)).thenReturn(true);
        when(chatMessageRepository.existsByMatch_MatchIdAndSenderId(matchId, userB)).thenReturn(true);

        chatService.sendMessage(matchId, userA, "또 보내요");

        verify(seedAutoResponder, never()).replyLater(any(), any());
    }
}