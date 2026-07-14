package com.hangeoreum.api.gamification.application;

import com.hangeoreum.api.gamification.domain.DailyActivity;
import com.hangeoreum.api.gamification.domain.Level;
import com.hangeoreum.api.gamification.domain.Streak;
import com.hangeoreum.api.gamification.infrastructure.*;
import com.hangeoreum.api.identity.domain.User;
import com.hangeoreum.api.identity.infrastructure.UserRepository;
import com.hangeoreum.api.identity.infrastructure.UserSettingsRepository;
import com.hangeoreum.api.learning.application.LearningService;
import com.hangeoreum.api.learning.domain.ProgressStatus;
import com.hangeoreum.api.learning.infrastructure.LessonProgressRepository;
import com.hangeoreum.api.shared.web.ApiException;
import com.hangeoreum.api.vocabulary.api.WordDto;
import com.hangeoreum.api.vocabulary.infrastructure.UserWordRepository;
import com.hangeoreum.api.vocabulary.infrastructure.WordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Composite read model over gamification's own tables plus learning/vocabulary reads.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DailyActivityRepository dailyActivityRepository;
    private final StreakRepository streakRepository;
    private final XpEventRepository xpEventRepository;
    private final LevelRepository levelRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final AchievementRepository achievementRepository;
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final UserWordRepository userWordRepository;
    private final WordRepository wordRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final LearningService learningService;

    public record GoalDto(int goalXp, int earnedXp, boolean reached) {
    }

    public record NextLessonDto(UUID id, String title, String type) {
    }

    public record Dashboard(GoalDto goal, WordDto wordOfDay, long dueWords, List<NextLessonDto> nextLessons,
                            List<Integer> weekXp, int streak, long totalXp, long wordsLearned,
                            long lessonsCompleted) {
    }

    @Transactional(readOnly = true)
    public Dashboard getDashboard(UUID userId) {
        LocalDate today = LocalDate.now();
        short goalXp = userSettingsRepository.findById(userId)
                .map(s -> s.getDailyGoalXp()).orElse((short) 20);
        DailyActivity activity = dailyActivityRepository.findByUserIdAndActivityDate(userId, today).orElse(null);
        int earned = activity == null ? 0 : activity.getXpEarned();
        GoalDto goal = new GoalDto(goalXp, earned, earned >= goalXp);

        Map<LocalDate, Integer> byDay = new HashMap<>();
        dailyActivityRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(
                        userId, today.minusDays(6), today)
                .forEach(d -> byDay.put(d.getActivityDate(), d.getXpEarned()));
        List<Integer> weekXp = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            weekXp.add(byDay.getOrDefault(today.minusDays(i), 0));
        }

        List<NextLessonDto> nextLessons = new ArrayList<>();
        for (LearningService.UnitNode unit : learningService.getCourseMap(userId).units()) {
            for (LearningService.LessonNode lesson : unit.lessons()) {
                if ("AVAILABLE".equals(lesson.status()) && nextLessons.size() < 3) {
                    nextLessons.add(new NextLessonDto(lesson.id(), lesson.title(), lesson.type().name()));
                }
            }
        }

        return new Dashboard(goal, wordOfDay(userId),
                userWordRepository.countByUserIdAndDueDateLessThanEqual(userId, today),
                nextLessons, weekXp,
                streakRepository.findById(userId).map(Streak::getCurrent).orElse(0),
                xpEventRepository.totalXp(userId),
                userWordRepository.countByUserIdAndLevelGreaterThanEqual(userId, (short) 1),
                lessonProgressRepository.countByUserIdAndStatus(userId, ProgressStatus.COMPLETED));
    }

    /** Deterministic pick by (date, userId) hash — no extra table. */
    private WordDto wordOfDay(UUID userId) {
        long count = wordRepository.count();
        if (count == 0) {
            return null;
        }
        int index = Math.floorMod(Objects.hash(LocalDate.now(), userId), (int) Math.min(count, Integer.MAX_VALUE));
        var page = wordRepository.findAll(PageRequest.of(index, 1,
                org.springframework.data.domain.Sort.by("createdAt", "id")));
        return page.hasContent() ? WordDto.from(page.getContent().getFirst()) : null;
    }

    public record ProfileDto(UUID id, String name, String email, String avatarUrl, Instant memberSince,
                             short level, String levelTitle, long totalXp, Integer xpToNext,
                             int streak, int longestStreak, long wordsLearned, long lessonsCompleted,
                             int achievementsEarned) {
    }

    @Transactional(readOnly = true)
    public ProfileDto getProfile(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        long totalXp = xpEventRepository.totalXp(userId);
        List<Level> levels = levelRepository.findAllByOrderByMinXpAsc();
        Level current = null;
        Integer xpToNext = null;
        for (Level level : levels) {
            if (totalXp >= level.getMinXp()) {
                current = level;
            } else {
                xpToNext = (int) (level.getMinXp() - totalXp);
                break;
            }
        }
        Streak streak = streakRepository.findById(userId).orElse(null);
        return new ProfileDto(user.getId(), user.getName(), user.getEmail(), user.getAvatarUrl(),
                user.getCreatedAt(),
                current == null ? 1 : current.getLevel(),
                current == null ? "" : current.getTitle(),
                totalXp, xpToNext,
                streak == null ? 0 : streak.getCurrent(),
                streak == null ? 0 : streak.getLongest(),
                userWordRepository.countByUserIdAndLevelGreaterThanEqual(userId, (short) 1),
                lessonProgressRepository.countByUserIdAndStatus(userId, ProgressStatus.COMPLETED),
                userAchievementRepository.findByUserId(userId).size());
    }

    public record AchievementDto(UUID id, String code, String title, String description, String icon,
                                 Instant earnedAt) {
    }

    @Transactional(readOnly = true)
    public List<AchievementDto> getAchievements(UUID userId) {
        Map<UUID, Instant> earned = new HashMap<>();
        userAchievementRepository.findByUserId(userId)
                .forEach(ua -> earned.put(ua.getAchievementId(), ua.getEarnedAt()));
        return achievementRepository.findAll().stream()
                .map(a -> new AchievementDto(a.getId(), a.getCode(), a.getTitle(), a.getDescription(),
                        a.getIcon(), earned.get(a.getId())))
                .toList();
    }
}
