package com.myongjithon.syncday.domain.match.similarity;

import java.util.List;

/**
 * 유사도 계산의 입력. DailyAnalysis(F2 산출물)에서 비교 대상 필드만 추출한 값 객체다.
 * JPA 엔티티와 분리해 계산 로직을 순수하게(=Spring 컨텍스트 없이 테스트 가능하게) 유지한다.
 */
public record AnalysisFeatures(
        List<String> sceneTags,
        String timeOfDay,
        String mood,
        String dominantColor,
        List<String> activityTags
) {
}
