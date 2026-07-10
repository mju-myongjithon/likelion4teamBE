package com.myongjithon.syncday.domain.analysis;

import com.myongjithon.syncday.domain.analysis.dto.AiFeatureRequest;
import com.myongjithon.syncday.domain.analysis.dto.AiFeatureResponse;
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
 * ai-service의 F2(POST /api/v1/features)를 호출한다.
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
}
