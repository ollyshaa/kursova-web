package com.ecoportal.dto.coordinator;

import java.math.BigDecimal;

public record ReportTotals(
        long eventsCount,
        long totalParticipants,
        BigDecimal totalWasteKg,
        long totalTreesPlanted,
        BigDecimal totalHoursSpent) {
}
