package com.hangeoreum.api.gamification.application;

import com.hangeoreum.api.gamification.domain.DailyActivity;
import com.hangeoreum.api.gamification.domain.Streak;
import com.hangeoreum.api.gamification.domain.XpEvent;
import com.hangeoreum.api.gamification.domain.XpSource;
import com.hangeoreum.api.gamification.infrastructure.DailyActivityRepository;
import com.hangeoreum.api.gamification.infrastructure.StreakRepository;
import com.hangeoreum.api.gamification.infrastructure.XpEventRepository;
import com.hangeoreum.api.identity.domain.event.UserRegisteredEvent;
import com.hangeoreum.api.identity.infrastructure.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GrantXpService {

    /** ponytail: fixed daily-goal bonus; move to config if game balance needs tuning */
    public static final int DAILY_GOAL_BONUS_XP = 5;

    private final XpEventRepository xpEventRepository;
    private final DailyActivityRepository dailyActivityRepository;
    private final StreakRepository streakRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final AchievementChecker achievementChecker;

    public record GrantResult(int xp, boolean goalReached, int streakCurrent) {
    }

    /** The single entry point for awarding XP. */
    @Transactional
    public GrantResult grant(UUID userId, int amount, XpSource source, UUID sourceId) {
        return grant(userId, amount, source, sourceId, null);
    }

    /**
     * Awards XP at most once for a client/server operation key.  The database claim is
     * deliberately made before touching daily totals or streaks, so a network replay
     * cannot duplicate any of the derived counters.
     */
    @Transactional
    public GrantResult grant(UUID userId, int amount, XpSource source, UUID sourceId, UUID idempotencyKey) {
        LocalDate today = LocalDate.now();
        if (idempotencyKey != null
                && xpEventRepository.claim(userId, amount, source.name(), sourceId, idempotencyKey) == 0) {
            return new GrantResult(0, false, streakRepository.findById(userId)
                    .map(Streak::getCurrent).orElse(0));
        }
        if (idempotencyKey == null) {
            xpEventRepository.save(XpEvent.of(userId, amount, source, sourceId));
        }

        dailyActivityRepository.ensureToday(userId, today, goalXpOf(userId));
        DailyActivity activity = dailyActivityRepository.findByUserIdAndActivityDateForUpdate(userId, today)
                .orElseThrow(() -> new IllegalStateException("Daily activity was not created"));
        boolean goalReached = activity.addXp(amount);
        if (goalReached) {
            xpEventRepository.save(XpEvent.of(userId, DAILY_GOAL_BONUS_XP, XpSource.STREAK_BONUS, null));
            activity.addXp(DAILY_GOAL_BONUS_XP);
        }
        if (source == XpSource.LESSON || source == XpSource.STORY) {
            activity.registerLesson();
        }
        if (source == XpSource.REVIEW || source == XpSource.GAME) {
            activity.registerReview();
        }

        Streak streak = streakRepository.findByIdForUpdate(userId)
                .orElseGet(() -> streakRepository.save(Streak.create(userId)));
        streak.registerActivity(today);

        achievementChecker.check(userId);
        int totalXp = amount + (goalReached ? DAILY_GOAL_BONUS_XP : 0);
        return new GrantResult(totalXp, goalReached, streak.getCurrent());
    }

    @Transactional(readOnly = true)
    public long lessonsCompletedToday(UUID userId) {
        return dailyActivityRepository.findByUserIdAndActivityDate(userId, LocalDate.now())
                .map(DailyActivity::getLessonsCompleted)
                .orElse((short) 0);
    }

    private short goalXpOf(UUID userId) {
        return userSettingsRepository.findById(userId)
                .map(s -> s.getDailyGoalXp())
                .orElse((short) 20);
    }

    @EventListener
    @Transactional
    public void onUserRegistered(UserRegisteredEvent event) {
        if (streakRepository.findById(event.userId()).isEmpty()) {
            streakRepository.save(Streak.create(event.userId()));
        }
    }
}
