package com.myongjithon.syncday.domain.match.similarity;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 두 유저의 하루 분석(DailyAnalysis)을 비교해 0~100점의 유사도를 계산한다.
 *
 * 차원별 계산:
 *  - scene_tags / activity_tags : 자카드 유사도 (|A∩B| / |A∪B|)
 *  - mood / dominant_color      : 완전일치(1.0) / 불일치(0.0)
 *  - time_of_day                : 순서 거리 (인접할수록 유사)
 *
 * 최종 점수 = round( Σ (차원 유사도 × 가중치) × 100 ).
 * 의존성이 없어 Spring 컨텍스트 없이 new 로 단위 테스트할 수 있다.
 */
@Component
public class SimilarityCalculator {

    // 차원별 가중치 (합 = 1.0). 데모 중 튜닝 대상이라 상수로 분리한다.
    static final double W_SCENE = 0.30;
    static final double W_ACTIVITY = 0.30;
    static final double W_MOOD = 0.20;
    static final double W_TIME = 0.10;
    static final double W_COLOR = 0.10;

    // time_of_day 의 순서. 인접할수록(아침↔오후) 유사하고 멀수록(아침↔밤) 낮다.
    // ※ F2가 확정하는 값 규약에 맞춰 갱신 필요 (현재는 한글 4단계 가정).
    private static final Map<String, Integer> TIME_ORDER = Map.of(
            "아침", 0,
            "오후", 1,
            "저녁", 2,
            "밤", 3
    );

    public SimilarityResult calculate(AnalysisFeatures a, AnalysisFeatures b) {
        DimensionScore scene = tagDimension(a.sceneTags(), b.sceneTags(), W_SCENE);
        DimensionScore activity = tagDimension(a.activityTags(), b.activityTags(), W_ACTIVITY);
        DimensionScore mood = categoryDimension(a.mood(), b.mood(), W_MOOD, exactSim(a.mood(), b.mood()));
        DimensionScore time = categoryDimension(a.timeOfDay(), b.timeOfDay(), W_TIME, timeSim(a.timeOfDay(), b.timeOfDay()));
        DimensionScore color = categoryDimension(a.dominantColor(), b.dominantColor(), W_COLOR, exactSim(a.dominantColor(), b.dominantColor()));

        double weighted = scene.sim() * W_SCENE
                + activity.sim() * W_ACTIVITY
                + mood.sim() * W_MOOD
                + time.sim() * W_TIME
                + color.sim() * W_COLOR;
        int totalScore = (int) Math.round(weighted * 100);

        // 순서 보존을 위해 LinkedHashMap 사용 (breakdown 출력 안정성).
        Map<String, DimensionScore> dimensions = new LinkedHashMap<>();
        dimensions.put("scene", scene);
        dimensions.put("activity", activity);
        dimensions.put("mood", mood);
        dimensions.put("time", time);
        dimensions.put("color", color);

        return new SimilarityResult(totalScore, dimensions);
    }

    // ---- 태그형 차원: 자카드 유사도 ----
    private DimensionScore tagDimension(List<String> a, List<String> b, double weight) {
        Set<String> setA = normalizeToSet(a);
        Set<String> setB = normalizeToSet(b);

        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);

        double sim;
        List<String> commonTags;
        if (union.isEmpty()) {
            // 양쪽 다 태그 없음 → 유사도 근거가 없으므로 0 (0 나눗셈 방지).
            sim = 0.0;
            commonTags = List.of();
        } else {
            Set<String> intersection = new LinkedHashSet<>(setA);
            intersection.retainAll(setB);
            sim = (double) intersection.size() / union.size();
            commonTags = new ArrayList<>(intersection);
        }
        return new DimensionScore(sim, weight, contribution(sim, weight), commonTags, null, null);
    }

    // ---- 카테고리형 차원: sim 은 호출부에서 계산해 넘긴다 ----
    private DimensionScore categoryDimension(String a, String b, double weight, double sim) {
        return new DimensionScore(sim, weight, contribution(sim, weight), null, normalize(a), normalize(b));
    }

    private double exactSim(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);
        if (na == null || nb == null) {
            return 0.0;
        }
        return na.equals(nb) ? 1.0 : 0.0;
    }

    private double timeSim(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);
        if (na == null || nb == null) {
            return 0.0;
        }
        Integer ia = TIME_ORDER.get(na);
        Integer ib = TIME_ORDER.get(nb);
        if (ia == null || ib == null) {
            // 순서 표에 없는 값이면 완전일치로만 판정 (규약 밖 값 방어).
            return na.equals(nb) ? 1.0 : 0.0;
        }
        int maxSpan = TIME_ORDER.size() - 1;
        return 1.0 - (double) Math.abs(ia - ib) / maxSpan;
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

    // 값 표기 흔들림(공백/대소문자) 방어. F2가 정규화해 준다는 전제여도 이중 안전장치로 둔다.
    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toLowerCase();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
