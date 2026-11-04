package com.hangeoreum.api.vocabulary.infrastructure;

import com.hangeoreum.api.vocabulary.domain.ReviewAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReviewAnswerRepository extends JpaRepository<ReviewAnswer, UUID> {

    Optional<ReviewAnswer> findBySessionIdAndWordId(UUID sessionId, UUID wordId);
    org.springframework.data.domain.Page<ReviewAnswer> findBySessionId(UUID sessionId, org.springframework.data.domain.Pageable pageable);
    interface AnswerCount {
        UUID getSessionId();
        long getAnswered();
        long getCorrect();
    }
    @org.springframework.data.jpa.repository.Query("""
            select a.sessionId as sessionId, count(a) as answered,
                   sum(case when a.isCorrect = true then 1 else 0 end) as correct
            from ReviewAnswer a where a.sessionId in :sessionIds group by a.sessionId
            """)
    java.util.List<AnswerCount> countsBySessionIds(java.util.Collection<UUID> sessionIds);

}
