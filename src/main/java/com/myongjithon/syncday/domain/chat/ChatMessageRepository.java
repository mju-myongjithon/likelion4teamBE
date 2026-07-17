package com.myongjithon.syncday.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByMatch_MatchIdAndMessageIdGreaterThanOrderByMessageIdAsc(
            UUID matchId, Long afterId);
}