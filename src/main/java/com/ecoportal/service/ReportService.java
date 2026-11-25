package com.ecoportal.service;

import com.ecoportal.domain.EventSummary;
import com.ecoportal.dto.coordinator.CityReportRow;
import com.ecoportal.dto.coordinator.PeriodReportResponse;
import com.ecoportal.dto.coordinator.ReportTotals;
import com.ecoportal.exception.BadRequestException;
import com.ecoportal.repository.EventSummaryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ReportService {

    /** Календарні дати періоду інтерпретуються за київським часом. */
    private static final ZoneId ZONE = ZoneId.of("Europe/Kyiv");

    private final CoordinatorAccess access;
    private final EventSummaryRepository summaryRepository;

    public ReportService(CoordinatorAccess access, EventSummaryRepository summaryRepository) {
        this.access = access;
        this.summaryRepository = summaryRepository;
    }

    /**
     * Звіт по завершених власних подіях, що закінчилися в межах [from, to] (обидві дати включно),
     * з групуванням за населеним пунктом.
     */
    public PeriodReportResponse getPeriodReport(UUID currentUserId, LocalDate from, LocalDate to) {
        access.requireCoordinator(currentUserId);
        if (from.isAfter(to)) {
            throw new BadRequestException("INVALID_DATE_RANGE",
                    "Дата початку періоду не може бути пізнішою за дату завершення");
        }
        Instant periodStart = from.atStartOfDay(ZONE).toInstant();
        Instant periodEnd = to.plusDays(1).atStartOfDay(ZONE).toInstant();

        List<EventSummary> summaries =
                summaryRepository.findForReport(currentUserId, periodStart, periodEnd, Instant.now());

        Map<String, Accumulator> byCity = new LinkedHashMap<>();
        Accumulator total = new Accumulator();
        for (EventSummary s : summaries) {
            byCity.computeIfAbsent(s.getEvent().getCity(), k -> new Accumulator()).add(s);
            total.add(s);
        }

        List<CityReportRow> rows = byCity.entrySet().stream()
                .map(e -> new CityReportRow(e.getKey(), e.getValue().events, e.getValue().participants,
                        e.getValue().waste(), e.getValue().trees, e.getValue().hours()))
                .toList();
        ReportTotals totals = new ReportTotals(total.events, total.participants, total.waste(),
                total.trees, total.hours());
        return new PeriodReportResponse(from, to, rows, totals, true);
    }

    private static final class Accumulator {
        private long events;
        private long participants;
        private long trees;
        private BigDecimal waste = BigDecimal.ZERO;
        private BigDecimal hours = BigDecimal.ZERO;

        void add(EventSummary s) {
            events++;
            participants += s.getActualParticipants();
            trees += s.getTreesPlanted();
            waste = waste.add(s.getWasteKg());
            hours = hours.add(s.getHoursSpent());
        }

        BigDecimal waste() {
            return waste.setScale(2, java.math.RoundingMode.HALF_UP);
        }

        BigDecimal hours() {
            return hours.setScale(2, java.math.RoundingMode.HALF_UP);
        }
    }
}
