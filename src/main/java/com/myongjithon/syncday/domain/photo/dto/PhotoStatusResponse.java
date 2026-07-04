package com.myongjithon.syncday.domain.photo.dto;

import com.myongjithon.syncday.domain.photo.Photo;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PhotoStatusResponse {

    private int uploadedCount;
    private int requiredCount;
    private boolean readyForAnalysis;

    public static PhotoStatusResponse of(int uploadedCount) {
        return PhotoStatusResponse.builder()
                .uploadedCount(uploadedCount)
                .requiredCount(Photo.REQUIRED_PHOTO_COUNT)
                .readyForAnalysis(uploadedCount >= Photo.REQUIRED_PHOTO_COUNT)
                .build();
    }

}