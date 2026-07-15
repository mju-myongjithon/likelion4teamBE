package com.myongjithon.syncday.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum MatchErrorCode {

    ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "아직 오늘의 분석이 완료되지 않았습니다."),
    MATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "아직 성사된 매칭이 없습니다."),
    SCORE_SERIALIZATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "매칭 점수 처리 중 오류가 발생했습니다."),
    FEATURES_DESERIALIZATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "분석 결과를 읽는 중 오류가 발생했습니다."),
    NOT_MATCH_PARTICIPANT(HttpStatus.FORBIDDEN, "해당 매칭의 참여자가 아닙니다."),
    MATCH_NOT_CONNECTED(HttpStatus.FORBIDDEN, "양쪽이 채팅 참여를 수락해야 이용할 수 있습니다.");

    private final HttpStatus status;
    private final String message;

    MatchErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
