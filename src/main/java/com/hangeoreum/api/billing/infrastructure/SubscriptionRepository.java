package com.hangeoreum.api.billing.infrastructure;

import com.hangeoreum.api.billing.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Subscription> findByProviderSubId(String providerSubId);

    List<Subscription> findByUserId(UUID userId);

    long countByStatus(com.hangeoreum.api.billing.domain.SubStatus status);
}
