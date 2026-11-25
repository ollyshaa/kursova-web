package com.ecoportal.dto.coordinator;

import com.ecoportal.domain.InitiativeStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * status необов'язковий: за замовчуванням ініціатива створюється опублікованою (PUBLISHED).
 */
public record InitiativeCreateRequest(
        @NotBlank(message = "Назва є обов'язковою")
        @Size(max = 200, message = "Назва не може перевищувати 200 символів")
        String title,

        @NotBlank(message = "Опис є обов'язковим")
        @Size(max = 5000, message = "Опис не може перевищувати 5000 символів")
        String description,

        @NotBlank(message = "Категорія є обов'язковою")
        @Size(max = 100, message = "Категорія не може перевищувати 100 символів")
        String category,

        InitiativeStatus status) {
}
