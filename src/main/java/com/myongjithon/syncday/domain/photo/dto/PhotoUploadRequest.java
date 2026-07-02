package com.myongjithon.syncday.domain.photo.dto;

import java.util.UUID;

public class PhotoUploadRequest {
    private UUID userId;
    private Boolean isPrivacyMode;
    // MultipartFile은 Controller에서 별도 파라미터로 받음
}