package com.hangeoreum.api.identity.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "user_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSettings {

    public static final short DEFAULT_DAILY_GOAL_XP = 20;

    @Id
    private UUID userId;

    private short dailyGoalXp = DEFAULT_DAILY_GOAL_XP;

    private boolean remindersEnabled = false;

    private LocalTime reminderTime;

    private boolean soundEnabled = true;

    private boolean autoplayAudio = true;

    private boolean showRomanization = true;

    private double playbackSpeed = 1.0;

    @JdbcTypeCode(SqlTypes.JSON)
    private String theme = "{}";

    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public static UserSettings defaults(UUID userId) {
        UserSettings settings = new UserSettings();
        settings.userId = userId;
        return settings;
    }

    public void update(short dailyGoalXp, boolean remindersEnabled, LocalTime reminderTime,
                       boolean soundEnabled, boolean autoplayAudio, boolean showRomanization,
                       double playbackSpeed, String theme) {
        setDailyGoalXp(dailyGoalXp);
        this.remindersEnabled = remindersEnabled;
        this.reminderTime = reminderTime;
        this.soundEnabled = soundEnabled;
        this.autoplayAudio = autoplayAudio;
        this.showRomanization = showRomanization;
        this.playbackSpeed = playbackSpeed;
        if (theme != null) {
            this.theme = theme;
        }
    }

    public void completeOnboarding(short dailyGoalXp, boolean remindersEnabled, LocalTime reminderTime) {
        setDailyGoalXp(dailyGoalXp);
        this.remindersEnabled = remindersEnabled;
        this.reminderTime = reminderTime;
    }

    private void setDailyGoalXp(short value) {
        if (value != 10 && value != DEFAULT_DAILY_GOAL_XP && value != 50) {
            throw com.hangeoreum.api.shared.web.ApiException.badRequest("Daily goal must be 10, 20 or 50 XP");
        }
        this.dailyGoalXp = value;
    }
}
