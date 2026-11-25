package com.ecoportal.dto.coordinator;

import com.ecoportal.domain.EventStatus;
import jakarta.validation.constraints.NotNull;

public record EventStatusRequest(
        @NotNull(message = "Статус є обов'язковим")
        EventStatus status) {
}
