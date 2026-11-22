package com.ecoportal.repository;

import com.ecoportal.domain.Event;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

    @Override
    @EntityGraph(attributePaths = "initiative")
    List<Event> findAll(Specification<Event> spec, Sort sort);

    @Query("""
            select e from Event e
            join fetch e.initiative i
            where e.id = :id
              and e.status = com.ecoportal.domain.EventStatus.PUBLISHED
              and i.status = com.ecoportal.domain.InitiativeStatus.PUBLISHED
            """)
    Optional<Event> findPublishedById(@Param("id") UUID id);

    /**
     * Кількість активних заявок (PENDING та APPROVED) для кожної події.
     * Рядок результату: [0] - id події, [1] - кількість.
     */
    @Query(value = """
            SELECT a.event_id, COUNT(*)
            FROM applications a
            WHERE a.event_id IN (:eventIds) AND a.status IN ('PENDING', 'APPROVED')
            GROUP BY a.event_id
            """, nativeQuery = true)
    List<Object[]> countActiveByEventIds(@Param("eventIds") Collection<UUID> eventIds);

    @Query(value = """
            SELECT COUNT(*)
            FROM applications a
            WHERE a.event_id = :eventId AND a.status IN ('PENDING', 'APPROVED')
            """, nativeQuery = true)
    long countActiveByEventId(@Param("eventId") UUID eventId);
}
