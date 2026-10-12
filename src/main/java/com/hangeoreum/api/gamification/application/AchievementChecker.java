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
import com.hangeoreum.api.learning.application.LearningQueryService;
import com.hangeoreum.api.vocabulary.application.VocabularyQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Reads cross-context facts through owner application APIs; this keeps achievement
 * rules in Gamification without coupling it to another context's persistence.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AchievementChecker {

    private final AchievementRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final StreakRepository streakRepository;
    private final VocabularyQueryService vocabularyQueryService;
    private final LearningQueryService learningQueryService;
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
            LearningQueryService.AchievementProgress learning = learningQueryService.achievementProgress(userId);
            JsonNode condition = objectMapper.readTree(achievement.getCondition());
            String type = condition.path("type").asText("");
            long value = condition.path("value").asLong(0);
            return switch (type) {
                case "words_learned" ->
                        vocabularyQueryService.learnedWordCount(userId) >= value;
                case "streak_days" ->
                        streakRepository.findById(userId).map(Streak::getCurrent).orElse(0) >= value;
                case "lessons_completed" ->
                        learning.completedLessons() >= value;
                case "alphabet_done" ->
                        learning.learnedLetters() >= learning.alphabetLetters() && learning.alphabetLetters() > 0;
                case "perfect_lesson" ->
                        learning.perfectLesson();
                default -> false;
            };
        } catch (Exception e) {
            log.warn("Bad achievement condition {}: {}", achievement.getCode(), e.getMessage());
            return false;
        }
    }
}
