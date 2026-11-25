package com.ecoportal.service;

import com.ecoportal.domain.Application;
import com.ecoportal.domain.ApplicationStatus;
import com.ecoportal.domain.Event;
import com.ecoportal.domain.EventStatus;
import com.ecoportal.domain.InitiativeStatus;
import com.ecoportal.domain.User;
import com.ecoportal.dto.ApplicationResponse;
import com.ecoportal.dto.MyApplicationResponse;
import com.ecoportal.exception.ConflictException;
import com.ecoportal.exception.ForbiddenException;
import com.ecoportal.exception.NotFoundException;
import com.ecoportal.repository.ApplicationRepository;
import com.ecoportal.repository.EventRepository;
import com.ecoportal.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ApplicationService {

    /** Результат подання: restored = true, якщо відновлено скасовану заявку. */
    public record SubmissionResult(ApplicationResponse application, boolean restored) {
    }

    private final ApplicationRepository applicationRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    public ApplicationService(ApplicationRepository applicationRepository,
                              EventRepository eventRepository,
                              UserRepository userRepository) {
        this.applicationRepository = applicationRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
    }

    /**
     * АЛГ-01. Ідентифікатор користувача береться лише із сесії (СП-01).
     */
    public SubmissionResult submitOrRestoreApplication(UUID currentUserId, UUID eventId, String comment) {
        // Блокування рядка події (SELECT ... FOR UPDATE) серіалізує перевірку ліміту.
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new NotFoundException("Подію не знайдено"));

        // FR-05.4: подія та її ініціатива мають бути опублікованими.
        if (event.getStatus() != EventStatus.PUBLISHED
                || event.getInitiative().getStatus() != InitiativeStatus.PUBLISHED) {
            throw new NotFoundException("Подію не знайдено");
        }

        Instant now = Instant.now();

        // FR-05.1: реєстрація ще відкрита.
        if (!now.isBefore(event.getRegistrationClosesAt())) {
            throw new ConflictException("REGISTRATION_CLOSED", "Реєстрацію на подію закрито");
        }

        // FR-05.2: повторна заявка заборонена, окрім відновлення скасованої.
        Application existing = applicationRepository.findByEventIdAndUserId(eventId, currentUserId).orElse(null);
        if (existing != null && existing.getStatus() != ApplicationStatus.CANCELLED) {
            throw new ConflictException("APPLICATION_ALREADY_EXISTS",
                    "Ви вже подали заявку на цю подію або її було відхилено");
        }

        // FR-05.3: є вільні місця.
        long active = applicationRepository.countActiveByEventId(eventId);
        if (active >= event.getParticipantsLimit()) {
            throw new ConflictException("NO_FREE_SPOTS", "Вільних місць більше немає");
        }

        String normalizedComment = normalizeComment(comment);

        if (existing != null) {
            existing.setStatus(ApplicationStatus.PENDING);
            existing.setComment(normalizedComment);
            existing.setDecidedBy(null);
            existing.setDecidedAt(null);
            existing.setUpdatedAt(now);
            Application saved = applicationRepository.saveAndFlush(existing);
            return new SubmissionResult(ApplicationResponse.from(saved), true);
        }

        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ForbiddenException("Користувача не знайдено, увійдіть повторно"));
        Application created = applicationRepository.saveAndFlush(new Application(event, user, normalizedComment));
        return new SubmissionResult(ApplicationResponse.from(created), false);
    }

    @Transactional(readOnly = true)
    public List<MyApplicationResponse> getMyApplications(UUID currentUserId) {
        Instant now = Instant.now();
        return applicationRepository.findAllByUserIdWithEvent(currentUserId).stream()
                .map(a -> toMyResponse(a, now))
                .toList();
    }

    public ApplicationResponse cancelApplication(UUID currentUserId, UUID applicationId) {
        Application application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new NotFoundException("Заявку не знайдено"));

        // СП-02: скасувати можна лише власну заявку.
        if (!application.getUser().getId().equals(currentUserId)) {
            throw new ForbiddenException("Ви не маєте доступу до цієї заявки");
        }

        Instant now = Instant.now();
        if (!isCancellable(application, now)) {
            throw new ConflictException("CANCELLATION_NOT_ALLOWED", "Скасування заявки неможливе");
        }

        application.setStatus(ApplicationStatus.CANCELLED);
        application.setDecidedBy(null);
        application.setDecidedAt(null);
        application.setUpdatedAt(now);
        return ApplicationResponse.from(applicationRepository.saveAndFlush(application));
    }

    private static boolean isCancellable(Application a, Instant now) {
        boolean cancellableStatus = a.getStatus() == ApplicationStatus.PENDING
                || a.getStatus() == ApplicationStatus.APPROVED;
        return cancellableStatus && now.isBefore(a.getEvent().getStartsAt());
    }

    private static MyApplicationResponse toMyResponse(Application a, Instant now) {
        Event e = a.getEvent();
        return new MyApplicationResponse(
                a.getId(), e.getId(), e.getTitle(), e.getCity(), e.getStartsAt(), e.getStatus(),
                a.getStatus(), a.getComment(), a.getCreatedAt(), a.getUpdatedAt(),
                isCancellable(a, now));
    }

    private static String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String trimmed = comment.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
