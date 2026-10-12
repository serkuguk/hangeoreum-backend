package com.hangeoreum.api.admin.application;

import com.hangeoreum.api.billing.application.BillingService;
import com.hangeoreum.api.gamification.application.ActivityQueryService;
import com.hangeoreum.api.identity.application.IdentityQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class AdminMetricsService {
    private final IdentityQueryService identity;
    private final ActivityQueryService activity;
    private final BillingService billing;

    @Transactional(readOnly = true)
    public Metrics metrics() {
        long users = identity.userCount();
        long activeSubs = billing.activeSubscriptionCount();
        var daily = activity.dailyMetrics(LocalDate.now());
        return new Metrics(users, daily.activeUsers(), daily.lessonsCompleted(), activeSubs,
                users == 0 ? 0 : (double) activeSubs / users);
    }

    public record Metrics(long totalUsers, long dau, long lessonsCompletedToday,
                          long activeSubscriptions, double conversion) {}
}
