package com.myongjithon.syncday.domain.demo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 데모 전용 — 시드 게이트2 자동 수락 트리거.
 * FE가 매칭 발견 화면(MATCHED) 진입 시 이 API를 1회 호출하면, 상대가 시드일 경우 서버가 대신 수락한다.
 * 실 유저 상대에는 무동작이라 모든 매칭에서 호출해도 안전하다.
 */
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
@Tag(name = "데모 전용", description = "시연용 시드 계정 자동화 (실 유저 미적용)")
public class SeedDemoController {

    private final SeedAutoAccepter seedAutoAccepter;

    @Operation(summary = "시드 상대 게이트2 자동 수락",
            description = "상대가 시드면 서버가 대신 채팅 수락. 실 유저 상대면 무동작.")
    @PostMapping("/seed/auto-accept")
    public ResponseEntity<Void> autoAccept(@RequestParam UUID userId) {
        seedAutoAccepter.autoAcceptIfPartnerIsSeed(userId, LocalDate.now());
        return ResponseEntity.ok().build();
    }
}