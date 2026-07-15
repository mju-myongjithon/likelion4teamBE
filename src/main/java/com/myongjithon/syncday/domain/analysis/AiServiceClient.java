package com.myongjithon.syncday.domain.analysis;

import com.myongjithon.syncday.domain.analysis.dto.AiDescriptionRequest;
import com.myongjithon.syncday.domain.analysis.dto.AiDescriptionResponse;
import com.myongjithon.syncday.domain.analysis.dto.AiFeatureRequest;
import com.myongjithon.syncday.domain.analysis.dto.AiFeatureResponse;
import com.myongjithon.syncday.domain.analysis.dto.FeaturesDto;
import com.myongjithon.syncday.global.exception.AnalysisErrorCode;
import com.myongjithon.syncday.global.exception.AnalysisException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * ai-service 호출 클라이언트.
 * F2(POST /api/v1/features) — 사진 특징 추출.
 * F4(POST /api/v1/description) — 매칭 두 유저의 유사도 코멘트 생성.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiServiceClient {

    private final RestClient aiServiceRestClient;

    public AiFeatureResponse extractFeatures(UUID userId, LocalDate date, List<String> imageUrls) {
        AiFeatureRequest request = AiFeatureRequest.builder()
                .userId(userId.toString())
                .date(date.toString())
                .imageUrls(imageUrls)
                .build();

        try {
            return aiServiceRestClient.post()
                    .uri("/api/v1/features")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiFeatureResponse.class);
        } catch (RestClientException e) {
            log.error("ai-service 호출 실패", e);
            throw new AnalysisException(AnalysisErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    /**
     * F4: 매칭된 두 유저의 하루 특징 + 유사도 점수를 보내 유사도 코멘트를 생성한다.
     * (FeaturesDto 는 ai-service의 DayFeatures 와 같은 형태라 그대로 직렬화한다.)
     */
    public String generateDescription(FeaturesDto userA, FeaturesDto userB, int similarityScore) {
        AiDescriptionRequest request = new AiDescriptionRequest(similarityScore, userA, userB);

        try {
            AiDescriptionResponse response = aiServiceRestClient.post()
                    .uri("/api/v1/description")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiDescriptionResponse.class);
            return response == null ? null : response.description();
        } catch (RestClientException e) {
            log.error("ai-service 설명(F4) 호출 실패", e);
            throw new AnalysisException(AnalysisErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }
}
