package com.myongjithon.syncday.global.exception;

import lombok.Getter;

@Getter
public class MatchException extends RuntimeException {

    private final MatchErrorCode errorCode;

    public MatchException(MatchErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
