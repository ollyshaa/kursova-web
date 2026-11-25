package com.ecoportal.dto.coordinator;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Запит на створення та оновлення події. initiativeId обов'язковий лише при створенні
 * (перевіряється в сервісі), під час оновлення ігнорується: подія не переходить між ініціативами.
 * Перевірки дат та ліміту виконує EventManagementService і повертає їх у fieldErrors.
 */
public record EventUpsertRequest(
        UUID initiativeId,

        @NotBlank(message = "Назва є обов'язковою")
        @Size(max = 200, message = "Назва не може перевищувати 200 символів")
        String title,

        @NotBlank(message = "Опис є обов'язковим")
        @Size(max = 5000, message = "Опис не може перевищувати 5000 символів")
        String description,

        @NotBlank(message = "Населений пункт є обов'язковим")
        @Size(max = 100, message = "Назва населеного пункту не може перевищувати 100 символів")
        String city,

        @NotBlank(message = "Адреса є обов'язковою")
        @Size(max = 300, message = "Адреса не може перевищувати 300 символів")
        String addressText,

        Double latitude,
        Double longitude,

        @NotNull(message = "Дата початку є обов'язковою")
        Instant startsAt,

        @NotNull(message = "Дата завершення є обов'язковою")
        Instant endsAt,

        @NotNull(message = "Дата закриття реєстрації є обов'язковою")
        Instant registrationClosesAt,

        @NotNull(message = "Ліміт учасників є обов'язковим")
        Integer participantsLimit) {
}
