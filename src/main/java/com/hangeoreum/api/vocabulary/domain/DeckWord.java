package com.hangeoreum.api.vocabulary.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "deck_words")
@IdClass(DeckWord.Pk.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeckWord {

    public record Pk(UUID deckId, UUID wordId) implements Serializable {
    }

    @Id
    private UUID deckId;

    @Id
    private UUID wordId;

    public static DeckWord of(UUID deckId, UUID wordId) {
        DeckWord dw = new DeckWord();
        dw.deckId = deckId;
        dw.wordId = wordId;
        return dw;
    }
}
