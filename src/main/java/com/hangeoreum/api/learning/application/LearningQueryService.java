package com.hangeoreum.api.learning.application;

import com.hangeoreum.api.learning.domain.ProgressStatus;
import com.hangeoreum.api.learning.infrastructure.AlphabetLetterRepository;
import com.hangeoreum.api.learning.infrastructure.LessonProgressRepository;
import com.hangeoreum.api.learning.infrastructure.UserLetterProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LearningQueryService {
    private final LessonProgressRepository lessonProgressRepository;
    private final AlphabetLetterRepository alphabetLetterRepository;
    private final UserLetterProgressRepository userLetterProgressRepository;

    @Transactional(readOnly = true)
    public AchievementProgress achievementProgress(UUID userId) {
        return new AchievementProgress(
                lessonProgressRepository.countByUserIdAndStatus(userId, ProgressStatus.COMPLETED),
                lessonProgressRepository.existsByUserIdAndStatusAndScoreGreaterThanEqual(userId, ProgressStatus.COMPLETED, (short) 100),
                userLetterProgressRepository.countByUserId(userId), alphabetLetterRepository.count());
    }

    @Transactional(readOnly = true)
    public long completedLessonCount(UUID userId) {
        return lessonProgressRepository.countByUserIdAndStatus(userId, ProgressStatus.COMPLETED);
    }

    public record AchievementProgress(long completedLessons, boolean perfectLesson, long learnedLetters, long alphabetLetters) {}
}
