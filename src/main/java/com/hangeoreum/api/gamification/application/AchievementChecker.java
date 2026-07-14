package com.hangeoreum.api.gamification.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.hangeoreum.api.gamification.domain.Achievement;
import com.hangeoreum.api.gamification.domain.Streak;
import com.hangeoreum.api.gamification.domain.UserAchievement;
import com.hangeoreum.api.gamification.domain.event.AchievementEarnedEvent;
import com.hangeoreum.api.gamification.infrastructure.AchievementRepository;
import com.hangeoreum.api.gamification.infrastructure.StreakRepository;
import com.hangeoreum.api.gamification.infrastructure.UserAchievementRepository;
import com.hangeoreum.api.learning.domain.ProgressStatus;
import com.hangeoreum.api.learning.infrastructure.AlphabetLetterRepository;
import com.hangeoreum.api.learning.infrastructure.LessonProgressRepository;
import com.hangeoreum.api.learning.infrastructure.UserLetterProgressRepository;
import com.hangeoreum.api.vocabulary.infrastructure.UserWordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * ponytail: reads other contexts' repositories directly (read-only counts) to avoid
 * a circular bean graph learning -> xp -> checker -> learning-facade; extract query
 * facades if these reads ever grow write logic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AchievementChecker {

    private final AchievementRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final StreakRepository streakRepository;
    private final UserWordRepository userWordRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final AlphabetLetterRepository alphabetLetterRepository;
    private final UserLetterProgressRepository userLetterProgressRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;

    public void check(UUID userId) {
        Set<UUID> earned = new HashSet<>();
        userAchievementRepository.findByUserId(userId)
                .forEach(ua -> earned.add(ua.getAchievementId()));
        for (Achievement achievement : achievementRepository.findAll()) {
            if (earned.contains(achievement.getId())) {
                continue;
            }
            if (isSatisfied(userId, achievement)) {
                userAchievementRepository.save(UserAchievement.earn(userId, achievement.getId()));
                events.publishEvent(new AchievementEarnedEvent(userId, achievement.getTitle()));
            }
        }
    }

    private boolean isSatisfied(UUID userId, Achievement achievement) {
        try {
            JsonNode condition = objectMapper.readTree(achievement.getCondition());
            String type = condition.path("type").asText("");
            long value = condition.path("value").asLong(0);
            return switch (type) {
                case "words_learned" ->
                        userWordRepository.countByUserIdAndLevelGreaterThanEqual(userId, (short) 1) >= value;
                case "streak_days" ->
                        streakRepository.findById(userId).map(Streak::getCurrent).orElse(0) >= value;
                case "lessons_completed" ->
                        lessonProgressRepository.countByUserIdAndStatus(userId, ProgressStatus.COMPLETED) >= value;
                case "alphabet_done" ->
                        userLetterProgressRepository.countByUserId(userId) >= alphabetLetterRepository.count()
                                && alphabetLetterRepository.count() > 0;
                case "perfect_lesson" ->
                        lessonProgressRepository.existsByUserIdAndStatusAndScoreGreaterThanEqual(
                                userId, ProgressStatus.COMPLETED, (short) 100);
                default -> false;
            };
        } catch (Exception e) {
            log.warn("Bad achievement condition {}: {}", achievement.getCode(), e.getMessage());
            return false;
        }
    }
}
