package com.myongjithon.syncday.domain.photo.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public class PhotoUploadResponse {
    private UUID photoId;
    private String imageUrl;
    private LocalDateTime uploadedAt;
}