package com.ecoportal.repository;

import com.ecoportal.domain.Initiative;
import com.ecoportal.domain.InitiativeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InitiativeRepository extends JpaRepository<Initiative, UUID> {

    List<Initiative> findByStatusOrderByCreatedAtDesc(InitiativeStatus status);

    List<Initiative> findByCoordinatorIdOrderByCreatedAtDesc(UUID coordinatorId);
}
