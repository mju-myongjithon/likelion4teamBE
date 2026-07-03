package com.myongjithon.syncday.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum PhotoErrorCode {

    PHOTO_COUNT_INSUFFICIENT(HttpStatus.BAD_REQUEST, "최소 3장 이상 업로드해야 합니다."),
    INVALID_IMAGE_FORMAT(HttpStatus.BAD_REQUEST, "지원하지 않는 이미지 형식입니다."),
    S3_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 업로드에 실패했습니다. 다시 시도해주세요."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),;

    private final HttpStatus status;
    private final String message;

    PhotoErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}