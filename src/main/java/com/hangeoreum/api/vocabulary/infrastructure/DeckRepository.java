package com.hangeoreum.api.vocabulary.infrastructure;

import com.hangeoreum.api.vocabulary.domain.Deck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeckRepository extends JpaRepository<Deck, UUID> {

    List<Deck> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<Deck> findByIdAndUserId(UUID id, UUID userId);
}
