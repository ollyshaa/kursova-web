package com.ecoportal.dto.coordinator;

import com.ecoportal.domain.Application;
import com.ecoportal.domain.ApplicationStatus;

import java.time.Instant;
import java.util.UUID;

public record EventApplicationResponse(
        UUID id,
        UUID applicantId,
        String applicantName,
        String applicantEmail,
        String comment,
        ApplicationStatus status,
        Instant createdAt,
        Instant decidedAt) {

    public static EventApplicationResponse from(Application a) {
        return new EventApplicationResponse(
                a.getId(), a.getUser().getId(), a.getUser().getFullName(), a.getUser().getEmail(),
                a.getComment(), a.getStatus(), a.getCreatedAt(), a.getDecidedAt());
    }
}
