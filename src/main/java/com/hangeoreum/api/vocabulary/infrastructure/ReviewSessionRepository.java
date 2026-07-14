package com.hangeoreum.api.vocabulary.infrastructure;

import com.hangeoreum.api.vocabulary.domain.ReviewSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ReviewSessionRepository extends JpaRepository<ReviewSession, UUID> {

    Optional<ReviewSession> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndStartedAtAfterAndModeIn(UUID userId, Instant after,
                                                 java.util.Collection<com.hangeoreum.api.vocabulary.domain.ReviewMode> modes);
}
