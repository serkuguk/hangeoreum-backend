package com.hangeoreum.api.learning.infrastructure;

import com.hangeoreum.api.learning.domain.LessonProgress;
import com.hangeoreum.api.learning.domain.ProgressStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, UUID> {

    Optional<LessonProgress> findByUserIdAndLessonId(UUID userId, UUID lessonId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from LessonProgress p where p.userId = :userId and p.lessonId = :lessonId")
    Optional<LessonProgress> findByUserIdAndLessonIdForUpdate(@Param("userId") UUID userId,
                                                               @Param("lessonId") UUID lessonId);

    @Modifying
    @Query(value = """
            insert into lesson_progress (user_id, lesson_id)
            values (:userId, :lessonId)
            on conflict (user_id, lesson_id) do nothing
            """, nativeQuery = true)
    int ensureExists(@Param("userId") UUID userId, @Param("lessonId") UUID lessonId);

    List<LessonProgress> findByUserIdAndStatus(UUID userId, ProgressStatus status);

    long countByUserIdAndStatus(UUID userId, ProgressStatus status);

    boolean existsByUserIdAndStatusAndScoreGreaterThanEqual(UUID userId, ProgressStatus status, short score);

    boolean existsByLessonId(UUID lessonId);
}
