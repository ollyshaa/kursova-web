package com.ecoportal.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Email є обов'язковим")
        String email,

        @NotBlank(message = "Пароль є обов'язковим")
        String password) {
}
