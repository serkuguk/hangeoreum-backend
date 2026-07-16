package com.hangeoreum.api.learning.infrastructure;

import com.hangeoreum.api.learning.domain.LessonAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface LessonAttemptRepository extends JpaRepository<LessonAttempt, UUID> {

    @Modifying
    @Query(value = """
            insert into lesson_attempts (id, user_id, lesson_id, score, accuracy)
            values (:attemptId, :userId, :lessonId, :score, :accuracy)
            on conflict (id) do nothing
            """, nativeQuery = true)
    int claim(@Param("attemptId") UUID attemptId, @Param("userId") UUID userId,
              @Param("lessonId") UUID lessonId, @Param("score") short score,
              @Param("accuracy") short accuracy);
}
