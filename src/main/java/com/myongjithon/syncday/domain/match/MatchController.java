package com.myongjithon.syncday.domain.match;

import com.myongjithon.syncday.domain.match.dto.MatchResultResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
@Tag(name = "F3. 유사도 매칭", description = "하루 분석 기반 유사도 매칭 API")
public class MatchController {

    private final MatchService matchService;

    @Operation(summary = "매칭 실행", description = """
            오늘 분석을 완료한 유저를 반대 캠퍼스 상대와 매칭합니다. 이미 매칭됐다면 기존 결과를 반환합니다.
            아직 상대가 없으면 에러가 아니라 status=PENDING 을 반환하므로, FE는 이 응답 동안 "매칭중..." 화면을 유지하며 폴링합니다.""")
    @PostMapping
    public ResponseEntity<MatchResultResponse> createMatch(@RequestParam UUID userId) {
        MatchResultResponse response = matchService.createMatchForUser(userId, LocalDate.now());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "오늘의 매칭 조회", description = """
            유저의 오늘 매칭 결과를 조회합니다. 매칭 전이면 status=PENDING, 성사되면 status=MATCHED 와 함께 match 가 채워집니다.
            FE는 결과 탭을 열어두고 이 엔드포인트를 폴링하다가 MATCHED로 바뀌면 결과 카드를 표시합니다.""")
    @GetMapping("/today")
    public ResponseEntity<MatchResultResponse> getTodayMatch(@RequestParam UUID userId) {
        MatchResultResponse response = matchService.getMatch(userId, LocalDate.now());
        return ResponseEntity.ok(response);
    }
}
