package com.myongjithon.syncday.domain.photo;

import com.myongjithon.syncday.domain.photo.dto.PhotoStatusResponse;
import com.myongjithon.syncday.domain.photo.dto.PhotoUploadResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class PhotoService {
    public PhotoUploadResponse uploadPhoto(UUID userId, MultipartFile file, boolean privacyMode) {
        return null;
    }

    public PhotoStatusResponse getTodayStatus(UUID userId) {
        return null;
    }

    private String uploadToS3(MultipartFile file) {
        return null;
    }

    private MultipartFile applyMosaicIfNeeded(MultipartFile file, boolean privacyMode) {
        return null;
    }
}