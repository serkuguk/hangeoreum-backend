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
@Table(name = "review_session_cards")
@IdClass(ReviewSessionCard.Pk.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewSessionCard {
    public record Pk(UUID sessionId, UUID wordId) implements Serializable {}

    @Id private UUID sessionId;
    @Id private UUID wordId;
    private int position;

    public static ReviewSessionCard issued(UUID sessionId, UUID wordId, int position) {
        ReviewSessionCard card = new ReviewSessionCard();
        card.sessionId = sessionId;
        card.wordId = wordId;
        card.position = position;
        return card;
    }
}
