package com.hangeoreum.api.vocabulary.infrastructure;

import com.hangeoreum.api.vocabulary.domain.ReviewSessionCard;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ReviewSessionCardRepository extends JpaRepository<ReviewSessionCard, ReviewSessionCard.Pk> {
    long countBySessionId(UUID sessionId);
}
