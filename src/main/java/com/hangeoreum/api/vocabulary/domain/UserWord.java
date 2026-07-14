package com.hangeoreum.api.vocabulary.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "user_words")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserWord {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "word_id")
    private Word word;

    private double easeFactor = 2.5;

    private int intervalDays = 0;

    private int repetitions = 0;

    private LocalDate dueDate = LocalDate.now();

    private short level = 0;

    private boolean isDifficult = false;

    private Instant addedAt = Instant.now();

    public static UserWord add(UUID userId, Word word) {
        UserWord userWord = new UserWord();
        userWord.userId = userId;
        userWord.word = word;
        return userWord;
    }

    public void review(int quality) {
        Sm2Algorithm.Result result = Sm2Algorithm.apply(easeFactor, intervalDays, repetitions, quality);
        this.easeFactor = result.easeFactor();
        this.intervalDays = result.intervalDays();
        this.repetitions = result.repetitions();
        this.dueDate = LocalDate.now().plusDays(result.intervalDays());
        // ponytail: stars = capped repetitions; refine with an EF-based mapping if UX asks
        this.level = (short) Math.min(5, result.repetitions());
    }

    public void setDifficult(boolean difficult) {
        this.isDifficult = difficult;
    }
}
