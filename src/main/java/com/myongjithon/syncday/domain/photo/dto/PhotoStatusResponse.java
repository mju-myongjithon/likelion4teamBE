package com.myongjithon.syncday.domain.photo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PhotoStatusResponse {

    private int uploadedCount;
    private int requiredCount;
    private boolean readyForAnalysis;

    public static PhotoStatusResponse of(int uploadedCount) {
        int required = 3;
        return PhotoStatusResponse.builder()
                .uploadedCount(uploadedCount)
                .requiredCount(required)
                .readyForAnalysis(uploadedCount >= required)
                .build();
    }
}