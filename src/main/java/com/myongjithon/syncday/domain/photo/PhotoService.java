package com.myongjithon.syncday.domain.photo;

import com.myongjithon.syncday.domain.analysis.AnalysisResultRepository;
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
import software.amazon.awssdk.services.rekognition.model.RekognitionException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final AppUserRepository appUserRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final S3Client s3Client;
    private final RekognitionClient rekognitionClient;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Transactional
    public PhotoUploadResponse uploadPhoto(UUID userId, MultipartFile file, boolean isPrivacyMode) {
        validateImageFormat(file);

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new PhotoUploadException(PhotoErrorCode.USER_NOT_FOUND));

        // 오늘 이미 분석(F2)까지 끝냈다면 더 이상 업로드를 받지 않는다. 재분석은 기존 결과를
        // 그대로 돌려줄 뿐이라(AnalysisService.analyzeToday), 이 시점 이후의 업로드는 새로 올린
        // 사진이 어디에도 반영되지 않는 채로 프라이버시 모드면 Rekognition 비용만 또 나가는
        // 조용한 무의미 재시도가 된다 — 그래서 아예 업로드 단계에서 막는다.
        if (analysisResultRepository.existsByUser_UserIdAndAnalysisDate(userId, LocalDate.now())) {
            throw new PhotoUploadException(PhotoErrorCode.ALREADY_ANALYZED_TODAY);
        }

        TodayRange today = getTodayRange();
        int todayCount = photoRepository.countByUser_UserIdAndUploadedAtBetween(userId, today.start(), today.end());
        if (todayCount >= Photo.MAX_PHOTO_COUNT) {
            throw new PhotoUploadException(PhotoErrorCode.PHOTO_COUNT_EXCEEDED);
        }

        String s3Key = uploadToS3(userId, file, isPrivacyMode);
        String presignedUrl = generatePresignedUrl(s3Key);

        Photo photo = Photo.builder()
                .user(user)
                .s3Key(s3Key)
                .isPrivacyMode(isPrivacyMode)
                .build();

        Photo savedPhoto = photoRepository.save(photo);

        return PhotoUploadResponse.from(savedPhoto, presignedUrl);
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

        List<Photo> photos = photoRepository.findByUser_UserIdAndUploadedAtBetweenOrderByUploadedAtAsc(
                userId, today.start(), today.end()
        );

        return photos.stream()
                .map(photo -> PhotoUploadResponse.from(photo, generatePresignedUrl(photo.getS3Key())))
                .toList();
    }

    /** 특정 유저의 오늘 사진 presigned URL 목록. 매칭 상대 사진 노출(F3)에서 재사용한다. */
    public List<String> getTodayPhotoUrls(UUID userId) {
        return getTodayPhotos(userId).stream()
                .map(PhotoUploadResponse::getImageUrl)
                .toList();
    }

    private record TodayRange(LocalDateTime start, LocalDateTime end) {
    }

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

    private String uploadToS3(UUID userId, MultipartFile file, boolean isPrivacyMode) {
        byte[] imageBytes;
        String contentType = file.getContentType();

        try {
            imageBytes = file.getBytes();

            // 방향 보정은 모자이크 여부와 무관하게 항상 먼저 적용
            imageBytes = FaceMosaicUtil.correctOrientationOnly(imageBytes);

            if (isPrivacyMode) {
                imageBytes = FaceMosaicUtil.applyMosaic(imageBytes, rekognitionClient);
            }

            imageBytes = resizeAndCompress(imageBytes);
            contentType = "image/jpeg";

        } catch (IOException e) {
            throw new PhotoUploadException(PhotoErrorCode.IMAGE_PROCESSING_FAILED);
        } catch (RekognitionException e) {
            // 리사이즈 전 원본을 그대로 Rekognition에 보내다 보니, 5MB 제한을 넘는
            // 고해상도 원본에서 종종 터진다(InvalidImageFormatException 등). 잡지 않으면
            // 처리되지 않은 500으로 그대로 나가버려서 여기서 도메인 예외로 변환한다.
            throw new PhotoUploadException(PhotoErrorCode.IMAGE_PROCESSING_FAILED);
        }

        String extension = isPrivacyMode ? "jpg" : extractExtension(file.getOriginalFilename());
        String fileName = userId + "/" + UUID.randomUUID() + "." + extension;

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

        return fileName;
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

    private String generatePresignedUrl(String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(30))  // 30분간 유효
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    private byte[] resizeAndCompress(byte[] imageBytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));

        if (image == null) {
            throw new PhotoUploadException(PhotoErrorCode.IMAGE_PROCESSING_FAILED);
        }

        int maxWidth = 1600;
        if (image.getWidth() > maxWidth) {
            double ratio = (double) maxWidth / image.getWidth();
            int newWidth = maxWidth;
            int newHeight = (int) (image.getHeight() * ratio);

            BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, newWidth, newHeight, null);
            g.dispose();
            image = resized;
        }

        // JPEG 압축 품질 85%로 저장
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.85f);

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        }
        writer.dispose();

        return output.toByteArray();
    }

    @Transactional
    public void resetTodayPhotos(UUID userId) {
        TodayRange today = getTodayRange();
        List<Photo> todayPhotos = photoRepository.findByUser_UserIdAndUploadedAtBetweenOrderByUploadedAtAsc(userId, today.start(), today.end());

        for (Photo photo : todayPhotos) {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(photo.getS3Key())
                    .build());
        }

        photoRepository.deleteAll(todayPhotos);
    }

}