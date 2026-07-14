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
@Table(name = "review_answers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewAnswer {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID sessionId;

    private UUID wordId;

    private short quality;

    private boolean isCorrect;

    private Instant answeredAt = Instant.now();

    public static ReviewAnswer of(UUID sessionId, UUID wordId, int quality) {
        ReviewAnswer answer = new ReviewAnswer();
        answer.sessionId = sessionId;
        answer.wordId = wordId;
        answer.quality = (short) quality;
        answer.isCorrect = quality >= 3;
        return answer;
    }
}
