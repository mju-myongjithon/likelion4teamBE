package com.myongjithon.syncday.domain.analysis.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * BE -> FE, 분석 결과 응답. analysisId는 이후 F3 매칭/F4/F6 호출 시 참조용으로 쓸 수 있다.
 */
@Getter
@Builder
public class AnalyzeResponse {

    private UUID analysisId;
    private FeaturesDto features;

    public static AnalyzeResponse of(UUID analysisId, FeaturesDto features) {
        return AnalyzeResponse.builder()
                .analysisId(analysisId)
                .features(features)
                .build();
    }
}
