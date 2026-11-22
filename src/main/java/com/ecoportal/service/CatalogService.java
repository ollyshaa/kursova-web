package com.ecoportal.service;

import com.ecoportal.domain.Event;
import com.ecoportal.domain.EventStatus;
import com.ecoportal.domain.Initiative;
import com.ecoportal.domain.InitiativeStatus;
import com.ecoportal.dto.EventDto;
import com.ecoportal.dto.InitiativeShortDto;
import com.ecoportal.exception.BadRequestException;
import com.ecoportal.exception.NotFoundException;
import com.ecoportal.repository.EventRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CatalogService {

    /** Календарні дати фільтра інтерпретуються за київським часом. */
    private static final ZoneId ZONE = ZoneId.of("Europe/Kyiv");
    private static final Locale UK = Locale.forLanguageTag("uk-UA");

    private final EventRepository eventRepository;

    public CatalogService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public List<EventDto> listEvents(String city, LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BadRequestException("INVALID_DATE_RANGE",
                    "Дата початку періоду не може бути пізнішою за дату завершення");
        }
        String normalizedCity = city == null || city.isBlank() ? null : city.trim().toLowerCase(UK);
        Instant from = dateFrom == null ? null : dateFrom.atStartOfDay(ZONE).toInstant();
        Instant toExclusive = dateTo == null ? null : dateTo.plusDays(1).atStartOfDay(ZONE).toInstant();

        Specification<Event> spec = (root, query, cb) -> {
            Join<Event, Initiative> initiative = root.join("initiative");
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), EventStatus.PUBLISHED));
            predicates.add(cb.equal(initiative.get("status"), InitiativeStatus.PUBLISHED));
            if (normalizedCity != null) {
                predicates.add(cb.equal(cb.lower(root.<String>get("city")), normalizedCity));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("startsAt"), from));
            }
            if (toExclusive != null) {
                predicates.add(cb.lessThan(root.<Instant>get("startsAt"), toExclusive));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<Event> events = eventRepository.findAll(spec, Sort.by("startsAt").ascending().and(Sort.by("title")));
        if (events.isEmpty()) {
            return List.of();
        }

        Map<UUID, Long> activeCounts = new HashMap<>();
        List<UUID> ids = events.stream().map(Event::getId).toList();
        for (Object[] row : eventRepository.countActiveByEventIds(ids)) {
            activeCounts.put(UUID.fromString(String.valueOf(row[0])), ((Number) row[1]).longValue());
        }
        return events.stream()
                .map(e -> toDto(e, activeCounts.getOrDefault(e.getId(), 0L)))
                .toList();
    }

    public EventDto getEvent(UUID id) {
        Event event = eventRepository.findPublishedById(id)
                .orElseThrow(() -> new NotFoundException("Подію не знайдено"));
        return toDto(event, eventRepository.countActiveByEventId(id));
    }

    private EventDto toDto(Event e, long activeCount) {
        Initiative i = e.getInitiative();
        int freeSpots = (int) Math.max(0L, e.getParticipantsLimit() - activeCount);
        return new EventDto(
                e.getId(), e.getTitle(), e.getDescription(), e.getCity(), e.getAddressText(),
                e.getLatitude(), e.getLongitude(), e.getStartsAt(), e.getEndsAt(),
                e.getRegistrationClosesAt(), e.getParticipantsLimit(), freeSpots,
                new InitiativeShortDto(i.getId(), i.getTitle(), i.getCategory()));
    }
}
