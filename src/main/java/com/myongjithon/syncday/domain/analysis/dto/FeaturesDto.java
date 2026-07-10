package com.myongjithon.syncday.domain.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 서비스(ai-service, Gemini 기반)가 반환하는 features 형태와 1:1로 대응된다.
 * scene/activity의 category는 고정 목록, detail은 자유 표현이다.
 * 자세한 스펙은 ai-service/README.md 참고.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FeaturesDto {

    private List<SceneEntryDto> scene;
    private List<String> timeOfDay;
    private List<String> mood;
    private List<String> color;
    private List<ActivityEntryDto> activity;
    private String summary;
}
