package com.hangeoreum.api.vocabulary.application;

import com.hangeoreum.api.gamification.application.GrantXpService;
import com.hangeoreum.api.gamification.domain.XpSource;
import com.hangeoreum.api.learning.application.LearningService;
import com.hangeoreum.api.shared.events.OutboxDispatcher;
import com.hangeoreum.api.shared.events.contract.LessonCompleted;
import com.hangeoreum.api.shared.web.ApiException;
import com.hangeoreum.api.vocabulary.domain.ReviewMode;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class QuotaReviewIntegrationTest {
    @Container static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.baseline-on-migrate", () -> false);
        registry.add("spring.docker.compose.enabled", () -> false);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ReviewService reviews;
    @Autowired LearningService learning;
    @Autowired GrantXpService xp;
    @Autowired ObjectMapper mapper;
    @MockitoBean OutboxDispatcher dispatcher;

    @Test
    void oneRemainingLessonIsReservedAtomicallyAndReceiptReplayAndXpDoNotReserveAgain() throws Exception {
        UUID user = user(jdbc);
        UUID lesson = publishedLesson(jdbc);
        UUID firstAttempt = UUID.randomUUID();
        UUID secondAttempt = UUID.randomUUID();
        assertEquals(List.of("LIMIT_REACHED", "OK"), race(
                () -> learning.completeLesson(user, lesson, firstAttempt, (short) 100, (short) 100),
                () -> learning.completeLesson(user, lesson, secondAttempt, (short) 100, (short) 100)));
        UUID accepted = jdbc.queryForObject("select id from lesson_attempts where user_id = ?", UUID.class, user);
        assertEquals(accepted, learning.completeLesson(user, lesson, accepted, (short) 100, (short) 100).attemptId());
        UUID grantKey = UUID.randomUUID();
        xp.grant(user, 10, XpSource.LESSON, lesson, grantKey);
        xp.grant(user, 10, XpSource.LESSON, lesson, grantKey);
        assertEquals(1, jdbc.queryForObject("select lesson_reservations from daily_activity where user_id = ?",
                Integer.class, user));
        assertEquals(1, jdbc.queryForObject("select lessons_completed from daily_activity where user_id = ?",
                Integer.class, user));
        assertEquals(1, jdbc.queryForObject("select count(*) from lesson_attempts where user_id = ?", Integer.class, user));
    }

    @Test
    void oneRemainingGameIsReservedAtomicallyAndEmptyQueueDoesNotConsumeQuota() throws Exception {
        UUID user = user(jdbc);
        word(jdbc, user);
        jdbc.update("insert into daily_activity(user_id, activity_date, game_sessions_started, reviews_done) "
                + "values (?, current_date, 2, 10)", user);
        assertEquals(List.of("LIMIT_REACHED", "OK"), race(
                () -> reviews.startSession(user, ReviewMode.MATCH, 1, null, false),
                () -> reviews.startSession(user, ReviewMode.MATCH, 1, null, false)));
        assertEquals(3, jdbc.queryForObject("select game_sessions_started from daily_activity where user_id = ?",
                Integer.class, user));
        assertEquals(10, jdbc.queryForObject("select reviews_done from daily_activity where user_id = ?", Integer.class, user));
        UUID emptyUser = user(jdbc);
        assertThrows(ApiException.class, () -> reviews.startSession(emptyUser, ReviewMode.MATCH, 1, null, false));
        assertEquals(0, jdbc.queryForObject("select count(*) from daily_activity where user_id = ?", Integer.class, emptyUser));
    }

    @Test
    void onlyIssuedCardsCanBeAnsweredOnceAndAllMustBeAnsweredBeforeFinish() {
        UUID user = user(jdbc);
        UUID first = word(jdbc, user);
        UUID second = word(jdbc, user);
        var session = reviews.startSession(user, ReviewMode.QUICK, 1, null, false);
        UUID issued = session.cards().getFirst().word().id();
        UUID unissued = issued.equals(first) ? second : first;
        assertCode("CONFLICT", () -> reviews.submitAnswers(user, session.id(), List.of(
                new ReviewService.AnswerCommand(issued, 5), new ReviewService.AnswerCommand(unissued, 5))));
        assertEquals(0, jdbc.queryForObject("select count(*) from review_answers where session_id = ?", Integer.class, session.id()));
        assertCode("CONFLICT", () -> reviews.submitAnswers(user, session.id(), List.of(new ReviewService.AnswerCommand(unissued, 5))));
        assertCode("NOT_FOUND", () -> reviews.submitAnswers(user(jdbc), session.id(), List.of(new ReviewService.AnswerCommand(issued, 5))));
        assertCode("CONFLICT", () -> reviews.finishSession(user, session.id()));
        assertCode("VALIDATION", () -> reviews.submitAnswers(user, session.id(), List.of(new ReviewService.AnswerCommand(issued, 6))));
        assertCode("VALIDATION", () -> reviews.submitAnswers(user, session.id(), List.of(new ReviewService.AnswerCommand(null, 5))));
        reviews.submitAnswers(user, session.id(), List.of(new ReviewService.AnswerCommand(issued, 5)));
        assertCode("CONFLICT", () -> reviews.submitAnswers(user, session.id(), List.of(new ReviewService.AnswerCommand(issued, 5))));
        var finished = reviews.finishSession(user, session.id());
        assertEquals(1, finished.total());
        assertEquals(1, finished.xp());
        assertEquals(finished, reviews.finishSession(user, session.id()));
        assertCode("CONFLICT", () -> reviews.submitAnswers(user, session.id(), List.of(new ReviewService.AnswerCommand(issued, 5))));
        assertEquals(1, jdbc.queryForObject("select repetitions from user_words where user_id = ? and word_id = ?",
                Integer.class, user, issued));
        assertEquals(0, jdbc.queryForObject("select repetitions from user_words where user_id = ? and word_id = ?",
                Integer.class, user, unissued));
    }

    @Test
    void typedEventKeepsTheExistingWireValues() {
        var event = new LessonCompleted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 10, LessonCompleted.XpSource.STORY);
        String json = mapper.writeValueAsString(event);
        assertEquals("STORY", mapper.readTree(json).get("xpSource").asText());
        assertEquals(event, mapper.readValue(json, LessonCompleted.class));
        var node = new LearningService.LessonNode(UUID.randomUUID(), "Lesson", com.hangeoreum.api.learning.domain.LessonType.LESSON,
                (short) 0, (short) 10, true, true, com.hangeoreum.api.learning.domain.LessonAvailability.Status.AVAILABLE, null);
        assertEquals("AVAILABLE", mapper.readTree(mapper.writeValueAsString(node)).get("status").asText());
    }

    @Test
    void upgradeFromV13BackfillsAcceptedAttemptsAndGamesAndClosesUnverifiableLegacySessions() {
        String database = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("create database " + database);
        String url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + database);
        Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()).target("13").load().migrate();
        JdbcTemplate old = new JdbcTemplate(new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()));
        UUID user = user(old);
        UUID lesson = publishedLesson(old);
        old.update("insert into lesson_attempts(user_id, lesson_id, id, score, accuracy) values (?, ?, ?, 100, 100)",
                user, lesson, UUID.randomUUID());
        old.update("insert into daily_activity(user_id, activity_date, lessons_completed, reviews_done) values (?, current_date, 0, 9)", user);
        UUID open = UUID.randomUUID();
        UUID finished = UUID.randomUUID();
        old.update("insert into review_sessions(id, user_id, mode) values (?, ?, 'MATCH')", open, user);
        old.update("insert into review_sessions(id, user_id, mode, finished_at, xp_earned) values (?, ?, 'MATCH', now(), 4)", finished, user);
        Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()).target("14").load().migrate();
        assertEquals(1, old.queryForObject("select lesson_reservations from daily_activity where user_id = ?", Integer.class, user));
        assertEquals(2, old.queryForObject("select game_sessions_started from daily_activity where user_id = ?", Integer.class, user));
        assertEquals(9, old.queryForObject("select reviews_done from daily_activity where user_id = ?", Integer.class, user));
        assertEquals(true, old.queryForObject("select finished_at is not null from review_sessions where id = ?", Boolean.class, open));
        assertEquals(0, old.queryForObject("select xp_earned from review_sessions where id = ?", Integer.class, open));
        assertEquals(4, old.queryForObject("select xp_earned from review_sessions where id = ?", Integer.class, finished));
        assertEquals(0, old.queryForObject("select count(*) from review_session_cards", Integer.class));
    }

    private static UUID user(JdbcTemplate db) {
        UUID id = UUID.randomUUID();
        db.update("insert into users(id, name, email) values (?, 'Quota User', ?)", id, id + "@example.com");
        return id;
    }

    private static UUID publishedLesson(JdbcTemplate db) {
        UUID course = UUID.randomUUID(), unit = UUID.randomUUID(), lesson = UUID.randomUUID();
        db.update("insert into courses(id, title, is_published) values (?, 'Quota Course', true)", course);
        db.update("insert into units(id, course_id, position, title, is_published) values (?, ?, 1, 'Quota Unit', true)", unit, course);
        db.update("insert into lessons(id, unit_id, position, title, is_published, is_free) values (?, ?, 1, 'Quota Lesson', true, true)", lesson, unit);
        return lesson;
    }

    private static UUID word(JdbcTemplate db, UUID user) {
        UUID word = UUID.randomUUID();
        db.update("insert into words(id, hangul, romanization, translation) values (?, '안녕', 'annyeong', 'hello')", word);
        db.update("insert into user_words(user_id, word_id) values (?, ?)", user, word);
        return word;
    }

    private static void assertCode(String code, Runnable action) {
        assertEquals(code, assertThrows(ApiException.class, action::run).getCode());
    }

    private static List<String> race(Supplier<?> first, Supplier<?> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> attempt(first, ready, start));
            var b = executor.submit(() -> attempt(second, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)).stream().sorted().toList();
        } finally {
            start.countDown();
        }
    }

    private static String attempt(Supplier<?> action, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try { action.get(); return "OK"; }
        catch (ApiException exception) { return exception.getCode(); }
    }
}
