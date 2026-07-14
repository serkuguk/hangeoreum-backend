package com.hangeoreum.api.gamification.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_achievements")
@IdClass(UserAchievement.Pk.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAchievement {

    public record Pk(UUID userId, UUID achievementId) implements Serializable {
    }

    @Id
    private UUID userId;

    @Id
    private UUID achievementId;

    private Instant earnedAt = Instant.now();

    public static UserAchievement earn(UUID userId, UUID achievementId) {
        UserAchievement ua = new UserAchievement();
        ua.userId = userId;
        ua.achievementId = achievementId;
        return ua;
    }
}
