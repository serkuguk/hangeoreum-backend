package com.hangeoreum.api.admin.api;

import com.hangeoreum.api.identity.application.AuthService;
import com.hangeoreum.api.identity.domain.User;
import com.hangeoreum.api.identity.domain.UserRole;
import com.hangeoreum.api.identity.infrastructure.PasswordResetMailSender;
import com.hangeoreum.api.identity.infrastructure.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminUserIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.docker.compose.enabled", () -> false);
    }

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PasswordResetMailSender mailSender;

    @Test
    void adminUserLifecycleEnforcesAccessAndSubscriptionGuard() throws Exception {
        User admin = User.register("Admin", "admin-user-test@example.com", "hash");
        admin.changeRole(UserRole.ADMIN);
        admin = users.saveAndFlush(admin);
        User target = users.saveAndFlush(User.register("Target", "target-user-test@example.com", "hash"));
        String adminToken = auth.issueTokens(admin).accessToken();
        AuthService.TokenPair targetTokens = auth.issueTokens(target);
        String url = "/api/v1/admin/users/" + target.getId();

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("insert into users (name, email, role) values ('Second Admin', 'second-admin@example.com', 'ADMIN')"));
        mvc.perform(patch(url).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(url).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"EDITOR\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("EDITOR"));
        mvc.perform(get("/api/v1/admin/words").header("Authorization", bearer(targetTokens.accessToken())))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/notifications/broadcast")
                        .header("Authorization", bearer(targetTokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Editor notice\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/metrics").header("Authorization", bearer(targetTokens.accessToken())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(targetTokens.accessToken())))
                .andExpect(status().isForbidden());
        mvc.perform(patch(url).header("Authorization", bearer(targetTokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(url).header("Authorization", bearer(targetTokens.accessToken())))
                .andExpect(status().isForbidden());
        mvc.perform(patch(url).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("USER"));
        mvc.perform(get("/api/v1/admin/words").header("Authorization", bearer(targetTokens.accessToken())))
                .andExpect(status().isForbidden());

        mvc.perform(get(url).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.isActive").value(true));
        mvc.perform(delete(url).header("Authorization", bearer(targetTokens.accessToken())))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/admin/users/" + admin.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"isActive\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/admin/users/" + admin.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/admin/users/" + admin.getId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden());

        mvc.perform(patch(url).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"isActive\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isActive").value(false));
        mvc.perform(get("/api/v1/me").header("Authorization", bearer(targetTokens.accessToken())))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", targetTokens.refreshToken())))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch(url).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"isActive\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isActive").value(true));

        UUID subscriptionId = UUID.randomUUID();
        UUID planId = jdbc.queryForObject("select id from plans where code = 'pro_month'", UUID.class);
        jdbc.update("insert into subscriptions (id, user_id, plan_id, provider, provider_sub_id) "
                + "values (?, ?, ?, 'STRIPE', 'sub_admin_user_test')", subscriptionId, target.getId(), planId);
        mvc.perform(delete(url).header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());
        jdbc.update("update subscriptions set status = 'CANCELED' where id = ?", subscriptionId);
        UUID paymentId = UUID.randomUUID();
        jdbc.update("insert into payments (id, user_id, subscription_id, provider, amount_cents) values (?, ?, ?, 'STRIPE', 100)",
                paymentId, target.getId(), subscriptionId);
        mvc.perform(delete(url).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
        assertFalse(users.existsById(target.getId()));
        assertEquals(1, jdbc.queryForObject("select count(*) from payments where id = ? and user_id is null and subscription_id = ?",
                Integer.class, paymentId, subscriptionId));
        assertEquals(1, jdbc.queryForObject("select count(*) from subscriptions where id = ? and user_id is null",
                Integer.class, subscriptionId));
        assertEquals(0, jdbc.queryForObject("select count(*) from payments where user_id = ?", Integer.class, target.getId()));
        mvc.perform(delete(url).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
