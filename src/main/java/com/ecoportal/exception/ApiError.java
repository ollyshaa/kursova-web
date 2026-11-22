package com.ecoportal.exception;

import java.util.Map;

/**
 * Єдиний формат помилок API: {status, code, message, fieldErrors}.
 */
public record ApiError(int status, String code, String message, Map<String, String> fieldErrors) {

    public static ApiError of(int status, String code, String message) {
        return new ApiError(status, code, message, Map.of());
    }
}
