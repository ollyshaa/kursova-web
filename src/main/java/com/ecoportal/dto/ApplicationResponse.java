package com.ecoportal.dto;

import com.ecoportal.domain.Application;
import com.ecoportal.domain.ApplicationStatus;

import java.time.Instant;
import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        UUID eventId,
        ApplicationStatus status,
        String comment,
        Instant createdAt,
        Instant updatedAt) {

    public static ApplicationResponse from(Application a) {
        return new ApplicationResponse(
                a.getId(), a.getEvent().getId(), a.getStatus(), a.getComment(),
                a.getCreatedAt(), a.getUpdatedAt());
    }
}
