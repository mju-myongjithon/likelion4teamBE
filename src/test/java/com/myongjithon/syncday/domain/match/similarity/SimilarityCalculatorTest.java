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
                List.of("카페", "캠퍼스 야외"), List.of("오후"), List.of("공부"),
                List.of("차분함"), List.of("파란 계열"));

        SimilarityResult result = calculator.calculate(feature, feature);

        assertThat(result.totalScore()).isEqualTo(100);
    }

    @Test
    @DisplayName("모든 차원이 다르면 0점이다")
    void completelyDifferentScore0() {
        AnalysisFeatures a = new AnalysisFeatures(
                List.of("카페"), List.of("아침"), List.of("공부"),
                List.of("차분함"), List.of("파란 계열"));
        AnalysisFeatures b = new AnalysisFeatures(
                List.of("체육시설"), List.of("밤"), List.of("운동"),
                List.of("활기참"), List.of("주황 계열"));

        SimilarityResult result = calculator.calculate(a, b);

        assertThat(result.totalScore()).isEqualTo(0);
    }

    @Test
    @DisplayName("scene 은 자카드 유사도로 계산되고 교집합을 commonTags 로 반환한다")
    void sceneUsesJaccard() {
        AnalysisFeatures a = new AnalysisFeatures(
                List.of("카페", "캠퍼스 야외"), null, null, null, null);
        AnalysisFeatures b = new AnalysisFeatures(
                List.of("카페", "체육시설"), null, null, null, null);

        SimilarityResult result = calculator.calculate(a, b);
        DimensionScore scene = result.dimensions().get("scene");

        // 교집합 {카페}=1, 합집합 {카페,캠퍼스 야외,체육시설}=3 → 1/3
        assertThat(scene.sim()).isCloseTo(1.0 / 3.0, within(1e-9));
        assertThat(scene.commonTags()).containsExactly("카페");
        assertThat(scene.contribution()).isEqualTo(10); // round(0.3333 * 0.30 * 100)
    }

    @Test
    @DisplayName("다중값으로 오는 timeOfDay/mood/color 도 자카드로 계산한다")
    void multiValuedDimensionsUseJaccard() {
        AnalysisFeatures a = new AnalysisFeatures(
                null, List.of("아침", "저녁"), null, List.of("차분함", "설렘"), List.of("파란 계열"));
        AnalysisFeatures b = new AnalysisFeatures(
                null, List.of("저녁"), null, List.of("설렘"), List.of("파란 계열", "다채로움"));

        SimilarityResult result = calculator.calculate(a, b);

        // 각각 교집합 1 / 합집합 2 → 0.5, color 도 1/2
        assertThat(result.dimensions().get("timeOfDay").sim()).isCloseTo(0.5, within(1e-9));
        assertThat(result.dimensions().get("timeOfDay").commonTags()).containsExactly("저녁");
        assertThat(result.dimensions().get("mood").sim()).isCloseTo(0.5, within(1e-9));
        assertThat(result.dimensions().get("color").sim()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    @DisplayName("가중치는 기획서 4-3 기준이다 (scene 30 / timeOfDay 20 / activity 20 / mood 20 / color 10)")
    void usesSpecifiedWeights() {
        SimilarityResult result = calculator.calculate(
                new AnalysisFeatures(null, null, null, null, null),
                new AnalysisFeatures(null, null, null, null, null));

        assertThat(result.dimensions().get("scene").weight()).isEqualTo(0.30);
        assertThat(result.dimensions().get("timeOfDay").weight()).isEqualTo(0.20);
        assertThat(result.dimensions().get("activity").weight()).isEqualTo(0.20);
        assertThat(result.dimensions().get("mood").weight()).isEqualTo(0.20);
        assertThat(result.dimensions().get("color").weight()).isEqualTo(0.10);
    }

    @Test
    @DisplayName("한 차원만 완전히 겹치면 그 차원의 가중치만큼만 점수가 오른다")
    void singleDimensionContributesItsWeight() {
        AnalysisFeatures a = new AnalysisFeatures(
                List.of("카페"), List.of("아침"), null, null, null);
        AnalysisFeatures b = new AnalysisFeatures(
                List.of("카페"), List.of("밤"), null, null, null);

        SimilarityResult result = calculator.calculate(a, b);

        assertThat(result.totalScore()).isEqualTo(30); // scene 만 1.0 → 0.30
    }

    @Test
    @DisplayName("빈 목록은 0 나눗셈 없이 sim 0 으로 처리한다")
    void emptyListsDoNotDivideByZero() {
        AnalysisFeatures a = new AnalysisFeatures(List.of(), List.of(), List.of(), List.of(), List.of());
        AnalysisFeatures b = new AnalysisFeatures(List.of(), List.of(), List.of(), List.of(), List.of());

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
                List.of(" Cafe "), null, null, List.of("Calm"), List.of("BLUE"));
        AnalysisFeatures b = new AnalysisFeatures(
                List.of("cafe"), null, null, List.of("calm"), List.of("blue"));

        SimilarityResult result = calculator.calculate(a, b);

        assertThat(result.dimensions().get("scene").sim()).isEqualTo(1.0);
        assertThat(result.dimensions().get("mood").sim()).isEqualTo(1.0);
        assertThat(result.dimensions().get("color").sim()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("부분 일치 시 차원별 기여도의 합이 총점과 일치한다")
    void breakdownContributionsSumToTotal() {
        AnalysisFeatures a = new AnalysisFeatures(
                List.of("카페", "캠퍼스 야외"), List.of("오후"), List.of("공부", "휴식"),
                List.of("차분함"), List.of("파란 계열"));
        AnalysisFeatures b = new AnalysisFeatures(
                List.of("카페"), List.of("저녁"), List.of("공부"),
                List.of("차분함"), List.of("주황 계열"));

        SimilarityResult result = calculator.calculate(a, b);

        int sumOfContributions = result.dimensions().values().stream()
                .mapToInt(DimensionScore::contribution)
                .sum();

        // 개별 반올림 누적이라 총점과 ±5 이내 오차 허용 (표시용 값이므로 근사만 검증).
        assertThat(sumOfContributions).isCloseTo(result.totalScore(), within(5));
    }
}
