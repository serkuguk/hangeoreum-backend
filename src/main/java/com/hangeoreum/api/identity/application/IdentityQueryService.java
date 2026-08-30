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

    @Transactional(readOnly = true)
    public short dailyGoalXp(UUID userId) {
        return settingsRepository.findById(userId).map(value -> value.getDailyGoalXp()).orElse((short) 20);
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
}
