package com.myongjithon.syncday.domain.match.similarity;

import java.util.Map;

/**
 * 두 하루 분석 간 최종 유사도.
 * totalScore(0~100)는 match.similarity_score에, dimensions는 match.score_breakdown에 대응된다.
 */
public record SimilarityResult(
        int totalScore,
        Map<String, DimensionScore> dimensions
) {
}
