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

    public static PhotoUploadResponse from(Photo photo, String presignedUrl) {
        return PhotoUploadResponse.builder()
                .photoId(photo.getPhotoId())
                .imageUrl(presignedUrl)  // 이제 presigned URL을 응답으로 줌
                .uploadedAt(photo.getUploadedAt())
                .build();
    }
}