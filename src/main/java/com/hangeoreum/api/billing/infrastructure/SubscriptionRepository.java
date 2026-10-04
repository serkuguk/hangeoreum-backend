package com.hangeoreum.api.billing.infrastructure;

import com.hangeoreum.api.billing.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    @org.springframework.data.jpa.repository.Query("select s.userId from Subscription s where s.provider = :provider and s.providerSubId = :ref")
    Optional<UUID> findOwner(com.hangeoreum.api.billing.domain.PayProvider provider, String ref);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from Subscription s where s.provider = :provider and s.providerSubId = :ref")
    Optional<Subscription> findForUpdate(com.hangeoreum.api.billing.domain.PayProvider provider, String ref);

    List<Subscription> findByUserId(UUID userId);

    long countByStatus(com.hangeoreum.api.billing.domain.SubStatus status);
}
