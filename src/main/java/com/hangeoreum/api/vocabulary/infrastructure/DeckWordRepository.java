package com.hangeoreum.api.vocabulary.infrastructure;

import com.hangeoreum.api.vocabulary.domain.DeckWord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeckWordRepository extends JpaRepository<DeckWord, DeckWord.Pk> {

    List<DeckWord> findByDeckId(UUID deckId);

    long countByDeckId(UUID deckId);
}
