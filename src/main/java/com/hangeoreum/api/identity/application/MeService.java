package com.hangeoreum.api.identity.application;

import com.hangeoreum.api.identity.domain.OauthProvider;
import com.hangeoreum.api.identity.domain.StartLevel;
import com.hangeoreum.api.identity.domain.User;
import com.hangeoreum.api.identity.domain.UserSettings;
import com.hangeoreum.api.identity.infrastructure.OauthLinkRepository;
import com.hangeoreum.api.identity.infrastructure.UserRepository;
import com.hangeoreum.api.identity.infrastructure.UserSettingsRepository;
import com.hangeoreum.api.shared.storage.MediaStorage;
import com.hangeoreum.api.shared.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MeService {

    private final UserRepository userRepository;
    private final UserSettingsRepository settingsRepository;
    private final OauthLinkRepository oauthLinkRepository;
    private final PasswordEncoder passwordEncoder;
    private final MediaStorage mediaStorage;

    @Transactional(readOnly = true)
    public User get(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
    }

    @Transactional
    public User update(UUID userId, String name, StartLevel startLevel) {
        User user = get(userId);
        if (name != null && !name.isBlank()) {
            user.rename(name);
        }
        if (startLevel != null) {
            user.changeStartLevel(startLevel);
        }
        return user;
    }

    @Transactional(readOnly = true)
    public UserSettings getSettings(UUID userId) {
        return settingsRepository.findById(userId).orElseThrow(() -> ApiException.notFound("Settings"));
    }

    @Transactional
    public UserSettings updateSettings(UUID userId, short dailyGoalXp, boolean remindersEnabled,
                                       LocalTime reminderTime, boolean soundEnabled, boolean autoplayAudio,
                                       boolean showRomanization, double playbackSpeed, String theme) {
        UserSettings settings = getSettings(userId);
        settings.update(dailyGoalXp, remindersEnabled, reminderTime, soundEnabled, autoplayAudio,
                showRomanization, playbackSpeed, theme);
        return settings;
    }

    @Transactional
    public void completeOnboarding(UUID userId, StartLevel startLevel, short dailyGoalXp,
                                   boolean remindersEnabled, LocalTime reminderTime) {
        get(userId).changeStartLevel(startLevel);
        getSettings(userId).completeOnboarding(dailyGoalXp, remindersEnabled, reminderTime);
    }

    @Transactional
    public void changePassword(UUID userId, String current, String next) {
        get(userId).changePassword(passwordEncoder, current, next);
    }

    @Transactional
    public String uploadAvatar(UUID userId, MultipartFile file) {
        String url = mediaStorage.store(file, "avatars");
        get(userId).changeAvatar(url);
        return url;
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        userRepository.deleteById(userId);
    }

    @Transactional
    public void unlinkOauth(UUID userId, OauthProvider provider) {
        var link = oauthLinkRepository.findByUserIdAndProvider(userId, provider)
                .orElseThrow(() -> ApiException.notFound("OAuth link"));
        boolean hasOtherLoginMethod = get(userId).hasPassword()
                || oauthLinkRepository.findByUserId(userId).size() > 1;
        if (!hasOtherLoginMethod) {
            throw ApiException.conflict("Cannot unlink the last login method");
        }
        oauthLinkRepository.delete(link);
    }
}
