package com.myongjithon.syncday.domain.match.similarity;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 두 유저의 하루 분석(F2 산출물)을 비교해 0~100점의 유사도를 계산한다.
 *
 * ai-service가 다섯 축을 모두 고정 어휘의 문자열 목록으로 주므로,
 * 다섯 축 전부 자카드 유사도(|A∩B| / |A∪B|)로 동일하게 계산한다.
 *
 * 최종 점수 = round( Σ (차원 유사도 × 가중치) × 100 ).
 * 의존성이 없어 Spring 컨텍스트 없이 new 로 단위 테스트할 수 있다.
 */
@Component
public class SimilarityCalculator {

    // 기획서 4-3의 가중치 (합 = 1.0). ai-service/app/services/similarity.py 의 참조 구현과 같다.
    static final double W_SCENE = 0.30;
    static final double W_TIME = 0.20;
    static final double W_ACTIVITY = 0.20;
    static final double W_MOOD = 0.20;
    static final double W_COLOR = 0.10;

    public SimilarityResult calculate(AnalysisFeatures a, AnalysisFeatures b) {
        // 순서 보존을 위해 LinkedHashMap 사용 (breakdown 출력 안정성).
        Map<String, DimensionScore> dimensions = new LinkedHashMap<>();
        dimensions.put("scene", jaccardDimension(a.scene(), b.scene(), W_SCENE));
        dimensions.put("timeOfDay", jaccardDimension(a.timeOfDay(), b.timeOfDay(), W_TIME));
        dimensions.put("activity", jaccardDimension(a.activity(), b.activity(), W_ACTIVITY));
        dimensions.put("mood", jaccardDimension(a.mood(), b.mood(), W_MOOD));
        dimensions.put("color", jaccardDimension(a.color(), b.color(), W_COLOR));

        double weighted = dimensions.values().stream()
                .mapToDouble(dimension -> dimension.sim() * dimension.weight())
                .sum();
        int totalScore = (int) Math.round(weighted * 100);

        return new SimilarityResult(totalScore, dimensions);
    }

    private DimensionScore jaccardDimension(List<String> a, List<String> b, double weight) {
        Set<String> setA = normalizeToSet(a);
        Set<String> setB = normalizeToSet(b);

        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
        if (union.isEmpty()) {
            // 양쪽 다 값 없음 → 유사도 근거가 없으므로 0 (0 나눗셈 방지).
            return new DimensionScore(0.0, weight, 0, List.of());
        }

        Set<String> intersection = new LinkedHashSet<>(setA);
        intersection.retainAll(setB);
        double sim = (double) intersection.size() / union.size();

        return new DimensionScore(sim, weight, contribution(sim, weight), List.copyOf(intersection));
    }

    private int contribution(double sim, double weight) {
        return (int) Math.round(sim * weight * 100);
    }

    private Set<String> normalizeToSet(List<String> tags) {
        if (tags == null) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String tag : tags) {
            String normalized = normalize(tag);
            if (normalized != null) {
                result.add(normalized);
            }
        }
        return result;
    }

    // 값 표기 흔들림(공백/대소문자) 방어. ai-service가 enum으로 강제해 주지만 이중 안전장치로 둔다.
    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toLowerCase();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
