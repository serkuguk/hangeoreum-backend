package com.hangeoreum.api.identity.api;

import com.hangeoreum.api.identity.domain.User;

import java.time.Instant;
import java.util.UUID;

public record UserDto(UUID id, String name, String email, String avatarUrl,
                      String role, String startLevel, Instant createdAt) {

    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getName(), user.getEmail(), user.getAvatarUrl(),
                user.getRole().name(), user.getStartLevel().name(), user.getCreatedAt());
    }
}
