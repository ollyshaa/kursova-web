package com.ecoportal.controller;

import com.ecoportal.dto.coordinator.ApplicationDecisionRequest;
import com.ecoportal.dto.coordinator.CoordinatorEventResponse;
import com.ecoportal.dto.coordinator.EventApplicationResponse;
import com.ecoportal.dto.coordinator.EventStatusRequest;
import com.ecoportal.dto.coordinator.EventSummaryRequest;
import com.ecoportal.dto.coordinator.EventSummaryResponse;
import com.ecoportal.dto.coordinator.EventUpsertRequest;
import com.ecoportal.dto.coordinator.InitiativeCreateRequest;
import com.ecoportal.dto.coordinator.InitiativeResponse;
import com.ecoportal.dto.coordinator.PeriodReportResponse;
import com.ecoportal.security.AuthenticatedUser;
import com.ecoportal.service.EventManagementService;
import com.ecoportal.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Кабінет координатора. Роль COORDINATOR та належність об'єктів перевіряють сервіси
 * (ідентифікатор користувача береться лише із сесії, правило СП-01).
 */
@RestController
@RequestMapping("/api/coordinator")
public class CoordinatorController {

    private final EventManagementService eventManagementService;
    private final ReportService reportService;

    public CoordinatorController(EventManagementService eventManagementService, ReportService reportService) {
        this.eventManagementService = eventManagementService;
        this.reportService = reportService;
    }

    @GetMapping("/initiatives")
    public List<InitiativeResponse> myInitiatives(@AuthenticationPrincipal AuthenticatedUser principal) {
        return eventManagementService.getMyInitiatives(principal.id());
    }

    @PostMapping("/initiatives")
    public ResponseEntity<InitiativeResponse> createInitiative(
            @Valid @RequestBody InitiativeCreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(eventManagementService.createInitiative(principal.id(), request));
    }

    @PostMapping("/events")
    public ResponseEntity<CoordinatorEventResponse> createEvent(
            @Valid @RequestBody EventUpsertRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(eventManagementService.createEvent(principal.id(), request));
    }

    @PutMapping("/events/{id}")
    public CoordinatorEventResponse updateEvent(@PathVariable("id") UUID eventId,
                                                @Valid @RequestBody EventUpsertRequest request,
                                                @AuthenticationPrincipal AuthenticatedUser principal) {
        return eventManagementService.updateEvent(principal.id(), eventId, request);
    }

    @PatchMapping("/events/{id}/status")
    public CoordinatorEventResponse changeStatus(@PathVariable("id") UUID eventId,
                                                 @Valid @RequestBody EventStatusRequest request,
                                                 @AuthenticationPrincipal AuthenticatedUser principal) {
        return eventManagementService.changeEventStatus(principal.id(), eventId, request.status());
    }

    @GetMapping("/events/{id}/applications")
    public List<EventApplicationResponse> eventApplications(@PathVariable("id") UUID eventId,
                                                            @AuthenticationPrincipal AuthenticatedUser principal) {
        return eventManagementService.getEventApplications(principal.id(), eventId);
    }

    @PatchMapping("/applications/{id}")
    public EventApplicationResponse decide(@PathVariable("id") UUID applicationId,
                                           @Valid @RequestBody ApplicationDecisionRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser principal) {
        return eventManagementService.decideApplication(principal.id(), applicationId, request);
    }

    @PutMapping("/events/{id}/summary")
    public EventSummaryResponse upsertSummary(@PathVariable("id") UUID eventId,
                                              @Valid @RequestBody EventSummaryRequest request,
                                              @AuthenticationPrincipal AuthenticatedUser principal) {
        return eventManagementService.upsertEventSummary(principal.id(), eventId, request);
    }

    @GetMapping("/reports")
    public PeriodReportResponse report(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return reportService.getPeriodReport(principal.id(), from, to);
    }
}
