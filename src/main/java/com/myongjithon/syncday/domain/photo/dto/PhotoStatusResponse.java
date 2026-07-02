package com.myongjithon.syncday.domain.photo.dto;

public class PhotoStatusResponse {
    private int uploadedCount;     // 예: 3
    private int requiredCount;     // 고정값 3
    private boolean readyForAnalysis;  // uploadedCount >= 3
}