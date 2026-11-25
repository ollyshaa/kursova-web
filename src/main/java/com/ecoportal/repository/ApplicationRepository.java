package com.ecoportal.repository;

import com.ecoportal.domain.Application;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    /** Кількість активних заявок (PENDING та APPROVED) на подію. */
    @Query("""
            select count(a) from Application a
            where a.event.id = :eventId
              and a.status in (com.ecoportal.domain.ApplicationStatus.PENDING,
                               com.ecoportal.domain.ApplicationStatus.APPROVED)
            """)
    long countActiveByEventId(@Param("eventId") UUID eventId);

    @Query("select a from Application a where a.event.id = :eventId and a.user.id = :userId")
    Optional<Application> findByEventIdAndUserId(@Param("eventId") UUID eventId, @Param("userId") UUID userId);

    /** Усі заявки користувача, найновіші першими; подія завантажується одним запитом (без N+1). */
    @Query("""
            select a from Application a
            join fetch a.event e
            where a.user.id = :userId
            order by a.createdAt desc
            """)
    List<Application> findAllByUserIdWithEvent(@Param("userId") UUID userId);

    @Query("""
            select a from Application a
            join fetch a.event e
            where a.id = :id
            """)
    Optional<Application> findByIdWithEvent(@Param("id") UUID id);

    /** Заявки події із заявниками (для кабінету координатора), від найстаріших до найновіших. */
    @Query("""
            select a from Application a
            join fetch a.user u
            where a.event.id = :eventId
            order by a.createdAt asc
            """)
    List<Application> findAllByEventIdWithUser(@Param("eventId") UUID eventId);

    /** Блокування рядка заявки: серіалізує рішення координатора та скасування учасником. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Application a where a.id = :id")
    Optional<Application> findByIdForUpdate(@Param("id") UUID id);
}
