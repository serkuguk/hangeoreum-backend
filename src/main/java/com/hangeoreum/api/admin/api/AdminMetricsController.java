package com.hangeoreum.api.admin.api;

import com.hangeoreum.api.billing.domain.SubStatus;
import com.hangeoreum.api.billing.infrastructure.SubscriptionRepository;
import com.hangeoreum.api.gamification.infrastructure.DailyActivityRepository;
import com.hangeoreum.api.identity.infrastructure.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Admin")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminMetricsController {

    private final UserRepository userRepository;
    private final DailyActivityRepository dailyActivityRepository;
    private final SubscriptionRepository subscriptionRepository;

    public record Metrics(long totalUsers, long dau, long lessonsCompletedToday,
                          long activeSubscriptions, double conversion) {
    }

    @GetMapping("/metrics")
    public Metrics metrics() {
        long users = userRepository.count();
        long activeSubs = subscriptionRepository.countByStatus(SubStatus.ACTIVE);
        return new Metrics(users,
                dailyActivityRepository.countByActivityDate(LocalDate.now()),
                dailyActivityRepository.lessonsCompletedOn(LocalDate.now()),
                activeSubs,
                users == 0 ? 0 : (double) activeSubs / users);
    }
}
