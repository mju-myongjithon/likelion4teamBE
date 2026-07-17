package com.myongjithon.syncday.domain.chat;

import com.myongjithon.syncday.domain.chat.dto.ChatMessageResponse;
import com.myongjithon.syncday.domain.demo.SeedAccountRepository;
import com.myongjithon.syncday.domain.match.Match;
import com.myongjithon.syncday.domain.match.MatchRepository;
import com.myongjithon.syncday.domain.user.AppUser;
import com.myongjithon.syncday.global.exception.MatchErrorCode;
import com.myongjithon.syncday.global.exception.MatchException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final MatchRepository matchRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SeedAccountRepository seedAccountRepository;
    private final SeedAutoResponder seedAutoResponder;

    @Transactional
    public ChatMessageResponse sendMessage(UUID matchId, UUID senderId, String content) {
        Match match = loadAndAuthorize(matchId, senderId);
        ChatMessage saved = chatMessageRepository.save(ChatMessage.of(match, senderId, content));

        triggerSeedReplyIfNeeded(match, senderId);

        return ChatMessageResponse.of(saved, senderId);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(UUID matchId, UUID viewerId, Long afterId) {
        loadAndAuthorize(matchId, viewerId);
        return chatMessageRepository
                .findByMatch_MatchIdAndMessageIdGreaterThanOrderByMessageIdAsc(
                        matchId, afterId == null ? 0L : afterId)
                .stream()
                .map(message -> ChatMessageResponse.of(message, viewerId))
                .toList();
    }

    /**
     * F3↔F5 계약: match.isConnected() (= connectedAt != null) 가 채팅 진입의 유일한 신호.
     * F3 내부 상태(PENDING/MATCHED/AWAITING_PARTNER 등)는 보지 않는다 (PR#21 계약).
     */
    private Match loadAndAuthorize(UUID matchId, UUID userId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new MatchException(MatchErrorCode.MATCH_NOT_FOUND));

        boolean isParticipant = match.getUserA().getUserId().equals(userId)
                || match.getUserB().getUserId().equals(userId);
        if (!isParticipant) {
            throw new MatchException(MatchErrorCode.NOT_MATCH_PARTICIPANT);
        }

        if (!match.isConnected()) {
            throw new MatchException(MatchErrorCode.MATCH_NOT_CONNECTED);
        }

        return match;
    }

    /**
     * 데모 전용(시드 자동 응답): 상대가 시드 계정(seed_account 존재)이고 아직 한 번도 답한 적 없으면,
     * 짧은 지연 후 고정 문구 1건을 자동 insert 한다. 매칭당 1회로 한정하며,
     * 실 유저 매칭(양쪽 다 seed_account 에 없음)에는 아무 일도 일어나지 않는다.
     */
    private void triggerSeedReplyIfNeeded(Match match, UUID senderId) {
        boolean senderIsA = match.getUserA().getUserId().equals(senderId);
        AppUser partner = senderIsA ? match.getUserB() : match.getUserA();

        if (seedAccountRepository.existsById(partner.getUserId())
                && !chatMessageRepository.existsByMatch_MatchIdAndSenderId(
                match.getMatchId(), partner.getUserId())) {
            seedAutoResponder.replyLater(match.getMatchId(), partner.getUserId());
        }
    }
}