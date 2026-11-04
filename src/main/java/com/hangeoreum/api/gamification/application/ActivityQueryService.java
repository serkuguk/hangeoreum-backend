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

    public MonitoringActivity monitoringActivity(UUID userId) {
        LocalDate today = LocalDate.now();
        var byDate = new java.util.HashMap<LocalDate, com.hangeoreum.api.gamification.domain.DailyActivity>();
        dailyActivityRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, today.minusDays(29), today)
                .forEach(day -> byDate.put(day.getActivityDate(), day));
        List<ActivityDay> days = new java.util.ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            var day = byDate.get(date);
            days.add(new ActivityDay(date, day == null ? 0 : day.getXpEarned(),
                    day == null ? 0 : day.getLessonsCompleted(), day == null ? 0 : day.getReviewsDone(),
                    day == null ? null : day.getGoalXp()));
        }
        var streak = streakRepository.findById(userId).orElse(null);
        return new MonitoringActivity(today, List.copyOf(days), streak == null ? 0 : streak.getCurrent(),
                streak == null ? 0 : streak.getLongest(), streak == null ? null : streak.getLastActiveDate());
    }

    public record ActivityDay(LocalDate date, int xpEarned, int lessonsCompleted, int reviewsDone, Short goalXp) {}
    public record MonitoringActivity(LocalDate serverDate, List<ActivityDay> days, int currentStreak,
                                      int longestStreak, LocalDate lastActiveDate) {}
}
