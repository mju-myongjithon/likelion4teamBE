package com.myongjithon.syncday.domain.chat;

import com.myongjithon.syncday.domain.match.Match;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "chat_message",
        indexes = @Index(name = "idx_chat_message_cursor", columnList = "match_id, message_id")
)
@Getter
@NoArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static ChatMessage of(Match match, UUID senderId, String content) {
        ChatMessage message = new ChatMessage();
        message.match = match;
        message.senderId = senderId;
        message.content = content;
        message.createdAt = LocalDateTime.now();
        return message;
    }
}