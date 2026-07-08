package com.myongjithon.syncday.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum AnalysisErrorCode {

    PHOTO_COUNT_INSUFFICIENT(HttpStatus.BAD_REQUEST, "오늘 업로드한 사진이 3장 미만이라 분석할 수 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
    AI_SERVICE_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "AI 분석 서버와 통신에 실패했습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String message;

    AnalysisErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
