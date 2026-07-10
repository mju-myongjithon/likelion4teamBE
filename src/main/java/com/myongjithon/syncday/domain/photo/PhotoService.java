package com.myongjithon.syncday.domain.photo;

import com.myongjithon.syncday.domain.photo.dto.PhotoStatusResponse;
import com.myongjithon.syncday.domain.photo.dto.PhotoUploadResponse;
import com.myongjithon.syncday.domain.user.AppUser;
import com.myongjithon.syncday.domain.user.AppUserRepository;
import com.myongjithon.syncday.global.exception.PhotoErrorCode;
import com.myongjithon.syncday.global.exception.PhotoUploadException;
import com.myongjithon.syncday.global.util.FaceMosaicUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final AppUserRepository appUserRepository;
    private final S3Client s3Client;
    private final RekognitionClient rekognitionClient;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Transactional
    public PhotoUploadResponse uploadPhoto(UUID userId, MultipartFile file, boolean isPrivacyMode) {
        validateImageFormat(file);

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new PhotoUploadException(PhotoErrorCode.USER_NOT_FOUND));

        TodayRange today = getTodayRange();
        int todayCount = photoRepository.countByUser_UserIdAndUploadedAtBetween(userId, today.start(), today.end());

        if (todayCount >= Photo.MAX_PHOTO_COUNT) {
            throw new PhotoUploadException(PhotoErrorCode.PHOTO_COUNT_EXCEEDED);
        }

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
        TodayRange today = getTodayRange();

        int count = photoRepository.countByUser_UserIdAndUploadedAtBetween(
                userId, today.start(), today.end()
        );

        return PhotoStatusResponse.of(count);
    }

    public List<PhotoUploadResponse> getTodayPhotos(UUID userId) {
        TodayRange today = getTodayRange();

        List<Photo> photos = photoRepository.findByUser_UserIdAndUploadedAtBetween(
                userId, today.start(), today.end()
        );

        return photos.stream()
                .map(PhotoUploadResponse::from)
                .toList();
    }

    private record TodayRange(LocalDateTime start, LocalDateTime end) {}

    private TodayRange getTodayRange() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        return new TodayRange(start, start.plusDays(1).minusNanos(1));
    }

    private void validateImageFormat(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null ||
                (!contentType.equals("image/jpeg") && !contentType.equals("image/png"))) {
            throw new PhotoUploadException(PhotoErrorCode.INVALID_IMAGE_FORMAT);
        }
    }

    private String uploadToS3(MultipartFile file, boolean isPrivacyMode) {
        byte[] imageBytes;
        String contentType = file.getContentType();

        try {
            imageBytes = file.getBytes();

            if (isPrivacyMode) {
                imageBytes = FaceMosaicUtil.applyMosaic(imageBytes, rekognitionClient);
                contentType = "image/jpeg";
            }
        } catch (IOException e) {
            throw new PhotoUploadException(PhotoErrorCode.IMAGE_PROCESSING_FAILED);
        }

        String extension = isPrivacyMode ? "jpg" : extractExtension(file.getOriginalFilename());
        String fileName = UUID.randomUUID() + "." + extension;

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .contentType(contentType)
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromBytes(imageBytes));
        } catch (Exception firstAttemptException) {
            try {
                s3Client.putObject(request, RequestBody.fromBytes(imageBytes));
            } catch (Exception secondAttemptException) {
                throw new PhotoUploadException(PhotoErrorCode.S3_UPLOAD_FAILED);
            }
        }

        return String.format("https://%s.s3.%s.amazonaws.com/%s",
                bucketName, "ap-northeast-2", fileName);
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "jpg";
        }
        String ext = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        if (ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png")) {
            return ext;
        }
        return "jpg";
    }
}