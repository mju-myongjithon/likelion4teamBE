package com.myongjithon.syncday.domain.photo.dto;

import com.myongjithon.syncday.domain.photo.Photo;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class PhotoUploadResponse {

    private UUID photoId;
    private String imageUrl;
    private LocalDateTime uploadedAt;

    public static PhotoUploadResponse from(Photo photo) {
        return PhotoUploadResponse.builder()
                .photoId(photo.getPhotoId())
                .imageUrl(photo.getImageUrl())
                .uploadedAt(photo.getUploadedAt())
                .build();
    }
}