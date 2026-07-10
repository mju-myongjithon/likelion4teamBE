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

/**
 * F1(사진 업로드)과 F2(AI 특징 추출, ai-service)를 잇는 오케스트레이션 서비스.
 * "오늘의 나를 분석하기" 버튼이 호출하는 지점.
 */
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

        // 이미 오늘 분석한 기록이 있으면 새로 만들지 않고 기존 결과를 그대로 돌려준다.
        // (버튼을 두 번 누르거나 네트워크 지연으로 중복 호출돼도 중복 레코드가 안 생기게)
        Optional<AnalysisResult> existing = analysisResultRepository.findByUser_UserIdAndAnalysisDate(userId, today);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new AnalysisException(AnalysisErrorCode.USER_NOT_FOUND));

        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1).minusNanos(1);

        List<Photo> todayPhotos = photoRepository.findByUser_UserIdAndUploadedAtBetween(
                userId, startOfDay, endOfDay
        );

        if (todayPhotos.size() < Photo.REQUIRED_PHOTO_COUNT) {
            throw new AnalysisException(AnalysisErrorCode.PHOTO_COUNT_INSUFFICIENT);
        }

        // syncday-photos 버킷이 private라 URL만 넘기면 ai-service가 다운로드를 못 한다.
        // BE가 대신 S3에서 읽어와 base64로 변환해서 사진 내용물 자체를 넘긴다.
        List<String> imageDataUris = todayPhotos.stream()
                .map(Photo::getImageUrl)
                .map(s3ImageFetcher::toDataUri)
                .toList();

        AiFeatureResponse aiResponse = aiServiceClient.extractFeatures(userId, today, imageDataUris);

        AnalysisResult analysisResult = AnalysisResult.builder()
                .user(user)
                .analysisDate(today)
                .featuresJson(toJson(aiResponse))
                .build();
        AnalysisResult saved = analysisResultRepository.save(analysisResult);

        List<UUID> photoIds = todayPhotos.stream().map(Photo::getPhotoId).toList();
        photoRepository.linkAnalysis(saved.getAnalysisId(), photoIds);

        return AnalyzeResponse.of(saved.getAnalysisId(), aiResponse.getFeatures());
    }

    /** 오늘 이미 분석한 결과만 조회한다 (새로 분석하지 않음). 화면 재진입 시 사용. */
    public AnalyzeResponse getTodayAnalysis(UUID userId) {
        AnalysisResult result = analysisResultRepository
                .findByUser_UserIdAndAnalysisDate(userId, LocalDate.now())
                .orElseThrow(() -> new AnalysisException(AnalysisErrorCode.ANALYSIS_NOT_FOUND));
        return toResponse(result);
    }

    private AnalyzeResponse toResponse(AnalysisResult result) {
        try {
            FeaturesDto features = objectMapper.readValue(result.getFeaturesJson(), FeaturesDto.class);
            return AnalyzeResponse.of(result.getAnalysisId(), features);
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
