package com.myongjithon.syncday.domain.match;

import com.myongjithon.syncday.domain.match.dto.MatchResultResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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

    @Operation(summary = "매칭 수락 & 실행 (FE 폴링 엔드포인트)", description = """
            "매칭 수락"에 해당합니다. 유저를 오늘 매칭 대상으로 등록(opt-in)하고, 마찬가지로 수락한 반대 캠퍼스 상대와 매칭합니다.
            이미 매칭됐다면 기존 결과를 반환합니다(멱등). 아직 수락한 상대가 없으면 에러가 아니라 status=PENDING 을 반환하므로,
            FE는 "매칭중..." 화면을 유지한 채 이 엔드포인트를 status=MATCHED 가 될 때까지 주기적으로 다시 호출(폴링)하면 됩니다.""")
    @PostMapping
    public ResponseEntity<MatchResultResponse> createMatch(@RequestParam UUID userId) {
        LocalDate today = LocalDate.now();
        MatchResultResponse response;
        try {
            response = matchService.createMatchForUser(userId, today);
        } catch (DataIntegrityViolationException e) {
            // 반대 캠퍼스 상대가 같은 순간에 같은 쌍을 저장해 유니크 제약이 충돌한 경우.
            // 상대 트랜잭션이 매칭을 이미 커밋했다는 뜻이므로, 조회로 성사된 결과를 그대로 돌려준다(멱등).
            response = matchService.getMatch(userId, today);
        }
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "매칭 거부", description = """
            "매칭 거부"에 해당합니다. 유저를 오늘 매칭에서 제외(opt-out)해 남의 상대로도 잡히지 않게 합니다. status=DECLINED 를 반환합니다.
            매칭이 이미 성사된 뒤라면 거부하기엔 늦었으므로 기존 매칭(status=MATCHED)을 그대로 반환합니다.""")
    @PostMapping("/decline")
    public ResponseEntity<MatchResultResponse> declineMatch(@RequestParam UUID userId) {
        MatchResultResponse response = matchService.declineMatch(userId, LocalDate.now());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "채팅 참여 수락 (게이트2)", description = """
            매칭 성사 후 상대를 확인하고 "채팅을 열겠다"고 수락합니다(게이트2). 상대도 수락하면 status=CONNECTED 가 되고
            유사도·근거가 응답에 공개되며, 이때부터 F5가 채팅방을 엽니다. 상대가 아직이면 status=AWAITING_PARTNER 를
            반환하므로 FE는 "상대 응답 대기" 화면을 유지한 채 GET /api/matches/today 를 폴링하면 됩니다.
            이미 연결됐거나 종료된 뒤에는 상태가 바뀌지 않습니다(멱등).""")
    @PostMapping("/chat/accept")
    public ResponseEntity<MatchResultResponse> acceptChat(@RequestParam UUID userId) {
        MatchResultResponse response =
                matchService.applyChatDecision(userId, LocalDate.now(), Gate2Decision.ACCEPTED);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "채팅 참여 거부 (게이트2)", description = """
            매칭 성사 후 상대와의 채팅을 거부합니다(게이트2). status=ENDED 가 되어 그날 매칭은 종료됩니다.
            한 명이라도 거부하면 종료되며, 이미 연결됐거나 종료된 뒤에는 상태가 바뀌지 않습니다(멱등).""")
    @PostMapping("/chat/reject")
    public ResponseEntity<MatchResultResponse> rejectChat(@RequestParam UUID userId) {
        MatchResultResponse response =
                matchService.applyChatDecision(userId, LocalDate.now(), Gate2Decision.REJECTED);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "오늘의 매칭 조회 (읽기 전용)", description = """
            유저의 오늘 매칭 결과를 조회만 합니다(매칭을 새로 시도하지 않음). 매칭 전이면 status=PENDING, 성사되면 status=MATCHED 와 함께 match 가 채워집니다.
            "매칭중..." 화면에서 실제 매칭을 진행시키려면 POST /api/matches 를 폴링하세요. 이 GET 은 이미 성사된 결과를 화면 재진입 시 다시 불러올 때 씁니다.""")
    @GetMapping("/today")
    public ResponseEntity<MatchResultResponse> getTodayMatch(@RequestParam UUID userId) {
        MatchResultResponse response = matchService.getMatch(userId, LocalDate.now());
        return ResponseEntity.ok(response);
    }
}
