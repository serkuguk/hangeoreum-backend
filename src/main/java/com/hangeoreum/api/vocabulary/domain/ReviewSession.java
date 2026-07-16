package com.hangeoreum.api.vocabulary.domain;

import com.hangeoreum.api.shared.web.ApiException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewSession {

    /** ponytail: XP cap per session; tune when balancing */
    public static final int XP_CAP = 20;

    @Id
    @GeneratedValue
    private UUID id;

    private UUID userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ReviewMode mode;

    private Instant startedAt = Instant.now();

    private Instant finishedAt;

    private short total = 0;

    private short correct = 0;

    private short xpEarned = 0;

    private Integer streakAfter;

    public static ReviewSession start(UUID userId, ReviewMode mode) {
        ReviewSession session = new ReviewSession();
        session.userId = userId;
        session.mode = mode;
        return session;
    }

    public void registerAnswer(boolean isCorrect) {
        requireOpen();
        total++;
        if (isCorrect) {
            correct++;
        }
    }

    public int finish() {
        requireOpen();
        this.finishedAt = Instant.now();
        this.xpEarned = (short) Math.min(correct, XP_CAP);
        return xpEarned;
    }

    public void saveStreakAfter(int streak) {
        this.streakAfter = streak;
    }

    public boolean isFinished() {
        return finishedAt != null;
    }

    private void requireOpen() {
        if (isFinished()) {
            throw ApiException.conflict("Review session is already finished");
        }
    }

    public boolean isGame() {
        return mode == ReviewMode.MATCH || mode == ReviewMode.LISTEN || mode == ReviewMode.SPELL;
    }
}
