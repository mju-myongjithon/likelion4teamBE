package com.myongjithon.syncday.domain.analysis.dto;

/**
 * ai-service F6(POST /api/v1/icebreaker) 요청.
 * 두 유저의 하루 특징(FeaturesDto == DayFeatures)을 보내 아이스브레이킹 질문을 생성한다.
 * (F4와 달리 유사도 점수는 필요 없다.)
 */
public record AiIcebreakerRequest(
        FeaturesDto userA,
        FeaturesDto userB
) {
}
