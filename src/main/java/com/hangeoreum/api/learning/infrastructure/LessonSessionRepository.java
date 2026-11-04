package com.hangeoreum.api.learning.infrastructure;

import com.hangeoreum.api.learning.domain.LessonSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface LessonSessionRepository extends JpaRepository<LessonSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from LessonSession s where s.id = :id")
    Optional<LessonSession> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            insert into lesson_sessions
                (id, user_id, lesson_id, course_id, lesson_title, lesson_type, exercises, started_at, last_received_at)
            values (:id, :userId, :lessonId, :courseId, :title, cast(:type as lesson_type),
                    cast(:exercises as jsonb), :receivedAt, :receivedAt)
            on conflict (id) do nothing
            """, nativeQuery = true)
    int claim(@Param("id") UUID id, @Param("userId") UUID userId, @Param("lessonId") UUID lessonId,
              @Param("courseId") UUID courseId, @Param("title") String title, @Param("type") String type,
              @Param("exercises") String exercises, @Param("receivedAt") Instant receivedAt);
    org.springframework.data.domain.Page<LessonSession> findByUserId(UUID userId, org.springframework.data.domain.Pageable pageable);
    Optional<LessonSession> findByIdAndUserId(UUID id, UUID userId);
    long countByUserId(UUID userId);
    @Query("select min(s.startedAt) from LessonSession s where s.userId = :userId")
    Instant firstTrackedAt(UUID userId);

}
