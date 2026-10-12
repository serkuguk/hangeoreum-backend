package com.hangeoreum.api.identity.application;

import com.hangeoreum.api.identity.infrastructure.UserSettingsRepository;
import com.hangeoreum.api.identity.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdentityQueryService {
    private final UserSettingsRepository settingsRepository;
    private final UserRepository userRepository;

    /** Shared lock order for billing and deletion: account, then subscription. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public boolean lockAccount(UUID userId) {
        return userRepository.findByIdForUpdate(userId).isPresent();
    }

    @Transactional(readOnly = true)
    public short dailyGoalXp(UUID userId) {
        return settingsRepository.findById(userId).map(value -> value.getDailyGoalXp()).orElse(com.hangeoreum.api.identity.domain.UserSettings.DEFAULT_DAILY_GOAL_XP);
    }

    @Transactional(readOnly = true)
    public List<ReminderSettings> enabledReminders() {
        return settingsRepository.findByRemindersEnabledTrue().stream()
                .map(value -> new ReminderSettings(value.getUserId(), value.getReminderTime())).toList();
    }

    @Transactional(readOnly = true)
    public Profile profile(UUID userId) {
        var user = userRepository.findById(userId).orElseThrow();
        return new Profile(user.getId(), user.getName(), user.getEmail(), user.getAvatarUrl(), user.getCreatedAt(), dailyGoalXp(userId));
    }

    public record ReminderSettings(UUID userId, LocalTime reminderTime) {}
    public record Profile(UUID id, String name, String email, String avatarUrl, java.time.Instant createdAt, short dailyGoalXp) {}

    @Transactional(readOnly = true)
    public java.util.Optional<com.hangeoreum.api.identity.domain.UserRole> activeRole(UUID userId) {
        return userRepository.findById(userId).filter(com.hangeoreum.api.identity.domain.User::isActive)
                .map(com.hangeoreum.api.identity.domain.User::getRole);
    }

    @Transactional(readOnly = true)
    public boolean isAdmin(UUID userId) {
        return userRepository.findById(userId)
                .map(user -> user.getRole() == com.hangeoreum.api.identity.domain.UserRole.ADMIN).orElse(false);
    }

    @Transactional(readOnly = true)
    public long userCount() {
        return userRepository.count();
    }

}
