package com.hangeoreum.api.billing.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription {

    public static final Duration PAST_DUE_GRACE = Duration.ofDays(3);

    @Id
    @GeneratedValue
    private UUID id;

    private UUID userId;

    private UUID planId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private SubStatus status = SubStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private PayProvider provider;

    private String providerSubId;

    private Instant currentPeriodEnd;

    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public static Subscription activate(UUID userId, UUID planId, PayProvider provider,
                                        String providerSubId, Instant currentPeriodEnd) {
        Subscription sub = new Subscription();
        sub.userId = userId;
        sub.planId = planId;
        sub.provider = provider;
        sub.providerSubId = providerSubId;
        sub.currentPeriodEnd = currentPeriodEnd;
        return sub;
    }

    public void renew(Instant periodEnd) {
        this.status = SubStatus.ACTIVE;
        this.currentPeriodEnd = periodEnd;
    }

    public void markPastDue() {
        this.status = SubStatus.PAST_DUE;
    }

    public void cancel() {
        this.status = SubStatus.CANCELED;
    }

    public void expire() {
        this.status = SubStatus.EXPIRED;
    }

    public boolean isActive(Instant now) {
        boolean statusOk = switch (status) {
            case ACTIVE -> true;
            case PAST_DUE -> currentPeriodEnd == null || now.isBefore(currentPeriodEnd.plus(PAST_DUE_GRACE));
            case CANCELED, EXPIRED -> false;
        };
        if (!statusOk) {
            return false;
        }
        // lifetime -> currentPeriodEnd == null
        if (currentPeriodEnd == null) {
            return true;
        }
        Instant deadline = status == SubStatus.PAST_DUE ? currentPeriodEnd.plus(PAST_DUE_GRACE) : currentPeriodEnd;
        return now.isBefore(deadline);
    }
}
