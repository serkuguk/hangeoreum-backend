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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    void activeUserWithoutAnySubscriptionCanLoginReadOwnProfileAndRefreshButCannotAdminister() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        User user = registerVerified("Free User", email);
        assertEquals(0, jdbc.queryForObject("select count(*) from subscriptions where user_id = ?", Integer.class, user.getId()));
        var login = mvc.perform(post("/api/v1/auth/login").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.isActive").value(true))
                .andExpect(jsonPath("$.user.active").doesNotExist()).andReturn();
        String accessToken = new tools.jackson.databind.ObjectMapper().readTree(login.getResponse().getContentAsString())
                .get("accessToken").asText();
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value(email)).andExpect(jsonPath("$.isActive").value(true));
        Cookie refreshCookie = login.getResponse().getCookie("refresh_token");
        mvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.user.isActive").value(true));
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
        assertEquals(0, jdbc.queryForObject("select count(*) from subscriptions where user_id = ?", Integer.class, user.getId()));
    }

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

    @Test
    void replayOfRotatedTokenAfterGracePeriodRevokesEverySession() throws Exception {
        User user = newUser();
        String stolen = auth.issueTokens(user).refreshToken();
        String other = auth.issueTokens(user).refreshToken();
        refresh(stolen, 200);
        refresh(stolen, 401); // inside the grace period: rejected, other sessions untouched
        assertEquals(2, tokenCount(user.getId(), " and revoked = false"));
        jdbc.update("update refresh_tokens set revoked_at = now() - interval '2 minutes' where token_hash = ?",
                AuthService.hash(stolen));
        refresh(stolen, 401);
        assertEquals(0, tokenCount(user.getId(), " and revoked = false"));
        refresh(other, 401);
    }

    @Test
    void passwordChangeEndsOtherSessionsAndRepeatedWrongPasswordsLockTheAccount() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        User user = registerVerified("Lock User", email);
        String other = auth.issueTokens(user).refreshToken();
        var login = login(email, "password123").andExpect(status().isOk()).andReturn();
        String accessToken = new tools.jackson.databind.ObjectMapper()
                .readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
        var changed = mvc.perform(post("/api/v1/me/password").header("Authorization", "Bearer " + accessToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"current\":\"password123\",\"next\":\"password456\"}"))
                .andExpect(status().isNoContent()).andReturn();
        refresh(other, 401);
        refresh(changed.getResponse().getCookie("refresh_token").getValue(), 200);

        for (int i = 0; i < 10; i++) {
            login(email, "wrong-password").andExpect(status().isUnauthorized());
        }
        login(email, "password456").andExpect(status().isTooManyRequests());
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    @Test
    void registrationIsNeutralAndSignInWaitsForTheMailedLink() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String body = "{\"name\":\"New User\",\"email\":\"" + email + "\",\"password\":\"password123\"}";
        var token = org.mockito.ArgumentCaptor.forClass(String.class);
        mvc.perform(post("/api/v1/auth/register").contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted()).andExpect(cookie().doesNotExist("refresh_token"));
        org.mockito.Mockito.verify(mailSender, org.mockito.Mockito.timeout(5000))
                .sendEmailVerification(org.mockito.ArgumentMatchers.eq(email), token.capture());
        String firstLink = token.getValue();
        login(email, "password123").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        login(email, "wrong-password").andExpect(status().isUnauthorized());
        // the mailed link is signed with the JWT key but is not an access token
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + firstLink)).andExpect(status().isUnauthorized());

        // someone else registers the same unconfirmed address: same answer, older link dies
        mvc.perform(post("/api/v1/auth/register").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body.replace("password123", "password456")))
                .andExpect(status().isAccepted());
        org.mockito.Mockito.verify(mailSender, org.mockito.Mockito.timeout(5000).times(2))
                .sendEmailVerification(org.mockito.ArgumentMatchers.eq(email), token.capture());
        String secondLink = token.getValue();
        confirm(firstLink, 400);
        confirm("garbage", 400);
        confirm(secondLink, 204);
        confirm(secondLink, 204);
        login(email, "password123").andExpect(status().isUnauthorized());
        login(email, "password456").andExpect(status().isOk());

        // a confirmed address: registration still answers 202, changes nothing and sends nothing
        mvc.perform(post("/api/v1/auth/register").contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/auth/email-verification/resend").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted());
        Thread.sleep(300);
        org.mockito.Mockito.verify(mailSender, org.mockito.Mockito.times(2))
                .sendEmailVerification(org.mockito.ArgumentMatchers.eq(email), org.mockito.ArgumentMatchers.anyString());
        login(email, "password456").andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("select count(*) from users where email = ?", Integer.class, email));
    }

    private void confirm(String token, int expectedStatus) throws Exception {
        mvc.perform(post("/api/v1/auth/email-verification/confirm").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    private User registerVerified(String name, String email) {
        auth.register(name, email, "password123");
        jdbc.update("update users set email_verified_at = now() where email = ?", email);
        return users.findByEmail(email).orElseThrow();
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
