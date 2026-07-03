package com.myongjithon.syncday.domain.photo;

import com.myongjithon.syncday.domain.photo.dto.PhotoStatusResponse;
import com.myongjithon.syncday.domain.photo.dto.PhotoUploadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/photos")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photoService;

    @PostMapping
    public ResponseEntity<PhotoUploadResponse> uploadPhoto(
            @RequestParam UUID userId,
            @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "true") boolean isPrivacyMode
    ) {
        PhotoUploadResponse response = photoService.uploadPhoto(userId, file, isPrivacyMode);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<PhotoStatusResponse> getTodayStatus(
            @RequestParam UUID userId
    ) {
        PhotoStatusResponse response = photoService.getTodayStatus(userId);
        return ResponseEntity.ok(response);
    }
}