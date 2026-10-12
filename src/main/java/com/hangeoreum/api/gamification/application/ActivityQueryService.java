package com.hangeoreum.api.gamification.application;

import com.hangeoreum.api.gamification.infrastructure.DailyActivityRepository;
import com.hangeoreum.api.gamification.infrastructure.StreakRepository;
import com.hangeoreum.api.gamification.infrastructure.XpEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ActivityQueryService {
    private final DailyActivityRepository dailyActivityRepository;
    private final StreakRepository streakRepository;
    private final XpEventRepository xpEventRepository;

    public long totalXp(UUID userId) {
        return xpEventRepository.totalXp(userId);
    }

    public DailyMetrics dailyMetrics(LocalDate day) {
        return new DailyMetrics(dailyActivityRepository.countByActivityDate(day),
                dailyActivityRepository.lessonsCompletedOn(day));
    }

    public List<StreakRisk> streaksAtRisk(LocalDate day) {
        return streakRepository.findByCurrentGreaterThanAndLastActiveDateBefore(0, day).stream()
                .map(streak -> new StreakRisk(streak.getUserId(), streak.getCurrent())).toList();
    }

    public record DailyMetrics(long activeUsers, long lessonsCompleted) {}
    public record StreakRisk(UUID userId, int current) {}
}
