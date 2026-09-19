package com.hangeoreum.api.identity.application;

import com.hangeoreum.api.identity.domain.User;
import com.hangeoreum.api.identity.infrastructure.PasswordResetMailSender;
import com.hangeoreum.api.identity.infrastructure.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PasswordResetIntegrationTest {

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

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    PasswordResetMailSender mailSender;

    @Test
    void completePasswordResetFlowUsesV6SecurityJpaAndDatabaseLocks() throws Exception {
        assertEquals("password_reset_tokens", jdbc.queryForObject(
                "select to_regclass('public.password_reset_tokens')::text", String.class));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '6' and success", Integer.class));

        requestReset("missing@example.com", 202);
        verify(mailSender, times(0)).send(eq("missing@example.com"), org.mockito.ArgumentMatchers.anyString());

        User user = userRepository.saveAndFlush(
                User.register("Reset User", "reset@example.com", passwordEncoder.encode("old-password")));
        UUID userId = user.getId();
        doAnswer(invocation -> {
            String rawToken = invocation.getArgument(1);
            assertEquals(1, jdbc.queryForObject(
                    "select count(*) from password_reset_tokens where token_hash = ?",
                    Integer.class, AuthService.hash(rawToken)));
            return null;
        }).when(mailSender).send(eq("reset@example.com"), org.mockito.ArgumentMatchers.anyString());

        requestReset("reset@example.com", 202);
        String firstToken = sentToken(1);
        String firstHash = jdbc.queryForObject(
                "select token_hash from password_reset_tokens where user_id = ? order by created_at desc limit 1",
                String.class, userId);
        assertEquals(AuthService.hash(firstToken), firstHash);
        assertNotEquals(firstToken, firstHash);
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from password_reset_tokens where token_hash = ?", Integer.class, firstToken));

        requestReset("reset@example.com", 202);
        verify(mailSender, times(1)).send(eq("reset@example.com"), org.mockito.ArgumentMatchers.anyString());
        assertEquals(1, tokenCount(userId));

        ageLatestToken(userId);
        requestReset("reset@example.com", 202);
        String secondToken = sentToken(2);
        assertEquals(2, tokenCount(userId));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from password_reset_tokens where user_id = ? and used_at is not null",
                Integer.class, userId));
        confirm(firstToken, "ignored-password", 400);

        login("reset@example.com", "old-password", 200);
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from refresh_tokens where user_id = ? and revoked = false", Integer.class, userId));

        confirm(secondToken, "new-password", 204);
        User changed = userRepository.findById(userId).orElseThrow();
        assertTrue(passwordEncoder.matches("new-password", changed.getPasswordHash()));
        assertFalse(passwordEncoder.matches("old-password", changed.getPasswordHash()));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from refresh_tokens where user_id = ? and revoked = false", Integer.class, userId));
        login("reset@example.com", "old-password", 401);
        login("reset@example.com", "new-password", 200);
        confirm(secondToken, "another-password", 400);

        ageLatestToken(userId);
        requestReset("reset@example.com", 202);
        String expiredToken = sentToken(3);
        jdbc.update("update password_reset_tokens set expires_at = now() - interval '1 second' where token_hash = ?",
                AuthService.hash(expiredToken));
        confirm(expiredToken, "another-password", 400);

        ageLatestToken(userId);
        requestReset("reset@example.com", 202);
        String concurrentToken = sentToken(4);
        List<Integer> results = confirmConcurrently(concurrentToken);
        assertEquals(List.of(204, 400), results.stream().sorted().toList());
    }

    private void requestReset(String email, int expectedStatus) throws Exception {
        mvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    private void confirm(String token, String password, int expectedStatus) throws Exception {
        var result = mvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetJson(token, password)))
                .andExpect(status().is(expectedStatus));
        if (expectedStatus == 400) {
            result.andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
        }
    }

    private void login(String email, String password, int expectedStatus) throws Exception {
        var result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().is(expectedStatus));
        if (expectedStatus == 401) {
            result.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
    }

    private String sentToken(int totalCalls) {
        var token = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mailSender, times(totalCalls)).send(eq("reset@example.com"), token.capture());
        return token.getAllValues().getLast();
    }

    private int tokenCount(UUID userId) {
        return jdbc.queryForObject("select count(*) from password_reset_tokens where user_id = ?",
                Integer.class, userId);
    }

    private void ageLatestToken(UUID userId) {
        jdbc.update("update password_reset_tokens set created_at = now() - interval '61 seconds' "
                + "where id = (select id from password_reset_tokens where user_id = ? order by created_at desc limit 1)",
                userId);
    }

    private List<Integer> confirmConcurrently(String token) throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> confirmAfter(start, token));
            Future<Integer> second = executor.submit(() -> confirmAfter(start, token));
            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
    }

    private int confirmAfter(CyclicBarrier start, String token) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        return mvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetJson(token, "concurrent-password")))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private static String resetJson(String token, String password) {
        return "{\"token\":\"" + token + "\",\"newPassword\":\"" + password + "\"}";
    }
}
