// PhotoUploadRequest.java 수정
package com.myongjithon.syncday.domain.photo.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class PhotoUploadRequest {

    private UUID userId;
    private MultipartFile file;
    private Boolean isPrivacyMode = true;
}