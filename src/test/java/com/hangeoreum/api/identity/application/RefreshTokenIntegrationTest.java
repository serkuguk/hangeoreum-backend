package com.hangeoreum.api.identity.application;

import com.hangeoreum.api.identity.domain.User;
import com.hangeoreum.api.identity.infrastructure.PasswordResetMailSender;
import com.hangeoreum.api.identity.infrastructure.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RefreshTokenIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.baseline-on-migrate", () -> false);
        registry.add("spring.docker.compose.enabled", () -> false);
    }

    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PasswordResetMailSender mailSender;

    @Test
    void concurrentRefreshCreatesExactlyOneSuccessor() throws Exception {
        User user = newUser();
        String token = auth.issueTokens(user).refreshToken();
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> refreshAfter(start, token));
            var second = executor.submit(() -> refreshAfter(start, token));
            assertEquals(List.of(200, 401), List.of(first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)).stream().sorted().toList());
        }
        assertEquals(2, tokenCount(user.getId(), ""));
        assertEquals(1, tokenCount(user.getId(), " and revoked = false"));
        assertTrue(jdbc.queryForObject("select revoked from refresh_tokens where token_hash = ?",
                Boolean.class, AuthService.hash(token)));
        refresh(token, 401);
    }

    @Test
    void missingUnknownExpiredRevokedAndInactiveTokensAreUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());
        refresh("unknown", 401);
        User user = newUser();
        String expired = auth.issueTokens(user).refreshToken();
        jdbc.update("update refresh_tokens set expires_at = now() - interval '1 second' where token_hash = ?",
                AuthService.hash(expired));
        refresh(expired, 401);
        String revoked = auth.issueTokens(user).refreshToken();
        auth.logout(revoked);
        refresh(revoked, 401);
        String inactive = auth.issueTokens(user).refreshToken();
        user.setActive(false);
        users.saveAndFlush(user);
        refresh(inactive, 401);
        assertEquals(3, tokenCount(user.getId(), ""));
    }

    private User newUser() {
        return users.saveAndFlush(User.register("Refresh User", UUID.randomUUID() + "@example.com", "hash"));
    }

    private int tokenCount(UUID userId, String condition) {
        return jdbc.queryForObject("select count(*) from refresh_tokens where user_id = ?" + condition,
                Integer.class, userId);
    }

    private int refreshAfter(CyclicBarrier start, String token) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        return mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", token)))
                .andReturn().getResponse().getStatus();
    }

    private void refresh(String token, int expectedStatus) throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", token)))
                .andExpect(status().is(expectedStatus));
    }
}
