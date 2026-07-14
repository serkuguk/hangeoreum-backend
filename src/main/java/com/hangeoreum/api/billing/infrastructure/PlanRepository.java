package com.hangeoreum.api.billing.infrastructure;

import com.hangeoreum.api.billing.domain.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

    List<Plan> findByIsActiveTrue();

    Optional<Plan> findByCode(String code);
}
