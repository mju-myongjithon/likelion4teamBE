package com.myongjithon.syncday.domain.analysis.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 서비스의 POST /api/v1/features 응답 바디.
 */
@Getter
@NoArgsConstructor
public class AiFeatureResponse {

    private String userId;
    private String date;
    private FeaturesDto features;
}
