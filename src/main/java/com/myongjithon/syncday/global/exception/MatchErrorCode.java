package com.myongjithon.syncday.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum MatchErrorCode {

    ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "아직 오늘의 분석이 완료되지 않았습니다."),
    NO_MATCH_CANDIDATE(HttpStatus.NOT_FOUND, "오늘 매칭 가능한 반대 캠퍼스 상대가 아직 없습니다."),
    SCORE_SERIALIZATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "매칭 점수 처리 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    MatchErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
