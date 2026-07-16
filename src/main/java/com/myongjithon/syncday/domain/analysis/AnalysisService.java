package com.myongjithon.syncday.domain.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myongjithon.syncday.domain.analysis.dto.AiFeatureResponse;
import com.myongjithon.syncday.domain.analysis.dto.AnalyzeResponse;
import com.myongjithon.syncday.domain.analysis.dto.FeaturesDto;
import com.myongjithon.syncday.domain.photo.Photo;
import com.myongjithon.syncday.domain.photo.PhotoRepository;
import com.myongjithon.syncday.domain.user.AppUser;
import com.myongjithon.syncday.domain.user.AppUserRepository;
import com.myongjithon.syncday.global.exception.AnalysisErrorCode;
import com.myongjithon.syncday.global.exception.AnalysisException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final PhotoRepository photoRepository;
    private final AppUserRepository appUserRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AiServiceClient aiServiceClient;
    private final S3ImageFetcher s3ImageFetcher;
    private final ObjectMapper objectMapper;

    @Transactional
    public AnalyzeResponse analyzeToday(UUID userId) {
        LocalDate today = LocalDate.now();

        Optional<AnalysisResult> existing = analysisResultRepository.findByUser_UserIdAndAnalysisDate(userId, today);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new AnalysisException(AnalysisErrorCode.USER_NOT_FOUND));

        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1).minusNanos(1);

        List<Photo> todayPhotos = photoRepository.findByUser_UserIdAndUploadedAtBetweenOrderByUploadedAtAsc(
                userId, startOfDay, endOfDay
        );

        if (todayPhotos.size() < Photo.REQUIRED_PHOTO_COUNT) {
            throw new AnalysisException(AnalysisErrorCode.PHOTO_COUNT_INSUFFICIENT);
        }

        List<String> imageDataUris = todayPhotos.stream()
                .map(Photo::getS3Key)
                .map(s3ImageFetcher::toDataUri)
                .toList();

        AiFeatureResponse aiResponse = aiServiceClient.extractFeatures(userId, today, imageDataUris);

        AnalysisResult analysisResult = AnalysisResult.builder()
                .user(user)
                .analysisDate(today)
                .featuresJson(toJson(aiResponse))
                .build();
        AnalysisResult saved = analysisResultRepository.saveAndFlush(analysisResult);

        user.updateStreak(today);

        List<UUID> photoIds = todayPhotos.stream().map(Photo::getPhotoId).toList();
        photoRepository.linkAnalysis(saved.getAnalysisId(), photoIds);

        return AnalyzeResponse.of(saved.getAnalysisId(), aiResponse.getFeatures(), user.getCurrentStreak());
    }

    @Transactional(readOnly = true)
    public AnalyzeResponse getTodayAnalysis(UUID userId) {
        AnalysisResult result = analysisResultRepository
                .findByUser_UserIdAndAnalysisDate(userId, LocalDate.now())
                .orElseThrow(() -> new AnalysisException(AnalysisErrorCode.ANALYSIS_NOT_FOUND));
        return toResponse(result);
    }

    private AnalyzeResponse toResponse(AnalysisResult result) {
        try {
            FeaturesDto features = objectMapper.readValue(result.getFeaturesJson(), FeaturesDto.class);
            return AnalyzeResponse.of(result.getAnalysisId(), features, result.getUser().getCurrentStreak());
        } catch (JsonProcessingException e) {
            log.error("features 역직렬화 실패", e);
            throw new AnalysisException(AnalysisErrorCode.FEATURE_SERIALIZE_FAILED);
        }
    }

    private String toJson(AiFeatureResponse aiResponse) {
        try {
            return objectMapper.writeValueAsString(aiResponse.getFeatures());
        } catch (JsonProcessingException e) {
            log.error("features 직렬화 실패", e);
            throw new AnalysisException(AnalysisErrorCode.FEATURE_SERIALIZE_FAILED);
        }
    }
}
