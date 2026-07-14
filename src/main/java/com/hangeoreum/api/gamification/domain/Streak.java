package com.hangeoreum.api.gamification.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "streaks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Streak {

    @Id
    private UUID userId;

    private int current = 0;

    private int longest = 0;

    private LocalDate lastActiveDate;

    public static Streak create(UUID userId) {
        Streak streak = new Streak();
        streak.userId = userId;
        return streak;
    }

    public void registerActivity(LocalDate today) {
        if (today.equals(lastActiveDate)) {
            return;
        }
        if (lastActiveDate != null && lastActiveDate.plusDays(1).equals(today)) {
            current++;
        } else {
            current = 1;
        }
        longest = Math.max(longest, current);
        lastActiveDate = today;
    }
}
