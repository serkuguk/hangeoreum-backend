package com.hangeoreum.api.gamification.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_activity")
@IdClass(DailyActivity.Pk.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyActivity {

    public record Pk(UUID userId, LocalDate activityDate) implements Serializable {
    }

    @Id
    private UUID userId;

    @Id
    private LocalDate activityDate;

    private int xpEarned = 0;

    private short lessonsCompleted = 0;

    private short reviewsDone = 0;

    private short goalXp = 20;

    public static DailyActivity start(UUID userId, LocalDate date, short goalXp) {
        DailyActivity activity = new DailyActivity();
        activity.userId = userId;
        activity.activityDate = date;
        activity.goalXp = goalXp;
        return activity;
    }

    /** @return true when this addition crossed the daily goal */
    public boolean addXp(int amount) {
        boolean before = xpEarned >= goalXp;
        xpEarned += amount;
        return !before && xpEarned >= goalXp;
    }

    public void registerLesson() {
        lessonsCompleted++;
    }

    public void registerReview() {
        reviewsDone++;
    }
}
