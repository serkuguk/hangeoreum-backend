package com.hangeoreum.api.learning.infrastructure;

import com.hangeoreum.api.learning.domain.LessonExerciseResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LessonExerciseResultRepository extends JpaRepository<LessonExerciseResult, UUID> {
    Optional<LessonExerciseResult> findBySessionIdAndSequence(UUID sessionId, int sequence);
    org.springframework.data.domain.Page<LessonExerciseResult> findBySessionId(UUID sessionId, org.springframework.data.domain.Pageable pageable);
    interface ResultCounts {
        UUID getSessionId();
        long getSaved();
        long getCorrect();
        long getDistinctExercises();
    }
    @org.springframework.data.jpa.repository.Query("""
            select r.sessionId as sessionId, count(r) as saved,
                   sum(case when r.correct = true then 1 else 0 end) as correct,
                   count(distinct r.exerciseId) as distinctExercises
            from LessonExerciseResult r where r.sessionId in :sessionIds group by r.sessionId
            """)
    java.util.List<ResultCounts> countsBySessionIds(java.util.Collection<UUID> sessionIds);
    @org.springframework.data.jpa.repository.Query("select distinct r.exerciseId from LessonExerciseResult r where r.sessionId = :sessionId and r.correct = true")
    java.util.List<UUID> correctExerciseIds(UUID sessionId);
    interface SequenceGap {
        int getFirstSequence();
        int getLastSequence();
    }
    @org.springframework.data.jpa.repository.Query(value = """
            select previous + 1 as firstSequence, sequence_number - 1 as lastSequence
            from (select sequence_number, lag(sequence_number, 1, 0) over (order by sequence_number) as previous
                  from lesson_exercise_results where session_id = :sessionId) ordered
            where sequence_number > previous + 1 order by sequence_number
            """, nativeQuery = true)
    java.util.List<SequenceGap> sequenceGaps(UUID sessionId);

}
