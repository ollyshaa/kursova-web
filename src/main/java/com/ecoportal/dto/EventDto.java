package com.ecoportal.dto;

import java.time.Instant;
import java.util.UUID;

public record EventDto(
        UUID id,
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
        int freeSpots,
        InitiativeShortDto initiative) {
}
