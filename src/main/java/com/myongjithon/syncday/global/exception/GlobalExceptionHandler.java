package com.myongjithon.syncday.global.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(PhotoUploadException.class)
    public ResponseEntity<?> handlePhotoUpload(PhotoUploadException e) {
        return null;
    }
}