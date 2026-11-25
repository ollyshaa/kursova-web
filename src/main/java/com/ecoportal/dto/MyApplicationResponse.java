package com.ecoportal.dto;

import com.ecoportal.domain.ApplicationStatus;
import com.ecoportal.domain.EventStatus;

import java.time.Instant;
import java.util.UUID;

public record MyApplicationResponse(
        UUID id,
        UUID eventId,
        String eventTitle,
        String city,
        Instant startsAt,
        EventStatus eventStatus,
        ApplicationStatus status,
        String comment,
        Instant createdAt,
        Instant updatedAt,
        boolean canCancel) {
}
