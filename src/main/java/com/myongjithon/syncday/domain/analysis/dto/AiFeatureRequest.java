package com.myongjithon.syncday.domain.analysis.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * AI 서비스의 POST /api/v1/features 요청 바디.
 */
@Getter
@Builder
public class AiFeatureRequest {

    private String userId;
    private String date;
    private List<String> imageUrls;
}
