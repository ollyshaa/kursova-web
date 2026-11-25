package com.ecoportal.dto.coordinator;

import java.time.LocalDate;
import java.util.List;

/**
 * syntheticData = true: звіт побудований на демонстраційних (синтетичних) даних.
 */
public record PeriodReportResponse(
        LocalDate from,
        LocalDate to,
        List<CityReportRow> cities,
        ReportTotals totals,
        boolean syntheticData) {
}
