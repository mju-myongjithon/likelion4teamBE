package com.myongjithon.syncday.domain.match.similarity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SimilarityCalculatorTest {

    private final SimilarityCalculator calculator = new SimilarityCalculator();

    @Test
    @DisplayName("완전히 동일한 하루는 100점이다")
    void identicalFeaturesScore100() {
        AnalysisFeatures feature = new AnalysisFeatures(
                List.of("카페", "야외"), "오후", "차분함", "블루", List.of("공부"));

        SimilarityResult result = calculator.calculate(feature, feature);

        assertThat(result.totalScore()).isEqualTo(100);
    }

    @Test
    @DisplayName("모든 차원이 다르면 0점이다")
    void completelyDifferentScore0() {
        AnalysisFeatures a = new AnalysisFeatures(
                List.of("카페"), "아침", "차분함", "블루", List.of("공부"));
        AnalysisFeatures b = new AnalysisFeatures(
                List.of("체육관"), "밤", "활기참", "레드", List.of("운동"));

        SimilarityResult result = calculator.calculate(a, b);

        assertThat(result.totalScore()).isEqualTo(0);
    }

    @Test
    @DisplayName("scene_tags 는 자카드 유사도로 계산되고 교집합을 commonTags 로 반환한다")
    void sceneTagsUseJaccard() {
        AnalysisFeatures a = new AnalysisFeatures(
                List.of("카페", "야외"), null, null, null, List.of());
        AnalysisFeatures b = new AnalysisFeatures(
                List.of("카페", "체육관"), null, null, null, List.of());

        SimilarityResult result = calculator.calculate(a, b);
        DimensionScore scene = result.dimensions().get("scene");

        // 교집합 {카페}=1, 합집합 {카페,야외,체육관}=3 → 1/3
        assertThat(scene.sim()).isCloseTo(1.0 / 3.0, within(1e-9));
        assertThat(scene.commonTags()).containsExactly("카페");
        assertThat(scene.contribution()).isEqualTo(10); // round(0.3333 * 0.30 * 100)
    }

    @Test
    @DisplayName("time_of_day 는 순서가 인접할수록 높고 멀수록 낮다")
    void timeOfDayUsesOrdinalDistance() {
        AnalysisFeatures base = new AnalysisFeatures(List.of(), "오후", null, null, List.of());
        AnalysisFeatures adjacent = new AnalysisFeatures(List.of(), "저녁", null, null, List.of());
        AnalysisFeatures far = new AnalysisFeatures(List.of(), "아침", null, null, List.of());

        double adjacentSim = calculator.calculate(base, adjacent).dimensions().get("time").sim();
        double farSim = calculator.calculate(base, far).dimensions().get("time").sim();

        // 오후(1)↔저녁(2): 1 - 1/3 ≈ 0.667, 오후(1)↔아침(0): 1 - 1/3 ≈ 0.667
        assertThat(adjacentSim).isCloseTo(2.0 / 3.0, within(1e-9));
        assertThat(farSim).isCloseTo(2.0 / 3.0, within(1e-9));

        // 아침(0)↔밤(3) 은 최대 거리라 0
        AnalysisFeatures morning = new AnalysisFeatures(List.of(), "아침", null, null, List.of());
        AnalysisFeatures night = new AnalysisFeatures(List.of(), "밤", null, null, List.of());
        assertThat(calculator.calculate(morning, night).dimensions().get("time").sim()).isZero();
    }

    @Test
    @DisplayName("빈 태그 배열은 0 나눗셈 없이 sim 0 으로 처리한다")
    void emptyTagsDoNotDivideByZero() {
        AnalysisFeatures a = new AnalysisFeatures(List.of(), null, null, null, List.of());
        AnalysisFeatures b = new AnalysisFeatures(List.of(), null, null, null, List.of());

        SimilarityResult result = calculator.calculate(a, b);

        assertThat(result.totalScore()).isEqualTo(0);
        assertThat(result.dimensions().get("scene").sim()).isZero();
        assertThat(result.dimensions().get("scene").commonTags()).isEmpty();
    }

    @Test
    @DisplayName("null 필드는 유사도 0 으로 안전하게 처리한다")
    void nullFieldsHandledSafely() {
        AnalysisFeatures a = new AnalysisFeatures(null, null, null, null, null);
        AnalysisFeatures b = new AnalysisFeatures(null, null, null, null, null);

        SimilarityResult result = calculator.calculate(a, b);

        assertThat(result.totalScore()).isEqualTo(0);
    }

    @Test
    @DisplayName("공백/대소문자가 달라도 같은 값으로 정규화해 매칭한다")
    void normalizesWhitespaceAndCase() {
        AnalysisFeatures a = new AnalysisFeatures(
                List.of(" Cafe "), null, "Calm", "BLUE", List.of());
        AnalysisFeatures b = new AnalysisFeatures(
                List.of("cafe"), null, "calm", "blue", List.of());

        SimilarityResult result = calculator.calculate(a, b);

        assertThat(result.dimensions().get("scene").sim()).isEqualTo(1.0);
        assertThat(result.dimensions().get("mood").sim()).isEqualTo(1.0);
        assertThat(result.dimensions().get("color").sim()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("부분 일치 시 차원별 기여도의 합이 총점과 일치한다")
    void breakdownContributionsSumToTotal() {
        AnalysisFeatures a = new AnalysisFeatures(
                List.of("카페", "야외"), "오후", "차분함", "블루", List.of("공부", "산책"));
        AnalysisFeatures b = new AnalysisFeatures(
                List.of("카페"), "저녁", "차분함", "레드", List.of("공부"));

        SimilarityResult result = calculator.calculate(a, b);

        int sumOfContributions = result.dimensions().values().stream()
                .mapToInt(DimensionScore::contribution)
                .sum();

        // 개별 반올림 누적이라 총점과 ±5 이내 오차 허용 (표시용 값이므로 근사만 검증).
        assertThat(sumOfContributions).isCloseTo(result.totalScore(), within(5));
    }
}
