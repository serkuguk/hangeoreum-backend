package com.hangeoreum.api.vocabulary.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "decks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Deck {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID userId;

    private String title;

    private Instant createdAt = Instant.now();

    public static Deck create(UUID userId, String title) {
        Deck deck = new Deck();
        deck.userId = userId;
        deck.title = title;
        return deck;
    }

    public void rename(String title) {
        this.title = title;
    }
}
