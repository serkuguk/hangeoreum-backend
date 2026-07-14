package com.hangeoreum.api.billing.infrastructure;

import com.hangeoreum.api.billing.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByUserIdOrderByCreatedAtDesc(UUID userId);

    boolean existsByProviderPaymentId(String providerPaymentId);
}
