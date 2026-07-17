package com.myongjithon.syncday.domain.chat.dto;

import com.myongjithon.syncday.domain.chat.ChatMessage;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * senderId 원값 대신 mine 플래그로 관점 변환 — 상대 UUID를 채팅 응답에 흘리지 않는다.
 * FE는 받은 messageId 중 최대값을 다음 폴링의 afterId 커서로 쓴다.
 */
public record ChatMessageResponse(
        Long messageId,
        boolean mine,
        String content,
        LocalDateTime createdAt
) {
    public static ChatMessageResponse of(ChatMessage message, UUID viewerId) {
        return new ChatMessageResponse(
                message.getMessageId(),
                message.getSenderId().equals(viewerId),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}