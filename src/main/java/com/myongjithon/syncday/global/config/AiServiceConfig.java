package com.myongjithon.syncday.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * AI 서비스(ai-service, Gemini 기반 FastAPI) 호출용 RestClient 설정.
 * .env에 AI_SERVICE_URL=http://<ai-service-host>:8000 형태로 추가해야 한다.
 */
@Configuration
public class AiServiceConfig {

    @Value("${AI_SERVICE_URL}")
    private String aiServiceUrl;

    @Bean
    public RestClient aiServiceRestClient() {
        return RestClient.builder()
                .baseUrl(aiServiceUrl)
                .build();
    }
}
