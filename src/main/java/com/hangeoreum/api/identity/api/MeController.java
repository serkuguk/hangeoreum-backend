package com.hangeoreum.api.identity.api;

import com.fasterxml.jackson.annotation.JsonRawValue;
import tools.jackson.databind.JsonNode;
import com.hangeoreum.api.identity.application.MeService;
import com.hangeoreum.api.identity.domain.OauthProvider;
import com.hangeoreum.api.identity.domain.StartLevel;
import com.hangeoreum.api.identity.domain.UserSettings;
import com.hangeoreum.api.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalTime;
import java.util.Map;

@Tag(name = "Me")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final MeService meService;

    public record UpdateMeRequest(@Size(max = 100) String name, StartLevel startLevel) {
    }

    public record SettingsDto(short dailyGoalXp, boolean remindersEnabled, LocalTime reminderTime,
                              boolean soundEnabled, boolean autoplayAudio, boolean showRomanization,
                              double playbackSpeed, @JsonRawValue String theme) {

        static SettingsDto from(UserSettings s) {
            return new SettingsDto(s.getDailyGoalXp(), s.isRemindersEnabled(), s.getReminderTime(),
                    s.isSoundEnabled(), s.isAutoplayAudio(), s.isShowRomanization(),
                    s.getPlaybackSpeed(), s.getTheme());
        }
    }

    public record UpdateSettingsRequest(short dailyGoalXp, boolean remindersEnabled, LocalTime reminderTime,
                                        boolean soundEnabled, boolean autoplayAudio, boolean showRomanization,
                                        double playbackSpeed, JsonNode theme) {
    }

    public record OnboardingRequest(@NotNull StartLevel startLevel, short dailyGoalXp,
                                    boolean remindersEnabled, LocalTime reminderTime) {
    }

    public record ChangePasswordRequest(@NotBlank String current,
                                        @NotBlank @Size(min = 8, max = 100) String next) {
    }

    @GetMapping
    public UserDto me() {
        return UserDto.from(meService.get(CurrentUser.id()));
    }

    @PatchMapping
    public UserDto update(@RequestBody @Valid UpdateMeRequest request) {
        return UserDto.from(meService.update(CurrentUser.id(), request.name(), request.startLevel()));
    }

    @GetMapping("/settings")
    public SettingsDto settings() {
        return SettingsDto.from(meService.getSettings(CurrentUser.id()));
    }

    @PutMapping("/settings")
    public SettingsDto updateSettings(@RequestBody @Valid UpdateSettingsRequest r) {
        return SettingsDto.from(meService.updateSettings(CurrentUser.id(), r.dailyGoalXp(), r.remindersEnabled(),
                r.reminderTime(), r.soundEnabled(), r.autoplayAudio(), r.showRomanization(),
                r.playbackSpeed(), r.theme() == null ? null : r.theme().toString()));
    }

    @PostMapping("/onboarding")
    public ResponseEntity<Void> onboarding(@RequestBody @Valid OnboardingRequest r) {
        meService.completeOnboarding(CurrentUser.id(), r.startLevel(), r.dailyGoalXp(),
                r.remindersEnabled(), r.reminderTime());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        meService.changePassword(CurrentUser.id(), request.current(), request.next());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/avatar")
    public Map<String, String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Map.of("avatarUrl", meService.uploadAvatar(CurrentUser.id(), file));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAccount() {
        meService.deleteAccount(CurrentUser.id());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/oauth/{provider}")
    public ResponseEntity<Void> unlinkOauth(@PathVariable OauthProvider provider) {
        meService.unlinkOauth(CurrentUser.id(), provider);
        return ResponseEntity.noContent().build();
    }
}
