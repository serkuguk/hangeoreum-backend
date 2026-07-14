package com.hangeoreum.api.billing.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionTest {

    private final Instant now = Instant.parse("2026-07-10T12:00:00Z");

    private Subscription active(Instant periodEnd) {
        return Subscription.activate(UUID.randomUUID(), UUID.randomUUID(), PayProvider.STRIPE, "sub_1", periodEnd);
    }

    @Test
    void activeWithFuturePeriodEnd() {
        assertTrue(active(now.plus(Duration.ofDays(10))).isActive(now));
    }

    @Test
    void expiredPeriodEndIsInactive() {
        assertFalse(active(now.minus(Duration.ofDays(1))).isActive(now));
    }

    @Test
    void lifetimeHasNoPeriodEnd() {
        assertTrue(active(null).isActive(now));
    }

    @Test
    void pastDueWithinGraceIsStillActive() {
        Subscription sub = active(now.minus(Duration.ofDays(1)));
        sub.markPastDue();
        assertTrue(sub.isActive(now));
    }

    @Test
    void pastDueBeyondGraceIsInactive() {
        Subscription sub = active(now.minus(Duration.ofDays(4)));
        sub.markPastDue();
        assertFalse(sub.isActive(now));
    }

    @Test
    void canceledIsInactive() {
        Subscription sub = active(now.plus(Duration.ofDays(10)));
        sub.cancel();
        assertFalse(sub.isActive(now));
    }
}
