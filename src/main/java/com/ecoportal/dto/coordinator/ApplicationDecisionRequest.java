package com.ecoportal.dto.coordinator;

import com.ecoportal.domain.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

public record ApplicationDecisionRequest(
        @NotNull(message = "Рішення є обов'язковим")
        ApplicationStatus decision) {
}
