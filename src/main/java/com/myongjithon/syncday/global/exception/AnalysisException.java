package com.myongjithon.syncday.global.exception;

import lombok.Getter;

@Getter
public class AnalysisException extends RuntimeException {

    private final AnalysisErrorCode errorCode;

    public AnalysisException(AnalysisErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
