package com.myongjithon.syncday.domain.match.similarity;

import java.util.List;

/**
 * 한 차원(scene/activity/mood/time/color)의 유사도 계산 결과.
 * match.score_breakdown(jsonb) 직렬화 및 F4(감성 설명)/F8(근거 상세) 소비용.
 *
 * 태그형 차원(scene/activity)은 commonTags를, 카테고리형 차원(mood/time/color)은 valueA/valueB를 채운다.
 * 사용하지 않는 필드는 null이다.
 */
public record DimensionScore(
        double sim,                // 0.0 ~ 1.0
        double weight,
        int contribution,          // round(sim * weight * 100)
        List<String> commonTags,   // 태그형 차원에서만
        String valueA,             // 카테고리형 차원에서만
        String valueB
) {
}
