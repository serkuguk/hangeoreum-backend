package com.hangeoreum.api;

import com.hangeoreum.api.billing.application.AccessService;
import com.hangeoreum.api.billing.domain.*;
import com.hangeoreum.api.billing.infrastructure.SubscriptionRepository;
import com.hangeoreum.api.identity.application.AuthService;
import com.hangeoreum.api.identity.domain.User;
import com.hangeoreum.api.identity.infrastructure.PasswordResetMailSender;
import com.hangeoreum.api.identity.infrastructure.UserRepository;
import com.hangeoreum.api.media.application.MediaService;
import com.hangeoreum.api.vocabulary.application.VocabularyService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@Testcontainers
class AuditInvariantsPerformanceIntegrationTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
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
    @Autowired AccessService access;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired MediaService media;
    @Autowired VocabularyService vocabulary;
    @Autowired EntityManagerFactory emf;
    @MockitoBean PasswordResetMailSender mail;
    @MockitoBean com.hangeoreum.api.shared.events.OutboxDispatcher outbox;
    @MockitoBean com.hangeoreum.api.notification.application.NotificationJobs jobs;

    @Test
    void invalidPaginationAndReviewLimitsReturn400AndKeepSecurity() throws Exception {
        User user = newUser();
        String token = "Bearer " + auth.issueTokens(user).accessToken();
        for (String path : List.of("/api/v1/vocabulary", "/api/v1/notifications")) {
            mvc.perform(get(path).header("Authorization", token).param("page", "-1")).andExpect(status().isBadRequest());
            for (String size : List.of("0", "-1", "101")) {
                mvc.perform(get(path).header("Authorization", token).param("size", size)).andExpect(status().isBadRequest());
            }
            mvc.perform(get(path).header("Authorization", token).param("size", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.content").isArray());
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        for (String size : List.of("0", "-1", "31")) {
            mvc.perform(get("/api/v1/immerse/feed").header("Authorization", token).param("size", size))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/immerse/feed").header("Authorization", token)).andExpect(status().isForbidden());
        for (int limit : new int[]{0, -1, 101}) {
            mvc.perform(post("/api/v1/review/sessions").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"mode\":\"QUICK\",\"limit\":" + limit + "}"))
                    .andExpect(status().isBadRequest());
        }
        jdbc.update("update users set role = 'ADMIN' where id = ?", user.getId());
        for (String path : List.of("/api/v1/admin/users", "/api/v1/admin/words")) {
            mvc.perform(get(path).header("Authorization", token).param("page", "-1")).andExpect(status().isBadRequest());
            mvc.perform(get(path).header("Authorization", token).param("size", "101")).andExpect(status().isBadRequest());
            mvc.perform(get(path).header("Authorization", token).param("size", "0")).andExpect(status().isBadRequest());
        }
    }

    @Test
    void anyActiveSubscriptionWinsAndRepositoryMatchesDomainDeadlines() {
        User user = newUser();
        UUID plan = jdbc.queryForObject("select id from plans where code = 'pro_month'", UUID.class);
        Instant now = Instant.parse("2026-10-12T12:00:00Z");
        Subscription valid = Subscription.activate(user.getId(), plan, PayProvider.STRIPE, UUID.randomUUID().toString(), null);
        subscriptions.saveAndFlush(valid);
        Subscription canceled = Subscription.activate(user.getId(), plan, PayProvider.STRIPE, UUID.randomUUID().toString(), now.plusSeconds(3600));
        canceled.cancel();
        subscriptions.saveAndFlush(canceled);
        assertTrue(access.isPro(user.getId()));
        for (SubStatus status : SubStatus.values()) {
            for (Instant end : new Instant[]{null, now, now.plusSeconds(1), now.minus(Subscription.PAST_DUE_GRACE), now.minus(Subscription.PAST_DUE_GRACE).plusSeconds(1)}) {
                Subscription sample = Subscription.activate(user.getId(), plan, PayProvider.STRIPE, UUID.randomUUID().toString(), end);
                switch (status) {
                    case ACTIVE -> {}
                    case PAST_DUE -> sample.markPastDue();
                    case CANCELED -> sample.cancel();
                    case EXPIRED -> sample.expire();
                }
                jdbc.update("delete from subscriptions where user_id = ?", user.getId());
                subscriptions.saveAndFlush(sample);
                assertEquals(sample.isActive(now), subscriptions.hasActiveSubscription(user.getId(), now), status + " " + end);
            }
        }
    }

    @Test
    void immerseSqlCountIsConstantAndPreservesSubtitleOrderAndEmptyClips() {
        UUID userId = newUser().getId();
        UUID first = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            UUID clip = i == 0 ? first : UUID.randomUUID();
            jdbc.update("insert into media_clips(id,kind,video_url,is_published,created_at) values (?, 'IMMERSE', '/test', true, ?)",
                    clip, java.sql.Timestamp.from(Instant.parse("2026-10-12T12:00:00Z").minusSeconds(i)));
        }
        jdbc.update("insert into subtitles(clip_id,lang,position,text,start_ms,end_ms) values (?, 'ko', 2, 'second', 1, 2), (?, 'ko', 1, 'first', 0, 1)", first, first);
        var statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        media.getImmerseFeed(userId, null, 1);
        long oneClip = statistics.getPrepareStatementCount();
        statistics.clear();
        var feed = media.getImmerseFeed(userId, null, 5);
        assertEquals(oneClip, statistics.getPrepareStatementCount());
        assertEquals(3, oneClip);
        assertEquals(5, feed.content().size());
        assertEquals(first, feed.content().getFirst().id());
        assertEquals(List.of("first", "second"), feed.content().getFirst().subtitles().stream().map(MediaService.SubtitleDto::text).toList());
        assertTrue(feed.content().get(1).subtitles().isEmpty());
    }

    @Test
    void deckSqlCountIsConstantAndIncludesZeroCounts() {
        UUID userId = newUser().getId();
        UUID first = vocabulary.createDeck(userId, "First").id();
        var statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        vocabulary.getDecks(userId);
        long oneDeck = statistics.getPrepareStatementCount();
        UUID word = UUID.randomUUID();
        jdbc.update("insert into words(id,hangul,romanization,translation) values (?, '단어', 'daneo', 'word')", word);
        jdbc.update("insert into deck_words(deck_id,word_id) values (?, ?)", first, word);
        for (int i = 0; i < 4; i++) vocabulary.createDeck(userId, "Empty " + i);
        statistics.clear();
        var decks = vocabulary.getDecks(userId);
        assertEquals(oneDeck, statistics.getPrepareStatementCount());
        assertEquals(2, oneDeck);
        assertEquals(5, decks.size());
        assertEquals(first, decks.getFirst().id());
        assertEquals(1, decks.getFirst().wordCount());
        assertTrue(decks.stream().skip(1).allMatch(deck -> deck.wordCount() == 0));
    }

    private User newUser() {
        return users.saveAndFlush(User.register("Audit", UUID.randomUUID() + "@example.com", "hash"));
    }
}
