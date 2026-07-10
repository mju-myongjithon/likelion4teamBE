package com.myongjithon.syncday.domain.analysis.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * FE -> BE, "오늘의 나를 분석하기" 요청 바디.
 */
@Getter
@NoArgsConstructor
public class AnalyzeRequest {

    @NotNull
    private UUID userId;
}
