package com.hangeoreum.api.learning.infrastructure;

import com.hangeoreum.api.learning.domain.LessonProgress;
import com.hangeoreum.api.learning.domain.ProgressStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, UUID> {

    Optional<LessonProgress> findByUserIdAndLessonId(UUID userId, UUID lessonId);

    List<LessonProgress> findByUserIdAndStatus(UUID userId, ProgressStatus status);

    long countByUserIdAndStatus(UUID userId, ProgressStatus status);

    boolean existsByUserIdAndStatusAndScoreGreaterThanEqual(UUID userId, ProgressStatus status, short score);

    boolean existsByLessonId(UUID lessonId);
}
