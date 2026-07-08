package com.myongjithon.syncday.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * AI 서비스(ai-service, Gemini 기반 FastAPI) 호출용 RestClient 설정.
 * .env에 AI_SERVICE_URL=http://<ai-service-host>:8000 형태로 추가해야 한다.
 *
 * 사진 여러 장을 base64로 보내면 요청 본문이 수십 MB까지 커질 수 있는데,
 * 기본 요청 팩토리(JDK HttpClient, chunked transfer)가 이 정도 크기에서
 * 본문을 제대로 못 보내는 문제가 있었다. SimpleClientHttpRequestFactory는
 * 본문을 통째로 버퍼링해 Content-Length를 명시적으로 계산해서 보내기 때문에 더 안전하다.
 */
@Configuration
public class AiServiceConfig {

    @Value("${AI_SERVICE_URL}")
    private String aiServiceUrl;

    @Bean
    public RestClient aiServiceRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(60_000); // Gemini 호출이 포함되어 응답이 늦어질 수 있음
        return RestClient.builder()
                .baseUrl(aiServiceUrl)
                .requestFactory(factory)
                .build();
    }
}
