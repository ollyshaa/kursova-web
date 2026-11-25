package com.ecoportal.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public BadRequestException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }

    public BadRequestException(String code, String message, Map<String, String> fieldErrors) {
        super(HttpStatus.BAD_REQUEST, code, message, fieldErrors);
    }

    /** Помилка валідації з переліком полів: {status, code = VALIDATION_ERROR, message, fieldErrors}. */
    public static BadRequestException validation(Map<String, String> fieldErrors) {
        return new BadRequestException("VALIDATION_ERROR", "Помилка валідації даних", fieldErrors);
    }
}
