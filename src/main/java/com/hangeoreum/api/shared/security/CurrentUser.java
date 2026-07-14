package com.hangeoreum.api.shared.security;

import com.hangeoreum.api.shared.web.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw ApiException.unauthorized("Not authenticated");
        }
        return UUID.fromString(jwt.getSubject());
    }
}
