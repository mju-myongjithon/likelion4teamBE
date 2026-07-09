package com.myongjithon.syncday.domain.match.similarity;

import com.myongjithon.syncday.domain.analysis.dto.ActivityEntryDto;
import com.myongjithon.syncday.domain.analysis.dto.FeaturesDto;
import com.myongjithon.syncday.domain.analysis.dto.SceneEntryDto;

import java.util.List;

/**
 * 유사도 계산의 입력. F2(ai-service)가 추출한 features 중 비교에 쓰는 5개 축만 뽑은 값 객체다.
 * 다섯 축 모두 문자열 목록이며, ai-service가 고정 어휘로 강제하므로 문자열 비교가 성립한다.
 *
 * scene/activity는 {category, detail} 객체 배열로 오는데 category만 남긴다.
 * detail은 자유 문장이라 문자열 비교가 성립하지 않고, F4/F6이 감성 문장을 만들 때 쓰는 값이다.
 * (ai-service/README.md "API 스펙" 참고)
 */
public record AnalysisFeatures(
        List<String> scene,
        List<String> timeOfDay,
        List<String> activity,
        List<String> mood,
        List<String> color
) {

    /** F2의 features JSON을 역직렬화한 DTO에서 계산 입력을 만든다. 엔티티·Jackson과 계산 로직의 결합을 끊는 지점. */
    public static AnalysisFeatures from(FeaturesDto features) {
        return new AnalysisFeatures(
                sceneCategories(features.getScene()),
                features.getTimeOfDay(),
                activityCategories(features.getActivity()),
                features.getMood(),
                features.getColor()
        );
    }

    private static List<String> sceneCategories(List<SceneEntryDto> entries) {
        if (entries == null) {
            return List.of();
        }
        return entries.stream().map(SceneEntryDto::getCategory).toList();
    }

    private static List<String> activityCategories(List<ActivityEntryDto> entries) {
        if (entries == null) {
            return List.of();
        }
        return entries.stream().map(ActivityEntryDto::getCategory).toList();
    }
}
