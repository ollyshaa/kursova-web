package com.ecoportal.service;

import com.ecoportal.domain.Application;
import com.ecoportal.domain.ApplicationStatus;
import com.ecoportal.domain.Event;
import com.ecoportal.domain.EventStatus;
import com.ecoportal.domain.EventSummary;
import com.ecoportal.domain.Initiative;
import com.ecoportal.domain.InitiativeStatus;
import com.ecoportal.domain.User;
import com.ecoportal.dto.coordinator.ApplicationDecisionRequest;
import com.ecoportal.dto.coordinator.CoordinatorEventResponse;
import com.ecoportal.dto.coordinator.EventApplicationResponse;
import com.ecoportal.dto.coordinator.EventSummaryRequest;
import com.ecoportal.dto.coordinator.EventSummaryResponse;
import com.ecoportal.dto.coordinator.EventUpsertRequest;
import com.ecoportal.dto.coordinator.InitiativeCreateRequest;
import com.ecoportal.dto.coordinator.InitiativeResponse;
import com.ecoportal.exception.BadRequestException;
import com.ecoportal.exception.ConflictException;
import com.ecoportal.exception.ForbiddenException;
import com.ecoportal.exception.NotFoundException;
import com.ecoportal.repository.ApplicationRepository;
import com.ecoportal.repository.EventRepository;
import com.ecoportal.repository.EventSummaryRepository;
import com.ecoportal.repository.InitiativeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class EventManagementService {

    private final CoordinatorAccess access;
    private final InitiativeRepository initiativeRepository;
    private final EventRepository eventRepository;
    private final ApplicationRepository applicationRepository;
    private final EventSummaryRepository summaryRepository;

    public EventManagementService(CoordinatorAccess access,
                                  InitiativeRepository initiativeRepository,
                                  EventRepository eventRepository,
                                  ApplicationRepository applicationRepository,
                                  EventSummaryRepository summaryRepository) {
        this.access = access;
        this.initiativeRepository = initiativeRepository;
        this.eventRepository = eventRepository;
        this.applicationRepository = applicationRepository;
        this.summaryRepository = summaryRepository;
    }

    // ---------------------------------------------------------------- Ініціативи

    @Transactional(readOnly = true)
    public List<InitiativeResponse> getMyInitiatives(UUID currentUserId) {
        access.requireCoordinator(currentUserId);
        List<Initiative> initiatives = initiativeRepository.findByCoordinatorIdOrderByCreatedAtDesc(currentUserId);
        if (initiatives.isEmpty()) {
            return List.of();
        }
        List<Event> events = eventRepository.findByInitiativeIdInOrderByStartsAtAsc(
                initiatives.stream().map(Initiative::getId).toList());
        Map<UUID, Long> activeCounts = activeCounts(events);
        Map<UUID, List<CoordinatorEventResponse>> byInitiative = new HashMap<>();
        for (Event e : events) {
            byInitiative.computeIfAbsent(e.getInitiative().getId(), k -> new ArrayList<>())
                    .add(CoordinatorEventResponse.from(e, activeCounts.getOrDefault(e.getId(), 0L)));
        }
        return initiatives.stream()
                .map(i -> InitiativeResponse.from(i, byInitiative.getOrDefault(i.getId(), List.of())))
                .toList();
    }

    public InitiativeResponse createInitiative(UUID currentUserId, InitiativeCreateRequest req) {
        User coordinator = access.requireCoordinator(currentUserId);
        InitiativeStatus status = req.status() == null ? InitiativeStatus.PUBLISHED : req.status();
        Initiative initiative = new Initiative(coordinator, req.title().trim(), req.description().trim(),
                req.category().trim(), status);
        return InitiativeResponse.from(initiativeRepository.saveAndFlush(initiative), List.of());
    }

    // ---------------------------------------------------------------- Події

    public CoordinatorEventResponse createEvent(UUID currentUserId, EventUpsertRequest req) {
        access.requireCoordinator(currentUserId);
        Initiative initiative = req.initiativeId() == null
                ? null
                : loadOwnedInitiative(currentUserId, req.initiativeId());

        Map<String, String> errors = validateEvent(req, null, Instant.now());
        if (initiative == null) {
            errors.put("initiativeId", "Ідентифікатор ініціативи є обов'язковим");
        }
        if (!errors.isEmpty()) {
            throw BadRequestException.validation(errors);
        }

        Event event = new Event();
        event.setInitiative(initiative);
        event.setStatus(EventStatus.DRAFT);
        applyEventFields(event, req);
        Event saved = eventRepository.saveAndFlush(event);
        return CoordinatorEventResponse.from(saved, 0L);
    }

    public CoordinatorEventResponse updateEvent(UUID currentUserId, UUID eventId, EventUpsertRequest req) {
        access.requireCoordinator(currentUserId);
        // Блокування рядка події: ліміт порівнюється з числом активних заявок без гонок із поданням заявок.
        Event event = loadOwnedEvent(currentUserId, eventId, true);

        Map<String, String> errors = validateEvent(req, event.getStartsAt(), Instant.now());
        if (!errors.isEmpty()) {
            throw BadRequestException.validation(errors);
        }

        long active = applicationRepository.countActiveByEventId(eventId);
        if (req.participantsLimit() < active) {
            throw new ConflictException("LIMIT_BELOW_ACTIVE_APPLICATIONS",
                    "Ліміт учасників не може бути меншим за кількість активних заявок (" + active + ")");
        }

        applyEventFields(event, req);
        Event saved = eventRepository.saveAndFlush(event);
        return CoordinatorEventResponse.from(saved, active);
    }

    /**
     * Публікація або скасування події. Статуси наявних заявок при скасуванні не змінюються (FR-07).
     */
    public CoordinatorEventResponse changeEventStatus(UUID currentUserId, UUID eventId, EventStatus newStatus) {
        access.requireCoordinator(currentUserId);
        Event event = loadOwnedEvent(currentUserId, eventId, true);

        if (newStatus != EventStatus.PUBLISHED && newStatus != EventStatus.CANCELLED) {
            throw BadRequestException.validation(
                    Map.of("status", "Допустимі значення: PUBLISHED, CANCELLED"));
        }
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new ConflictException("INVALID_STATUS_TRANSITION", "Скасовану подію не можна змінити");
        }
        if (event.getStatus() == newStatus) {
            throw new ConflictException("INVALID_STATUS_TRANSITION", "Подія вже має такий статус");
        }

        event.setStatus(newStatus);
        Event saved = eventRepository.saveAndFlush(event);
        return CoordinatorEventResponse.from(saved, applicationRepository.countActiveByEventId(eventId));
    }

    // ---------------------------------------------------------------- Заявки на подію

    @Transactional(readOnly = true)
    public List<EventApplicationResponse> getEventApplications(UUID currentUserId, UUID eventId) {
        access.requireCoordinator(currentUserId);
        loadOwnedEvent(currentUserId, eventId, false);
        return applicationRepository.findAllByEventIdWithUser(eventId).stream()
                .map(EventApplicationResponse::from)
                .toList();
    }

    /**
     * Матриця переходів: PENDING -> APPROVED, PENDING -> REJECTED,
     * APPROVED -> REJECTED (лише до початку події). Решта переходів заборонена.
     */
    public EventApplicationResponse decideApplication(UUID currentUserId, UUID applicationId,
                                                      ApplicationDecisionRequest request) {
        User coordinator = access.requireCoordinator(currentUserId);
        // Блокування заявки не дає учаснику одночасно скасувати її під час ухвалення рішення.
        Application application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new NotFoundException("Заявку не знайдено"));
        Event event = application.getEvent();
        if (!event.getInitiative().getCoordinator().getId().equals(currentUserId)) {
            throw new ForbiddenException("Ви не маєте доступу до цієї заявки");
        }

        ApplicationStatus decision = request.decision();
        if (decision != ApplicationStatus.APPROVED && decision != ApplicationStatus.REJECTED) {
            throw BadRequestException.validation(Map.of("decision", "Допустимі значення: APPROVED, REJECTED"));
        }

        Instant now = Instant.now();
        ApplicationStatus current = application.getStatus();
        boolean allowed = switch (current) {
            case PENDING -> true;
            case APPROVED -> decision == ApplicationStatus.REJECTED && now.isBefore(event.getStartsAt());
            case REJECTED, CANCELLED -> false;
        };
        if (!allowed) {
            throw new ConflictException("INVALID_APPLICATION_TRANSITION",
                    "Перехід статусу заявки з " + current + " у " + decision + " неможливий");
        }

        application.setStatus(decision);
        application.setDecidedBy(coordinator);
        application.setDecidedAt(now);
        application.setUpdatedAt(now);
        return EventApplicationResponse.from(applicationRepository.saveAndFlush(application));
    }

    // ---------------------------------------------------------------- Підсумки події

    public EventSummaryResponse upsertEventSummary(UUID currentUserId, UUID eventId, EventSummaryRequest req) {
        access.requireCoordinator(currentUserId);
        Event event = loadOwnedEvent(currentUserId, eventId, true);

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new ConflictException("EVENT_NOT_PUBLISHED",
                    "Підсумки можна вносити лише для опублікованої події");
        }
        if (Instant.now().isBefore(event.getEndsAt())) {
            throw new ConflictException("EVENT_NOT_FINISHED",
                    "Підсумки можна вносити лише після завершення події");
        }

        EventSummary summary = summaryRepository.findByEventId(eventId).orElseGet(() -> new EventSummary(event));
        summary.setActualParticipants(req.actualParticipants());
        summary.setWasteKg(req.wasteKg());
        summary.setTreesPlanted(req.treesPlanted());
        summary.setHoursSpent(req.hoursSpent());
        String note = req.note() == null ? null : req.note().trim();
        summary.setNote(note == null || note.isEmpty() ? null : note);
        return EventSummaryResponse.from(summaryRepository.saveAndFlush(summary));
    }

    // ---------------------------------------------------------------- Допоміжні методи

    private Initiative loadOwnedInitiative(UUID userId, UUID initiativeId) {
        Initiative initiative = initiativeRepository.findById(initiativeId)
                .orElseThrow(() -> new NotFoundException("Ініціативу не знайдено"));
        if (!initiative.getCoordinator().getId().equals(userId)) {
            throw new ForbiddenException("Ви не маєте доступу до цієї ініціативи");
        }
        return initiative;
    }

    private Event loadOwnedEvent(UUID userId, UUID eventId, boolean forUpdate) {
        Event event = (forUpdate ? eventRepository.findByIdForUpdate(eventId) : eventRepository.findById(eventId))
                .orElseThrow(() -> new NotFoundException("Подію не знайдено"));
        if (!event.getInitiative().getCoordinator().getId().equals(userId)) {
            throw new ForbiddenException("Ви не маєте доступу до цієї події");
        }
        return event;
    }

    /**
     * existingStartsAt = null при створенні. Під час оновлення вимога «початок у майбутньому»
     * застосовується лише якщо дату початку змінюють.
     */
    private Map<String, String> validateEvent(EventUpsertRequest req, Instant existingStartsAt, Instant now) {
        Map<String, String> errors = new LinkedHashMap<>();
        Instant startsAt = req.startsAt();
        if (startsAt == null) {
            errors.put("startsAt", "Дата початку є обов'язковою");
        } else if ((existingStartsAt == null || !startsAt.equals(existingStartsAt)) && !startsAt.isAfter(now)) {
            errors.put("startsAt", "Дата початку має бути в майбутньому");
        }
        if (req.endsAt() == null) {
            errors.put("endsAt", "Дата завершення є обов'язковою");
        } else if (startsAt != null && !req.endsAt().isAfter(startsAt)) {
            errors.put("endsAt", "Дата завершення має бути пізнішою за дату початку");
        }
        if (req.registrationClosesAt() == null) {
            errors.put("registrationClosesAt", "Дата закриття реєстрації є обов'язковою");
        } else if (startsAt != null && req.registrationClosesAt().isAfter(startsAt)) {
            errors.put("registrationClosesAt", "Реєстрація має закриватися не пізніше початку події");
        }
        if (req.participantsLimit() == null || req.participantsLimit() <= 0) {
            errors.put("participantsLimit", "Ліміт учасників має бути більшим за нуль");
        }
        if (req.latitude() != null && (req.latitude() < -90 || req.latitude() > 90)) {
            errors.put("latitude", "Широта має бути в межах від -90 до 90");
        }
        if (req.longitude() != null && (req.longitude() < -180 || req.longitude() > 180)) {
            errors.put("longitude", "Довгота має бути в межах від -180 до 180");
        }
        return errors;
    }

    private void applyEventFields(Event event, EventUpsertRequest req) {
        event.setTitle(req.title().trim());
        event.setDescription(req.description().trim());
        event.setCity(req.city().trim());
        event.setAddressText(req.addressText().trim());
        event.setLatitude(req.latitude());
        event.setLongitude(req.longitude());
        event.setStartsAt(req.startsAt());
        event.setEndsAt(req.endsAt());
        event.setRegistrationClosesAt(req.registrationClosesAt());
        event.setParticipantsLimit(req.participantsLimit());
    }

    private Map<UUID, Long> activeCounts(Collection<Event> events) {
        Map<UUID, Long> counts = new HashMap<>();
        if (events.isEmpty()) {
            return counts;
        }
        List<UUID> ids = events.stream().map(Event::getId).toList();
        for (Object[] row : eventRepository.countActiveByEventIds(ids)) {
            counts.put(UUID.fromString(String.valueOf(row[0])), ((Number) row[1]).longValue());
        }
        return counts;
    }
}
