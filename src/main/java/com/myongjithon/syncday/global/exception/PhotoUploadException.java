package com.myongjithon.syncday.global.exception;

import lombok.Getter;

@Getter
public class PhotoUploadException extends RuntimeException {

    private final PhotoErrorCode errorCode;

    public PhotoUploadException(PhotoErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}