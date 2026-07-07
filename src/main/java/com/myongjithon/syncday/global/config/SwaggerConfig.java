package com.myongjithon.syncday.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI syncdayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sync.day API")
                        .description("명지톤 2026 | AI 기반 대학 간 연결 플랫폼 백엔드 API")
                        .version("v1.0.0"));
    }
}