package com.ecoportal.dto.coordinator;

import com.ecoportal.domain.Event;
import com.ecoportal.domain.EventStatus;

import java.time.Instant;
import java.util.UUID;

public record CoordinatorEventResponse(
        UUID id,
        UUID initiativeId,
        String title,
        String description,
        String city,
        String addressText,
        Double latitude,
        Double longitude,
        Instant startsAt,
        Instant endsAt,
        Instant registrationClosesAt,
        int participantsLimit,
        EventStatus status,
        long activeApplications,
        Instant createdAt) {

    public static CoordinatorEventResponse from(Event e, long activeApplications) {
        return new CoordinatorEventResponse(
                e.getId(), e.getInitiative().getId(), e.getTitle(), e.getDescription(), e.getCity(),
                e.getAddressText(), e.getLatitude(), e.getLongitude(), e.getStartsAt(), e.getEndsAt(),
                e.getRegistrationClosesAt(), e.getParticipantsLimit(), e.getStatus(),
                activeApplications, e.getCreatedAt());
    }
}
