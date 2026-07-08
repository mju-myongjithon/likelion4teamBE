package com.myongjithon.syncday.domain.analysis;

import com.myongjithon.syncday.domain.analysis.dto.AnalyzeRequest;
import com.myongjithon.syncday.domain.analysis.dto.AnalyzeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
@Tag(name = "F2. AI 분석", description = "오늘 업로드한 사진을 AI 서비스로 분석합니다")
public class AnalysisController {

    private final AnalysisService analysisService;

    @Operation(summary = "오늘의 나를 분석하기", description = "오늘 업로드한 사진 3장 이상을 ai-service로 보내 특징을 추출하고 저장합니다")
    @PostMapping
    public ResponseEntity<AnalyzeResponse> analyzeToday(@RequestBody AnalyzeRequest request) {
        AnalyzeResponse response = analysisService.analyzeToday(request.getUserId());
        return ResponseEntity.ok(response);
    }
}
