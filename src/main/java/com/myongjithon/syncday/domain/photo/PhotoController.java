package com.myongjithon.syncday.domain.photo;

import com.myongjithon.syncday.domain.photo.dto.PhotoStatusResponse;
import com.myongjithon.syncday.domain.photo.dto.PhotoUploadResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/photos")
public class PhotoController {

    @PostMapping                       // 사진 업로드
    public ResponseEntity<PhotoUploadResponse> upload() {
        return null;
    }

    @GetMapping("/status")             // 오늘 업로드 상태 조회
    public ResponseEntity<PhotoStatusResponse> getStatus() {
        return null;
    }
}