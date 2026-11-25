package com.ecoportal.dto.coordinator;

import com.ecoportal.domain.EventSummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EventSummaryResponse(
        UUID eventId,
        int actualParticipants,
        BigDecimal wasteKg,
        int treesPlanted,
        BigDecimal hoursSpent,
        String note,
        Instant updatedAt) {

    public static EventSummaryResponse from(EventSummary s) {
        return new EventSummaryResponse(s.getEvent().getId(), s.getActualParticipants(), s.getWasteKg(),
                s.getTreesPlanted(), s.getHoursSpent(), s.getNote(), s.getUpdatedAt());
    }
}
