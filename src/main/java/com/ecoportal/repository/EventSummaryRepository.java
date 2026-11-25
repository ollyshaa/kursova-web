package com.ecoportal.repository;

import com.ecoportal.domain.EventSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventSummaryRepository extends JpaRepository<EventSummary, UUID> {

    @Query("select s from EventSummary s where s.event.id = :eventId")
    Optional<EventSummary> findByEventId(@Param("eventId") UUID eventId);

    /**
     * Підсумки завершених опублікованих подій координатора, що закінчилися в межах періоду
     * [periodStart, periodEnd) і не пізніше за asOf.
     */
    @Query("""
            select s from EventSummary s
            join fetch s.event e
            join e.initiative i
            where i.coordinator.id = :coordinatorId
              and e.status = com.ecoportal.domain.EventStatus.PUBLISHED
              and e.endsAt >= :periodStart
              and e.endsAt < :periodEnd
              and e.endsAt <= :asOf
            order by e.city asc, e.endsAt asc
            """)
    List<EventSummary> findForReport(@Param("coordinatorId") UUID coordinatorId,
                                     @Param("periodStart") Instant periodStart,
                                     @Param("periodEnd") Instant periodEnd,
                                     @Param("asOf") Instant asOf);
}
