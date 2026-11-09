package com.hangeoreum.api.vocabulary.infrastructure;

import com.hangeoreum.api.vocabulary.domain.ReviewSessionCard;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ReviewSessionCardRepository extends JpaRepository<ReviewSessionCard, ReviewSessionCard.Pk> {
    long countBySessionId(UUID sessionId);
    interface IssuedCount {
        UUID getSessionId();
        long getIssued();
    }
    @org.springframework.data.jpa.repository.Query("select c.sessionId as sessionId, count(c) as issued from ReviewSessionCard c where c.sessionId in :sessionIds group by c.sessionId")
    java.util.List<IssuedCount> countsBySessionIds(java.util.Collection<UUID> sessionIds);

}
