package com.myongjithon.syncday.domain.chat;

import com.myongjithon.syncday.domain.chat.dto.ChatMessageRequest;
import com.myongjithon.syncday.domain.chat.dto.ChatMessageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/matches/{matchId}/messages")
@RequiredArgsConstructor
@Tag(name = "F5. 매칭 채팅", description = "CONNECTED 된 매칭의 1:1 채팅 API (폴링 기반)")
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "메시지 전송", description = """
            양쪽 모두 채팅을 수락한(CONNECTED) 매칭에서만 전송 가능합니다.
            그 전에 호출하면 403을 반환합니다. content는 1000자 이내.""")
    @PostMapping
    public ResponseEntity<ChatMessageResponse> send(
            @PathVariable UUID matchId,
            @RequestParam UUID userId,
            @RequestBody @Valid ChatMessageRequest request) {
        return ResponseEntity.ok(chatService.sendMessage(matchId, userId, request.content()));
    }

    @Operation(summary = "메시지 조회 (FE 폴링)", description = """
            afterId 이후의 새 메시지만 반환합니다. 생략 시 전체 히스토리.
            FE는 받은 messageId 최대값을 afterId로 넣어 2~3초 간격 폴링하세요.""")
    @GetMapping
    public ResponseEntity<List<ChatMessageResponse>> poll(
            @PathVariable UUID matchId,
            @RequestParam UUID userId,
            @RequestParam(required = false) Long afterId) {
        return ResponseEntity.ok(chatService.getMessages(matchId, userId, afterId));
    }
}