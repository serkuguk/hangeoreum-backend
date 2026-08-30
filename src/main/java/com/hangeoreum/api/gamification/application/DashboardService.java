package com.hangeoreum.api.gamification.application;

import com.hangeoreum.api.gamification.domain.DailyActivity;
import com.hangeoreum.api.gamification.domain.Level;
import com.hangeoreum.api.gamification.domain.Streak;
import com.hangeoreum.api.gamification.infrastructure.*;
import com.hangeoreum.api.identity.application.IdentityQueryService;
import com.hangeoreum.api.learning.application.LearningService;
import com.hangeoreum.api.learning.application.LearningQueryService;
import com.hangeoreum.api.shared.web.ApiException;
import com.hangeoreum.api.vocabulary.application.VocabularyQueryService;
import lombok.RequiredArgsConstructor;
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
    private final IdentityQueryService identityQueryService;
    private final VocabularyQueryService vocabularyQueryService;
    private final LearningQueryService learningQueryService;
    private final LearningService learningService;

    public record GoalDto(int goalXp, int earnedXp, boolean reached) {
    }

    public record NextLessonDto(UUID id, String title, String type) {
    }

    public record Dashboard(GoalDto goal, VocabularyQueryService.WordSummary wordOfDay, long dueWords, List<NextLessonDto> nextLessons,
                            List<Integer> weekXp, int streak, long totalXp, long wordsLearned,
                            long lessonsCompleted) {
    }

    @Transactional(readOnly = true)
    public Dashboard getDashboard(UUID userId) {
        LocalDate today = LocalDate.now();
        short goalXp = identityQueryService.dailyGoalXp(userId);
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

        return new Dashboard(goal, vocabularyQueryService.wordOfDay(userId, today),
                vocabularyQueryService.dueWordCount(userId, today),
                nextLessons, weekXp,
                streakRepository.findById(userId).map(Streak::getCurrent).orElse(0),
                xpEventRepository.totalXp(userId),
                vocabularyQueryService.learnedWordCount(userId), learningQueryService.completedLessonCount(userId));
    }

    public record ProfileDto(UUID id, String name, String email, String avatarUrl, Instant memberSince,
                             short level, String levelTitle, long totalXp, Integer xpToNext,
                             int streak, int longestStreak, long wordsLearned, long lessonsCompleted,
                             int achievementsEarned) {
    }

    @Transactional(readOnly = true)
    public ProfileDto getProfile(UUID userId) {
        IdentityQueryService.Profile user = identityQueryService.profile(userId);
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
        return new ProfileDto(user.id(), user.name(), user.email(), user.avatarUrl(),
                user.createdAt(),
                current == null ? 1 : current.getLevel(),
                current == null ? "" : current.getTitle(),
                totalXp, xpToNext,
                streak == null ? 0 : streak.getCurrent(),
                streak == null ? 0 : streak.getLongest(),
                vocabularyQueryService.learnedWordCount(userId), learningQueryService.completedLessonCount(userId),
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
