package com.myongjithon.syncday.domain.photo;

import com.myongjithon.syncday.domain.photo.dto.PhotoStatusResponse;
import com.myongjithon.syncday.domain.photo.dto.PhotoUploadResponse;
import com.myongjithon.syncday.domain.user.AppUser;
import com.myongjithon.syncday.domain.user.AppUserRepository;
import com.myongjithon.syncday.global.exception.PhotoErrorCode;
import com.myongjithon.syncday.global.exception.PhotoUploadException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PhotoService {

    private static final int REQUIRED_PHOTO_COUNT = 3;

    private final PhotoRepository photoRepository;
    private final AppUserRepository appUserRepository;

    @Transactional
    public PhotoUploadResponse uploadPhoto(UUID userId, MultipartFile file, boolean isPrivacyMode) {
        validateImageFormat(file);

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new PhotoUploadException(PhotoErrorCode.USER_NOT_FOUND));

        String imageUrl = uploadToS3(file, isPrivacyMode);

        Photo photo = Photo.builder()
                .user(user)
                .imageUrl(imageUrl)
                .isPrivacyMode(isPrivacyMode)
                .build();

        Photo savedPhoto = photoRepository.save(photo);

        return PhotoUploadResponse.from(savedPhoto);
    }

    public PhotoStatusResponse getTodayStatus(UUID userId) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1).minusNanos(1);

        int count = photoRepository.countByUser_UserIdAndUploadedAtBetween(
                userId, startOfDay, endOfDay
        );

        return PhotoStatusResponse.of(count);
    }

    private void validateImageFormat(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null ||
                (!contentType.equals("image/jpeg") && !contentType.equals("image/png"))) {
            throw new PhotoUploadException(PhotoErrorCode.INVALID_IMAGE_FORMAT);
        }
    }

    private String uploadToS3(MultipartFile file, boolean isPrivacyMode) {
        // TODO: AWS S3 세팅 완료 후 구현
        // 1. isPrivacyMode == true면 FaceMosaicUtil로 모자이크 처리
        // 2. S3Config로 만든 S3Client로 업로드
        // 3. 업로드된 S3 URL 반환
        throw new UnsupportedOperationException("S3 연동 아직 미구현");
    }
}