package com.myongjithon.syncday.domain.match.similarity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myongjithon.syncday.domain.analysis.dto.ActivityEntryDto;
import com.myongjithon.syncday.domain.analysis.dto.FeaturesDto;
import com.myongjithon.syncday.domain.analysis.dto.SceneEntryDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisFeaturesTest {

    /** ai-service/README.md 의 POST /api/v1/features 응답 예시를 그대로 옮긴 것. */
    private static final String AI_SERVICE_RESPONSE = """
            {
              "scene": [
                { "category": "카페", "detail": "홍대 감성 카페" },
                { "category": "체육시설", "detail": "수영장" }
              ],
              "timeOfDay": ["아침", "저녁"],
              "mood": ["차분함"],
              "color": ["주황 계열"],
              "activity": [
                { "category": "운동", "detail": "수영" },
                { "category": "휴식", "detail": "카페에서 음료 마시며 휴식" }
              ],
              "summary": "아침 수영으로 상쾌하게 시작해 홍대 감성 카페에서 하루를 마무리했어요."
            }
            """;

    @Test
    @DisplayName("ai-service 가 실제로 내려주는 features JSON 을 그대로 역직렬화한다")
    void deserializesRealAiServiceResponse() throws Exception {
        FeaturesDto dto = new ObjectMapper().readValue(AI_SERVICE_RESPONSE, FeaturesDto.class);

        AnalysisFeatures features = AnalysisFeatures.from(dto);

        assertThat(features.scene()).containsExactly("카페", "체육시설");
        assertThat(features.timeOfDay()).containsExactly("아침", "저녁");
        assertThat(features.activity()).containsExactly("운동", "휴식");
        assertThat(features.mood()).containsExactly("차분함");
        assertThat(features.color()).containsExactly("주황 계열");
    }

    @Test
    @DisplayName("scene/activity 는 category 만 취하고 detail 은 버린다")
    void takesCategoryAndDropsDetail() {
        FeaturesDto dto = new FeaturesDto(
                List.of(new SceneEntryDto("카페", "홍대 감성 카페"),
                        new SceneEntryDto("체육시설", "수영장")),
                List.of("아침", "저녁"),
                List.of("차분함"),
                List.of("주황 계열"),
                List.of(new ActivityEntryDto("운동", "수영")),
                "아침 수영으로 상쾌하게 시작한 하루");

        AnalysisFeatures features = AnalysisFeatures.from(dto);

        assertThat(features.scene()).containsExactly("카페", "체육시설");
        assertThat(features.activity()).containsExactly("운동");
        assertThat(features.timeOfDay()).containsExactly("아침", "저녁");
        assertThat(features.mood()).containsExactly("차분함");
        assertThat(features.color()).containsExactly("주황 계열");
    }

    @Test
    @DisplayName("detail 이 달라도 category 가 같으면 유사도 1.0 이다")
    void detailDoesNotAffectSimilarity() {
        FeaturesDto a = sceneOnly(new SceneEntryDto("카페", "홍대 감성 카페"));
        FeaturesDto b = sceneOnly(new SceneEntryDto("카페", "학교 앞 프랜차이즈 카페"));

        SimilarityResult result = new SimilarityCalculator()
                .calculate(AnalysisFeatures.from(a), AnalysisFeatures.from(b));

        assertThat(result.dimensions().get("scene").sim()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("features 의 각 목록이 null 이어도 안전하게 변환한다")
    void handlesNullLists() {
        FeaturesDto dto = new FeaturesDto(null, null, null, null, null, null);

        AnalysisFeatures features = AnalysisFeatures.from(dto);

        assertThat(features.scene()).isEmpty();
        assertThat(features.activity()).isEmpty();
        assertThat(features.timeOfDay()).isNull();
    }

    private FeaturesDto sceneOnly(SceneEntryDto scene) {
        return new FeaturesDto(List.of(scene), List.of(), List.of(), List.of(), List.of(), "");
    }
}
