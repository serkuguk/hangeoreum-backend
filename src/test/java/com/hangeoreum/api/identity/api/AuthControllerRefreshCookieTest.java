package com.hangeoreum.api.identity.api;

import com.hangeoreum.api.identity.application.AuthService;
import com.hangeoreum.api.identity.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpHeaders;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerRefreshCookieTest {

    @Test
    void productionCookiesAreSecureAndLocalHttpStillWorksForEveryCookieWriter() {
        for (String profile : new String[]{"prod", "default"}) {
            try (var context = new AnnotationConfigApplicationContext()) {
                context.getEnvironment().setActiveProfiles(profile);
                AuthService auth = mock(AuthService.class);
                var pair = new AuthService.TokenPair("access", "refresh",
                        User.register("User", "user@example.com", "hash"));
                when(auth.register("User", "user@example.com", "password")).thenReturn(pair);
                when(auth.login("user@example.com", "password")).thenReturn(pair);
                when(auth.refresh("old-refresh")).thenReturn(pair);
                when(auth.refreshTtl()).thenReturn(Duration.ofDays(30));
                context.getBeanFactory().registerSingleton("authService", auth);
                context.register(AuthController.class);
                context.refresh();
                AuthController controller = context.getBean(AuthController.class);
                var responses = java.util.List.of(
                        controller.register(new AuthController.RegisterRequest("User", "user@example.com", "password")),
                        controller.login(new AuthController.LoginRequest("user@example.com", "password")),
                        controller.refresh("old-refresh"), controller.logout("old-refresh"));
                for (var response : responses) {
                    String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
                    assertTrue(cookie.contains("HttpOnly"));
                    assertTrue(cookie.contains("Path=/api/v1/auth"));
                    assertTrue(cookie.contains("SameSite=Lax"));
                    if (profile.equals("prod")) {
                        assertTrue(cookie.contains("Secure"));
                    } else {
                        assertFalse(cookie.contains("Secure"));
                    }
                }
            }
        }
    }
}
