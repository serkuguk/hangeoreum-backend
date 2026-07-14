package com.hangeoreum.api.billing.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID subscriptionId;

    private UUID userId;

    private int amountCents;

    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private PaymentStatus status = PaymentStatus.PENDING;

    private String providerPaymentId;

    private Instant createdAt = Instant.now();

    public static Payment record(UUID userId, UUID subscriptionId, int amountCents, String currency,
                                 PaymentStatus status, String providerPaymentId) {
        Payment payment = new Payment();
        payment.userId = userId;
        payment.subscriptionId = subscriptionId;
        payment.amountCents = amountCents;
        if (currency != null) {
            payment.currency = currency;
        }
        payment.status = status;
        payment.providerPaymentId = providerPaymentId;
        return payment;
    }
}
