package com.myongjithon.syncday.domain.photo;

import com.myongjithon.syncday.domain.photo.dto.PhotoStatusResponse;
import com.myongjithon.syncday.domain.photo.dto.PhotoUploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/photos")
@RequiredArgsConstructor
@Tag(name = "F1. 사진 업로드", description = "사진 업로드 및 프라이버시 처리 관련 API")
public class PhotoController {

    private final PhotoService photoService;

    @Operation(summary = "사진 업로드", description = "오늘의 사진을 업로드합니다. 최소 3장 필요, 프라이버시 모드 기본 ON")
    @PostMapping
    public ResponseEntity<PhotoUploadResponse> uploadPhoto(
            @RequestParam UUID userId,
            @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "true") boolean isPrivacyMode
    ) {
        PhotoUploadResponse response = photoService.uploadPhoto(userId, file, isPrivacyMode);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "오늘 업로드 상태 조회", description = "오늘 업로드한 사진 개수와 분석 가능 여부를 반환합니다")
    @GetMapping("/status")
    public ResponseEntity<PhotoStatusResponse> getTodayStatus(
            @RequestParam UUID userId
    ) {
        PhotoStatusResponse response = photoService.getTodayStatus(userId);
        return ResponseEntity.ok(response);
    }
}