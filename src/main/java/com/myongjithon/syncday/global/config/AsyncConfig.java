package com.myongjithon.syncday.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** SeedAutoResponder(@Async 시드 자동 응답)의 비동기 실행 활성화. */
@Configuration
@EnableAsync
public class AsyncConfig {
}