package com.myongjithon.syncday.domain.analysis;

import com.myongjithon.syncday.domain.analysis.dto.AnalyzeRequest;
import com.myongjithon.syncday.domain.analysis.dto.AnalyzeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
@Tag(name = "F2. AI 분석", description = "오늘 업로드한 사진을 AI 서비스로 분석합니다")
public class AnalysisController {

    private final AnalysisService analysisService;

    @Operation(summary = "오늘의 나를 분석하기", description = "오늘 업로드한 사진 3장 이상을 ai-service로 보내 특징을 추출하고 저장합니다. 이미 분석했으면 기존 결과를 그대로 반환합니다.")
    @PostMapping
    public ResponseEntity<AnalyzeResponse> analyzeToday(@RequestBody @Valid AnalyzeRequest request) {
        AnalyzeResponse response;
        try {
            response = analysisService.analyzeToday(request.getUserId());
        } catch (DataIntegrityViolationException e) {
            // 같은 유저가 짧은 간격으로 두 번 요청해 유니크 제약(uk_analysis_user_date)이 충돌한 경우.
            // 다른 요청이 이미 분석을 커밋했다는 뜻이므로, 조회로 그 결과를 그대로 돌려준다(멱등).
            response = analysisService.getTodayAnalysis(request.getUserId());
        }
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "오늘 분석 결과 조회", description = "오늘 이미 분석한 기록이 있으면 그대로 조회만 합니다 (새로 분석하지 않음)")
    @GetMapping("/today")
    public ResponseEntity<AnalyzeResponse> getTodayAnalysis(@RequestParam UUID userId) {
        AnalyzeResponse response = analysisService.getTodayAnalysis(userId);
        return ResponseEntity.ok(response);
    }
}
