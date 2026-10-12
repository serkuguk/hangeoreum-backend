package com.hangeoreum.api.identity.application;

import com.hangeoreum.api.identity.domain.User;
import com.hangeoreum.api.identity.infrastructure.PasswordResetMailSender;
import com.hangeoreum.api.identity.infrastructure.UserRepository;
import com.hangeoreum.api.shared.security.JwtService;
import com.hangeoreum.api.shared.storage.MediaStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AvatarIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
    @TempDir static Path directory;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.docker.compose.enabled", () -> false);
        registry.add("app.storage.dir", () -> directory.toString());
    }

    @Autowired MockMvc mvc;
    @Autowired MeService service;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean PasswordResetMailSender mailSender;
    @MockitoSpyBean MediaStorage storage;

    @Test
    void uploadsRequireAuthenticationAndRejectActiveOrForgedContent() throws Exception {
        User user = users.saveAndFlush(User.register("Avatar", "avatar-http@example.com", "hash"));
        String token = jwt.createAccessToken(user.getId(), user.getRole().name());
        mvc.perform(multipart("/api/v1/me/avatar").file(png())).andExpect(status().isUnauthorized());
        for (var file : new MockMultipartFile[]{
                new MockMultipartFile("file", "a.png", "image/png", "<html>active</html>".getBytes()),
                new MockMultipartFile("file", "a.svg", "image/svg+xml", "<svg/>".getBytes()),
                new MockMultipartFile("file", "a.html", "image/png", png().getBytes()),
                new MockMultipartFile("file", "a.png", "image/png", new byte[5 * 1024 * 1024 + 1])}) {
            mvc.perform(multipart("/api/v1/me/avatar").file(file).header("Authorization", "Bearer " + token))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION"));
        }
        mvc.perform(multipart("/api/v1/me/avatar").file(png()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.avatarUrl").exists());
        String url = users.findById(user.getId()).orElseThrow().getAvatarUrl();
        mvc.perform(get(url)).andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        user = users.findById(user.getId()).orElseThrow();
        user.setActive(false);
        users.saveAndFlush(user);
        mvc.perform(multipart("/api/v1/me/avatar").file(png()).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rollbackDeletesNewFileAndCommitDeletesPreviousAvatar() throws Exception {
        User user = users.saveAndFlush(User.register("Rollback", "avatar-rollback@example.com", "hash"));
        String first = service.uploadAvatar(user.getId(), png());
        String[] rolledBack = new String[1];
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            rolledBack[0] = service.uploadAvatar(user.getId(), png());
            assertTrue(Files.exists(path(first)));
            assertTrue(Files.exists(path(rolledBack[0])));
            status.setRollbackOnly();
        });
        assertEquals(first, users.findById(user.getId()).orElseThrow().getAvatarUrl());
        assertTrue(Files.exists(path(first)));
        assertFalse(Files.exists(path(rolledBack[0])));
        String[] failedCommit = new String[1];
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                    failedCommit[0] = service.uploadAvatar(user.getId(), png());
                    users.findById(user.getId()).orElseThrow().rename(null);
                }));
        assertEquals(first, users.findById(user.getId()).orElseThrow().getAvatarUrl());
        assertTrue(Files.exists(path(first)));
        assertFalse(Files.exists(path(failedCommit[0])));
        String committed = service.uploadAvatar(user.getId(), png());
        assertFalse(Files.exists(path(first)));
        assertTrue(Files.exists(path(committed)));
        assertEquals(committed, users.findById(user.getId()).orElseThrow().getAvatarUrl());
        doThrow(new IllegalStateException("Disk cleanup failure")).when(storage).deleteAvatar(committed);
        String token = jwt.createAccessToken(user.getId(), user.getRole().name());
        mvc.perform(multipart("/api/v1/me/avatar").file(png()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.avatarUrl").exists());
        String replacement = users.findById(user.getId()).orElseThrow().getAvatarUrl();
        assertNotEquals(committed, replacement);
        assertTrue(Files.exists(path(replacement)));
        assertTrue(Files.exists(path(committed)));
        verify(storage).deleteAvatar(committed);
        try (var files = Files.list(directory.resolve("avatars"))) {
            long before = files.count();
            assertThrows(com.hangeoreum.api.shared.web.ApiException.class,
                    () -> service.uploadAvatar(UUID.randomUUID(), png()));
            try (var after = Files.list(directory.resolve("avatars"))) {
                assertEquals(before, after.count());
            }
        }
    }

    private static Path path(String url) {
        return directory.resolve(url.substring("/media/".length()));
    }

    private static MockMultipartFile png() {
        try {
            var bytes = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
            return new MockMultipartFile("file", "avatar.png", "image/png", bytes.toByteArray());
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
