package com.myongjithon.syncday.domain.photo.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class PhotoUploadRequest {

    private UUID userId;
    private Boolean isPrivacyMode;
}