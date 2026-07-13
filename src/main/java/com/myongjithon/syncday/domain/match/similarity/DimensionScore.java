package com.myongjithon.syncday.domain.match.similarity;

import java.util.List;

/**
 * 한 차원(scene/timeOfDay/activity/mood/color)의 유사도 계산 결과.
 * match.score_breakdown(jsonb) 직렬화 및 F4(감성 설명)/F8(근거 상세) 소비용.
 *
 * 다섯 차원 모두 문자열 목록을 자카드로 비교하므로 형태가 같다.
 */
public record DimensionScore(
        double sim,                // 0.0 ~ 1.0
        double weight,
        int contribution,          // round(sim * weight * 100)
        List<String> commonTags    // 두 유저가 공통으로 가진 값
) {
}
