package com.hangeoreum.api.learning.application;

import com.hangeoreum.api.learning.infrastructure.LessonAttemptRepository;
import com.hangeoreum.api.shared.events.OutboxDispatcher;
import com.hangeoreum.api.shared.events.OutboxEvent;
import com.hangeoreum.api.shared.events.OutboxEventRepository;
import com.hangeoreum.api.shared.events.contract.LessonWordsAdded;
import com.hangeoreum.api.shared.events.contract.XpGranted;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@Testcontainers
class LessonReceiptIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.baseline-on-migrate", () -> false);
        registry.add("spring.docker.compose.enabled", () -> false);
    }

    @Autowired LessonWordsAddedHandler wordsHandler;
    @Autowired XpGrantedHandler xpHandler;
    @Autowired LessonAttemptRepository attempts;
    @Autowired OutboxEventRepository events;
    @Autowired LearningService learning;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean ObjectMapper objectMapper;
    @MockitoBean OutboxDispatcher dispatcher;

    @Test
    void concurrentHandlersPreserveBothReceiptDetailsAndIgnoreReplay() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        jdbc.update("insert into users (id, name, email) values (?, 'Receipt User', ?)", userId, userId + "@example.com");
        jdbc.update("insert into courses (id, title) values (?, 'Receipt Course')", courseId);
        jdbc.update("insert into units (id, course_id, position, title) values (?, ?, 1, 'Receipt Unit')", unitId, courseId);
        jdbc.update("insert into lessons (id, unit_id, position, title) values (?, ?, 1, 'Receipt Lesson')", lessonId, unitId);
        jdbc.update("insert into lesson_attempts (id, user_id, lesson_id, score, accuracy) values (?, ?, ?, 100, 100)",
                attemptId, userId, lessonId);

        var words = List.of(new LessonWordsAdded.Word(UUID.randomUUID(), "안녕", "annyeong", "hello"));
        String wordsPayload = objectMapper.writeValueAsString(new LessonWordsAdded(attemptId, words));
        String xpPayload = objectMapper.writeValueAsString(new XpGranted(attemptId, 25, 3, true));
        UUID wordsEventId = events.saveAndFlush(OutboxEvent.pending(UUID.randomUUID(), LessonWordsAdded.TYPE, wordsPayload)).getId();
        UUID xpEventId = events.saveAndFlush(OutboxEvent.pending(UUID.randomUUID(), XpGranted.TYPE, xpPayload)).getId();

        CountDownLatch wordsRead = new CountDownLatch(1);
        CountDownLatch releaseWords = new CountDownLatch(1);
        doAnswer(invocation -> {
            // Pause after the first receipt read so the other handler must wait on its database lock.
            wordsRead.countDown();
            assertTrue(releaseWords.await(10, TimeUnit.SECONDS));
            return invocation.callRealMethod();
        }).when(objectMapper).writeValueAsString(eq(words));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var wordsTask = executor.submit(() -> wordsHandler.handle(wordsEventId, wordsPayload));
            try {
                assertTrue(wordsRead.await(5, TimeUnit.SECONDS));
                var xpTask = executor.submit(() -> xpHandler.handle(xpEventId, xpPayload));
                assertTrue(awaitReceiptLock(), "XP handler must wait until the words transaction commits");
                releaseWords.countDown();
                xpTask.get(5, TimeUnit.SECONDS);
            } finally {
                releaseWords.countDown();
            }
            wordsTask.get(5, TimeUnit.SECONDS);
        }

        var receipt = attempts.findById(attemptId).orElseThrow();
        assertTrue(receipt.isCompleted());
        assertEquals(25, receipt.getXp());
        assertEquals(3, receipt.getStreak());
        assertEquals(true, receipt.getGoalReached());
        var status = learning.completionStatus(userId, attemptId);
        assertEquals("COMPLETED", status.status());
        assertEquals(words, status.result().newWords());
        assertEquals(25, status.result().xp());

        wordsHandler.handle(wordsEventId, objectMapper.writeValueAsString(new LessonWordsAdded(attemptId, List.of())));
        xpHandler.handle(xpEventId, objectMapper.writeValueAsString(new XpGranted(attemptId, 999, 999, false)));
        assertEquals(status, learning.completionStatus(userId, attemptId));
        assertEquals(2, jdbc.queryForObject("select count(*) from processed_events where event_id in (?, ?)",
                Integer.class, wordsEventId, xpEventId));
    }

    private boolean awaitReceiptLock() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from pg_stat_activity "
                    + "where pid <> pg_backend_pid() and wait_event_type = 'Lock' and query like '%lesson_attempts%')",
                    Boolean.class))) return true;
            Thread.sleep(20);
        }
        return false;
    }
}
