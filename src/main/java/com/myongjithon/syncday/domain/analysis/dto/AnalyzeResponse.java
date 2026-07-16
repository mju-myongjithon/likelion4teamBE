package com.myongjithon.syncday.domain.analysis.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class AnalyzeResponse {

    private UUID analysisId;
    private FeaturesDto features;
    private Integer currentStreak;

    public static AnalyzeResponse of(UUID analysisId, FeaturesDto features, Integer currentStreak) {
        return AnalyzeResponse.builder()
                .analysisId(analysisId)
                .features(features)
                .currentStreak(currentStreak)
                .build();
    }
}
