package com.myongjithon.syncday.domain.analysis.dto;

/**
 * ai-service F4(POST /api/v1/description) 요청.
 * 두 유저의 하루 특징(FeaturesDto == DayFeatures)과 유사도 점수를 보내 유사도 코멘트를 생성한다.
 */
public record AiDescriptionRequest(
        int similarityScore,
        FeaturesDto userA,
        FeaturesDto userB
) {
}
