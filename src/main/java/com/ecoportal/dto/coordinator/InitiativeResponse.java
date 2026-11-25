package com.ecoportal.dto.coordinator;

import com.ecoportal.domain.Initiative;
import com.ecoportal.domain.InitiativeStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InitiativeResponse(
        UUID id,
        String title,
        String description,
        String category,
        InitiativeStatus status,
        Instant createdAt,
        List<CoordinatorEventResponse> events) {

    public static InitiativeResponse from(Initiative i, List<CoordinatorEventResponse> events) {
        return new InitiativeResponse(i.getId(), i.getTitle(), i.getDescription(), i.getCategory(),
                i.getStatus(), i.getCreatedAt(), events);
    }
}
