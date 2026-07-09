package com.myongjithon.syncday.domain.photo;

import com.myongjithon.syncday.domain.photo.dto.PhotoStatusResponse;
import com.myongjithon.syncday.domain.photo.dto.PhotoUploadRequest;
import com.myongjithon.syncday.domain.photo.dto.PhotoUploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/photos")
@RequiredArgsConstructor
@Tag(name = "F1. 사진 업로드", description = "사진 업로드 및 프라이버시 처리 관련 API")
public class PhotoController {

    private final PhotoService photoService;

    @Operation(summary = "사진 업로드", description = "오늘의 사진을 업로드합니다. 최소 3장 필요, 프라이버시 모드 기본 ON")
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<PhotoUploadResponse> uploadPhoto(
            @ModelAttribute PhotoUploadRequest request
    ) {
        boolean privacyMode = request.getIsPrivacyMode() != null ? request.getIsPrivacyMode() : true;
        PhotoUploadResponse response = photoService.uploadPhoto(
                request.getUserId(), request.getFile(), privacyMode
        );
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

    @Operation(summary = "오늘 업로드한 사진 목록 조회", description = "오늘 업로드한 사진들의 URL과 업로드 시각을 반환합니다")
    @GetMapping
    public ResponseEntity<List<PhotoUploadResponse>> getTodayPhotos(
            @RequestParam UUID userId
    ) {
        List<PhotoUploadResponse> response = photoService.getTodayPhotos(userId);
        return ResponseEntity.ok(response);
    }
}