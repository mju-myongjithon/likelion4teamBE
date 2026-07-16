package com.myongjithon.syncday.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum GlobalErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.");

    private final HttpStatus status;
    private final String message;

    GlobalErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
