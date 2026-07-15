package com.myongjithon.syncday.domain.chat;

import com.myongjithon.syncday.domain.chat.dto.ChatMessageResponse;
import com.myongjithon.syncday.domain.match.Match;
import com.myongjithon.syncday.domain.match.MatchRepository;
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

    @Transactional
    public ChatMessageResponse sendMessage(UUID matchId, UUID senderId, String content) {
        Match match = loadAndAuthorize(matchId, senderId);
        ChatMessage saved = chatMessageRepository.save(ChatMessage.of(match, senderId, content));
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
}