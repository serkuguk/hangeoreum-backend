package com.hangeoreum.api.notification.application;

import com.hangeoreum.api.gamification.application.ActivityQueryService;
import com.hangeoreum.api.identity.application.IdentityQueryService;
import com.hangeoreum.api.notification.domain.NotificationType;
import com.hangeoreum.api.notification.infrastructure.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class NotificationJobs {

    private final IdentityQueryService identity;
    private final ActivityQueryService activity;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    /** Every 15 minutes: study reminder for users whose reminder_time has just passed. */
    @Scheduled(cron = "0 */15 * * * *")
    @Transactional
    public void studyReminders() {
        LocalTime now = LocalTime.now();
        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        identity.enabledReminders().forEach(settings -> {
            LocalTime time = settings.reminderTime();
            if (time == null || time.isAfter(now) || time.isBefore(now.minusMinutes(15))) {
                return;
            }
            boolean alreadySent = notificationRepository.existsByUserIdAndTypeAndCreatedAtAfter(
                    settings.userId(), NotificationType.REMINDER, startOfDay);
            if (!alreadySent) {
                notificationService.notify(settings.userId(), NotificationType.REMINDER,
                        "Время заниматься!", "Пара минут корейского — и цель дня твоя.");
            }
        });
    }

    /** Evening streak-risk warning for users who haven't been active today. */
    @Scheduled(cron = "0 0 18 * * *")
    @Transactional
    public void streakRisk() {
        LocalDate today = LocalDate.now();
        Instant startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
        activity.streaksAtRisk(today).forEach(streak -> {
            boolean alreadySent = notificationRepository.existsByUserIdAndTypeAndCreatedAtAfter(
                    streak.userId(), NotificationType.STREAK, startOfDay);
            if (!alreadySent) {
                notificationService.notify(streak.userId(), NotificationType.STREAK,
                        "Серия под угрозой!",
                        "Твоя серия " + streak.current() + " дн. прервётся — заверши хотя бы один урок.");
            }
        });
    }
}
