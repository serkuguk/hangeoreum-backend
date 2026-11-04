package com.hangeoreum.api.learning.application;

import com.hangeoreum.api.learning.domain.ExerciseKind;
import com.hangeoreum.api.learning.domain.LessonExerciseResult;
import com.hangeoreum.api.learning.domain.LessonSession;
import com.hangeoreum.api.learning.domain.LessonType;
import com.hangeoreum.api.learning.infrastructure.LessonExerciseResultRepository;
import com.hangeoreum.api.learning.infrastructure.LessonSessionRepository;
import com.hangeoreum.api.shared.events.OutboxDispatcher;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class LessonTrackingIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.baseline-on-migrate", () -> false);
        registry.add("spring.docker.compose.enabled", () -> false);
    }

    @Autowired LessonSessionRepository sessions;
    @Autowired LessonExerciseResultRepository results;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;
    @Autowired LearningService learning;
    @Autowired com.hangeoreum.api.learning.infrastructure.LessonAttemptRepository attempts;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean LessonSessionRepository trackingStore;
    @MockitoBean OutboxDispatcher dispatcher;

    @Test
    void httpStartsAreOwnedImmutableAndIndependentOfProgress() throws Exception {
        Fixture f = fixture();
        UUID sessionId = UUID.randomUUID();
        mvc.perform(post(f.startUrl()).contentType(MediaType.APPLICATION_JSON).content(start(sessionId)))
                .andExpect(status().isUnauthorized());
        String first = send(f.userId(), f.startUrl(), start(sessionId)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        jdbc.update("update lessons set title='Edited' where id=?", f.lessonId());
        jdbc.update("delete from exercises where lesson_id=?", f.lessonId());
        assertEquals(first, send(f.userId(), f.startUrl(), start(sessionId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        send(f.userId(), f.startUrl(), start(UUID.randomUUID())).andExpect(status().isConflict());
        assertEquals("Tracked lesson", sessions.findById(sessionId).orElseThrow().getLessonTitle());
        send(user(), f.startUrl(), start(sessionId)).andExpect(status().isConflict());
        UUID otherLesson = UUID.randomUUID();
        send(f.userId(), "/api/v1/lessons/" + otherLesson + "/sessions", start(sessionId)).andExpect(status().isConflict());
        assertEquals(0, count("select count(*) from lesson_progress where user_id=?", f.userId()));
        assertEquals(0, count("select count(*) from lesson_attempts where user_id=?", f.userId()));
        jdbc.update("update lessons set is_published=false where id=?", f.lessonId());
        send(f.userId(), f.startUrl(), start(sessionId)).andExpect(status().isNotFound());
    }

    @Test
    void resultsUseSnapshotHaveStableTimestampsAndRejectForeignOrChangedRetries() throws Exception {
        Fixture f = fixture(); UUID sessionId = UUID.randomUUID();
        send(f.userId(), f.startUrl(), start(sessionId)).andExpect(status().isCreated());
        String url = f.resultUrl(sessionId), body = answer(2, f.exerciseId(), false, "expected", null);
        String first = send(f.userId(), url, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Instant last = sessions.findById(sessionId).orElseThrow().getLastReceivedAt();
        jdbc.update("delete from exercises where id=?", f.exerciseId());
        assertEquals(first, send(f.userId(), url, body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(last, sessions.findById(sessionId).orElseThrow().getLastReceivedAt());
        send(f.userId(), url, answer(2, f.exerciseId(), true, "expected", null)).andExpect(status().isConflict());
        send(f.userId(), url, answer(1, f.exerciseId(), true, null, null)).andExpect(status().isCreated());
        send(f.userId(), url, answer(3, UUID.randomUUID(), true, null, null)).andExpect(status().isNotFound());
        send(user(), url, body).andExpect(status().isNotFound());
        send(f.userId(), f.resultUrl(UUID.randomUUID()), body).andExpect(status().isNotFound());
        send(f.userId(), url.replace(f.lessonId().toString(), UUID.randomUUID().toString()), body).andExpect(status().isNotFound());
        jdbc.update("update lessons set is_free=false where id=?", f.lessonId());
        send(f.userId(), url, body).andExpect(status().isForbidden());
        assertEquals(2, count("select count(*) from lesson_exercise_results where session_id=?", sessionId));
    }

    @Test
    void httpValidationBoundsRawBytesUnknownFieldsAndRequiredValues() throws Exception {
        Fixture f = fixture(); UUID sessionId = UUID.randomUUID();
        for (String body : List.of("{}", "null", "{", "{\"sessionId\":\"wrong\"}",
                "{\"sessionId\":null}", start(sessionId).replace("}", ",\"userId\":\"" + f.userId() + "\"}"),
                start(sessionId) + "{}", start(sessionId) + " ".repeat(8192))) {
            send(f.userId(), f.startUrl(), body).andExpect(status().isBadRequest());
        }
        send(f.userId(), "/api/v1/lessons/wrong/sessions", start(sessionId)).andExpect(status().isBadRequest());
        send(f.userId(), f.startUrl(), start(sessionId)).andExpect(status().isCreated());
        String url = f.resultUrl(sessionId);
        for (String body : List.of("{}", "null", answer(0, f.exerciseId(), true, null, null),
                answer(10001, f.exerciseId(), true, null, null), answer(1, f.exerciseId(), true, "x".repeat(501), null),
                answer(1, f.exerciseId(), true, null, -1), answer(1, f.exerciseId(), true, null, 1000001),
                answer(1, f.exerciseId(), true, null, 0),
                answer(1, f.exerciseId(), true, null, null).replace("true", "null"),
                answer(1, f.exerciseId(), true, null, null).replace("true", "\"true\""),
                answer(1, f.exerciseId(), true, null, null).replace("\"sequence\":1", "\"sequence\":\"1\""),
                answer(1, f.exerciseId(), true, null, null).replace("\"sequence\":1", "\"sequence\":1.5"),
                answer(1, f.exerciseId(), true, null, null).replace("true", "{}"))) {
            send(f.userId(), url, body).andExpect(status().isBadRequest());
        }
        send(f.userId(), url.replace(sessionId.toString(), "bad"), answer(1, f.exerciseId(), true, null, null))
                .andExpect(status().isBadRequest());
        byte[] oversized = (answer(1, f.exerciseId(), true, null, null) + " ".repeat(8192)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        mvc.perform(post(url).with(jwt().jwt(token -> token.subject(f.userId().toString())))
                        .contentType(MediaType.APPLICATION_JSON).content(oversized)
                        .with(request -> { request.removeHeader("Content-Length"); request.addHeader("Transfer-Encoding", "chunked"); return request; }))
                .andExpect(status().isBadRequest());
        send(f.userId(), url, answer(10000, f.exerciseId(), true, "x".repeat(500), null)).andExpect(status().isCreated());
    }

    @Test
    void snapshotBoundsAndMatchMistakesAreEnforced() throws Exception {
        Fixture f = fixture();
        jdbc.update("update exercises set payload=cast(? as jsonb) where id=?", "{\"question\":\"" + "x".repeat(501) + "\"}", f.exerciseId());
        send(f.userId(), f.startUrl(), start(UUID.randomUUID())).andExpect(status().isBadRequest());
        jdbc.update("update exercises set kind='MATCH_PAIRS',payload='{\"pairs\":[{\"left\":\"안녕\",\"right\":\"hello\"}]}' where id=?", f.exerciseId());
        UUID sessionId = UUID.randomUUID();
        send(f.userId(), f.startUrl(), start(sessionId)).andExpect(status().isCreated());
        send(f.userId(), f.resultUrl(sessionId), answer(1, f.exerciseId(), true, null, 1000000)).andExpect(status().isCreated());
        assertEquals(1000000, results.findBySessionIdAndSequence(sessionId, 1).orElseThrow().getMistakes());
        jdbc.update("insert into exercises(lesson_id,position,kind,payload) select ?,n,'CHOICE','{}' from generate_series(1,500) n", f.lessonId());
        send(f.userId(), f.startUrl(), start(UUID.randomUUID())).andExpect(status().isBadRequest());
        jdbc.update("delete from exercises where lesson_id=? and position=500", f.lessonId());
        send(f.userId(), f.startUrl(), start(UUID.randomUUID())).andExpect(status().isCreated());
    }

    @Test
    void concurrentHttpRetriesAreIdempotentAndConflictsDoNotBecome500() throws Exception {
        Fixture f = fixture(); UUID sessionId = UUID.randomUUID();
        var starts = concurrent(() -> send(f.userId(), f.startUrl(), start(sessionId)).andReturn().getResponse(),
                () -> send(f.userId(), f.startUrl(), start(sessionId)).andReturn().getResponse());
        assertEquals(List.of(200, 201), starts.stream().map(org.springframework.mock.web.MockHttpServletResponse::getStatus).sorted().toList());
        assertEquals(starts.getFirst().getContentAsString(), starts.getLast().getContentAsString());
        String body = answer(1, f.exerciseId(), true, null, null);
        var same = concurrent(() -> send(f.userId(), f.resultUrl(sessionId), body).andReturn().getResponse(),
                () -> send(f.userId(), f.resultUrl(sessionId), body).andReturn().getResponse());
        assertEquals(List.of(200, 201), same.stream().map(org.springframework.mock.web.MockHttpServletResponse::getStatus).sorted().toList());
        assertEquals(same.getFirst().getContentAsString(), same.getLast().getContentAsString());
        var conflict = concurrent(() -> send(f.userId(), f.resultUrl(sessionId), answer(2, f.exerciseId(), true, null, null)).andReturn().getResponse(),
                () -> send(f.userId(), f.resultUrl(sessionId), answer(2, f.exerciseId(), false, null, null)).andReturn().getResponse());
        assertEquals(List.of(201, 409), conflict.stream().map(org.springframework.mock.web.MockHttpServletResponse::getStatus).sorted().toList());
        assertEquals(2, count("select count(*) from lesson_exercise_results where session_id=?", sessionId));
    }

    @Test
    void completeAndStatusIgnoreMissingOrBrokenTrackingAndLateResultsHaveNoCompletionEffects() throws Exception {
        Fixture f = fixture(); UUID attemptId = UUID.randomUUID();
        String completeUrl = "/api/v1/lessons/" + f.lessonId() + "/complete";
        org.mockito.Mockito.doThrow(new IllegalStateException("tracking unavailable")).when(trackingStore).findById(org.mockito.ArgumentMatchers.any());
        try {
            send(f.userId(), completeUrl, "{\"attemptId\":\"" + attemptId + "\",\"score\":80,\"accuracy\":90}")
                    .andExpect(status().isAccepted());
            mvc.perform(get("/api/v1/lessons/attempts/" + attemptId).with(jwt().jwt(token -> token.subject(f.userId().toString()))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
        } finally { org.mockito.Mockito.reset(trackingStore); }
        send(f.userId(), f.startUrl(), start(attemptId)).andExpect(status().isCreated());
        UUID admin = user();
        jdbc.update("update users set role='ADMIN' where id=?", admin);
        String monitor = "/api/v1/admin/users/" + f.userId() + "/monitoring";
        mvc.perform(get(monitor + "/sessions/" + attemptId).with(jwt().jwt(token -> token.subject(admin.toString()))
                        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.session.receiptStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.session.receiptSavedAt").isEmpty());
        int outbox = count("select count(*) from outbox_events");
        String progress = jdbc.queryForObject("select row_to_json(p)::text from lesson_progress p where user_id=?", String.class, f.userId());
        String receipt = jdbc.queryForObject("select row_to_json(a)::text from lesson_attempts a where id=?", String.class, attemptId);
        send(f.userId(), f.resultUrl(attemptId), answer(4, f.exerciseId(), false, "x", null)).andExpect(status().isCreated());
        assertEquals(receipt, jdbc.queryForObject("select row_to_json(a)::text from lesson_attempts a where id=?", String.class, attemptId));
        learning.recordLessonWords(attemptId, List.of());
        learning.recordXpGranted(new com.hangeoreum.api.shared.events.contract.XpGranted(attemptId, 10, 1, false));
        String completedReceipt = jdbc.queryForObject("select row_to_json(a)::text from lesson_attempts a where id=?", String.class, attemptId);
        send(f.userId(), f.resultUrl(attemptId), answer(1, f.exerciseId(), true, null, null)).andExpect(status().isCreated());
        assertEquals(completedReceipt, jdbc.queryForObject("select row_to_json(a)::text from lesson_attempts a where id=?", String.class, attemptId));
        assertEquals(progress, jdbc.queryForObject("select row_to_json(p)::text from lesson_progress p where user_id=?", String.class, f.userId()));
        assertEquals(outbox, count("select count(*) from outbox_events"));
        assertEquals(0, count("select count(*) from xp_events where user_id=?", f.userId()));
        assertEquals(0, count("select count(*) from user_words where user_id=?", f.userId()));
        assertTrue(attempts.findById(attemptId).orElseThrow().isCompleted());
        mvc.perform(get(monitor + "/sessions/" + attemptId).with(jwt().jwt(token -> token.subject(admin.toString()))
                        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.session.receiptStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.session.receiptSavedAt").isNotEmpty()).andExpect(jsonPath("$.session.savedAnswers").value(2));
        mvc.perform(get(monitor + "/history").param("kind", "completions").with(jwt().jwt(token -> token.subject(admin.toString()))
                        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].status").value("COMPLETED"));
        UUID foreign = user(), foreignAttempt = UUID.randomUUID();
        jdbc.update("insert into lesson_attempts(id,user_id,lesson_id,score,accuracy) values (?,?,?,100,100)", foreignAttempt, foreign, f.lessonId());
        send(f.userId(), f.startUrl(), start(foreignAttempt)).andExpect(status().isConflict());
    }

    @Test
    void storyRequiresExistingStoryAndProAndHasNoExercises() throws Exception {
        Fixture f = fixture();
        jdbc.update("update lessons set type='STORY' where id=?", f.lessonId());
        send(f.userId(), f.startUrl(), start(UUID.randomUUID())).andExpect(status().isForbidden());
        jdbc.update("insert into subscriptions(user_id,plan_id,status,provider) select ?,id,'ACTIVE','STRIPE' from plans limit 1", f.userId());
        send(f.userId(), f.startUrl(), start(UUID.randomUUID())).andExpect(status().isNotFound());
        jdbc.update("insert into stories(lesson_id,title) values (?,'Story')", f.lessonId());
        UUID sessionId = UUID.randomUUID();
        send(f.userId(), f.startUrl(), start(sessionId)).andExpect(status().isCreated());
        assertTrue(sessions.findById(sessionId).orElseThrow().getExercises().isEmpty());
        send(f.userId(), f.resultUrl(sessionId), answer(1, f.exerciseId(), true, null, null)).andExpect(status().isNotFound());
    }

    private record Fixture(UUID userId, UUID lessonId, UUID exerciseId) {
        String startUrl() { return "/api/v1/lessons/" + lessonId + "/sessions"; }
        String resultUrl(UUID sessionId) { return startUrl() + "/" + sessionId + "/results"; }
    }

    private Fixture fixture() {
        jdbc.update("update courses set is_published=false");
        UUID userId = user(), courseId = UUID.randomUUID(), unitId = UUID.randomUUID(), lessonId = UUID.randomUUID(), exerciseId = UUID.randomUUID();
        jdbc.update("insert into courses(id,title,is_published) values (?,'Tracked course',true)", courseId);
        jdbc.update("insert into units(id,course_id,position,title,is_published) values (?,?,0,'Tracked unit',true)", unitId, courseId);
        jdbc.update("insert into lessons(id,unit_id,position,title,is_free,is_published) values (?,?,0,'Tracked lesson',true,true)", lessonId, unitId);
        jdbc.update("insert into exercises(id,lesson_id,position,kind,payload) values (?,?,0,'CHOICE','{\"question\":\"안녕\"}')", exerciseId, lessonId);
        return new Fixture(userId, lessonId, exerciseId);
    }

    private ResultActions send(UUID userId, String url, String body) throws Exception {
        return mvc.perform(post(url).with(jwt().jwt(token -> token.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String start(UUID sessionId) { return "{\"sessionId\":\"" + sessionId + "\"}"; }

    private String answer(int sequence, UUID exerciseId, boolean correct, String expected, Integer mistakes) {
        return mapper.writeValueAsString(new LearningService.ExerciseResultRequest(sequence, exerciseId, correct, expected, mistakes));
    }

    private <T> List<T> concurrent(java.util.concurrent.Callable<T> first, java.util.concurrent.Callable<T> second) throws Exception {
        var ready = new java.util.concurrent.CountDownLatch(2);
        var go = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { ready.countDown(); assertTrue(go.await(10, java.util.concurrent.TimeUnit.SECONDS)); return first.call(); });
            var b = executor.submit(() -> { ready.countDown(); assertTrue(go.await(10, java.util.concurrent.TimeUnit.SECONDS)); return second.call(); });
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)); go.countDown();
            return List.of(a.get(15, java.util.concurrent.TimeUnit.SECONDS), b.get(15, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    @Test
    void snapshotSurvivesContentDeletionAndOwnerDeletionCascades() {
        UUID userId = user();
        UUID courseId = UUID.randomUUID(), unitId = UUID.randomUUID(), lessonId = UUID.randomUUID();
        UUID exerciseId = UUID.randomUUID(), sessionId = UUID.randomUUID();
        jdbc.update("insert into courses(id,title) values (?, 'Original course')", courseId);
        jdbc.update("insert into units(id,course_id,position,title) values (?,?,0,'Unit')", unitId, courseId);
        jdbc.update("insert into lessons(id,unit_id,position,title) values (?,?,0,'Original lesson')", lessonId, unitId);
        jdbc.update("insert into exercises(id,lesson_id,position,kind,payload) values (?,?,0,'CHOICE','{}')", exerciseId, lessonId);
        Instant startedAt = Instant.parse("2026-11-03T12:00:00Z");
        var snapshot = new ArrayList<>(List.of(new LessonSession.ExerciseSnapshot(exerciseId, ExerciseKind.CHOICE, 0, "안녕 — greeting")));
        var session = LessonSession.start(sessionId, userId, lessonId, courseId, "Original lesson", LessonType.LESSON, snapshot, startedAt);
        snapshot.clear();
        assertEquals(1, session.getExercises().size());
        assertThrows(UnsupportedOperationException.class, () -> session.getExercises().clear());
        sessions.saveAndFlush(session);
        results.saveAndFlush(LessonExerciseResult.received(sessionId, 1, exerciseId, false, "안녕", null, startedAt.plusSeconds(1)));

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var stored = sessions.findByIdForUpdate(sessionId).orElseThrow();
            stored.recordReceivedAt(startedAt.plusSeconds(1));
            stored.recordReceivedAt(startedAt.minusSeconds(1));
        });
        jdbc.update("delete from courses where id = ?", courseId);
        var stored = sessions.findById(sessionId).orElseThrow();
        assertEquals("Original lesson", stored.getLessonTitle());
        assertEquals(session.getExercises(), stored.getExercises());
        assertEquals(startedAt, stored.getStartedAt());
        assertEquals(startedAt.plusSeconds(1), stored.getLastReceivedAt());
        assertEquals("안녕", results.findBySessionIdAndSequence(sessionId, 1).orElseThrow().getExpected());
        assertEquals(0, count("select count(*) from lesson_attempts where id = ?", sessionId));
        assertEquals(0, count("select count(*) from lesson_progress where user_id = ?", userId));

        jdbc.update("delete from users where id = ?", userId);
        assertFalse(sessions.existsById(sessionId));
        assertTrue(results.findBySessionIdAndSequence(sessionId, 1).isEmpty());
    }

    @Test
    void conflictSafeStartKeepsOriginalSnapshotAndResultsHaveUniqueSequences() {
        UUID userId = user(), sessionId = UUID.randomUUID(), exerciseId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID(), courseId = UUID.randomUUID();
        Instant receivedAt = Instant.parse("2026-11-03T13:00:00Z");
        String snapshot = mapper.writeValueAsString(List.of(new LessonSession.ExerciseSnapshot(exerciseId, ExerciseKind.MATCH_PAIRS, 0, "Pairs")));
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertEquals(1, sessions.claim(sessionId, userId, lessonId, courseId, "Lesson", "LESSON", snapshot, receivedAt));
            assertEquals(0, sessions.claim(sessionId, userId, lessonId, courseId, "Changed", "STORY", "[]", receivedAt.plusSeconds(1)));
        });
        assertEquals("Lesson", sessions.findById(sessionId).orElseThrow().getLessonTitle());
        assertEquals(ExerciseKind.MATCH_PAIRS, sessions.findById(sessionId).orElseThrow().getExercises().getFirst().kind());
        var later = results.saveAndFlush(LessonExerciseResult.received(sessionId, 2, exerciseId, true, null, 2, receivedAt.plusSeconds(2)));
        results.saveAndFlush(LessonExerciseResult.received(sessionId, 1, exerciseId, false, "Pair", 3, receivedAt.plusSeconds(1)));
        assertThrows(DataIntegrityViolationException.class, () -> results.saveAndFlush(
                LessonExerciseResult.received(sessionId, 2, exerciseId, false, null, 2, receivedAt.plusSeconds(3))));
        var original = results.findBySessionIdAndSequence(sessionId, 2).orElseThrow();
        assertEquals(later.getId(), original.getId());
        assertEquals(receivedAt.plusSeconds(2), original.getReceivedAt());
        assertTrue(original.matches(exerciseId, true, null, 2));
        assertFalse(original.matches(exerciseId, false, null, 2));
        assertEquals(2, count("select count(*) from lesson_exercise_results where session_id = ?", sessionId));

        UUID storyId = UUID.randomUUID();
        sessions.saveAndFlush(LessonSession.start(storyId, userId, UUID.randomUUID(), courseId, "Story", LessonType.STORY, List.of(), receivedAt));
        assertTrue(sessions.findById(storyId).orElseThrow().getExercises().isEmpty());
    }

    @Test
    void snapshotAndResultBoundsRejectOversizedDataAndOrphans() {
        UUID userId = user(), sessionId = UUID.randomUUID(), exerciseId = UUID.randomUUID();
        Instant receivedAt = Instant.parse("2026-11-03T14:00:00Z");
        var exercise = new LessonSession.ExerciseSnapshot(exerciseId, ExerciseKind.TYPE_WORD, 0, "Context");
        assertThrows(IllegalArgumentException.class, () -> new LessonSession.ExerciseSnapshot(exerciseId, ExerciseKind.TYPE_WORD, 0, "x".repeat(501)));
        assertThrows(IllegalArgumentException.class, () -> LessonSession.start(sessionId, userId, UUID.randomUUID(), UUID.randomUUID(),
                "Lesson", LessonType.LESSON, java.util.Collections.nCopies(501, exercise), receivedAt));
        assertThrows(IllegalArgumentException.class, () -> LessonExerciseResult.received(sessionId, 0, exerciseId, true, null, null, receivedAt));
        assertThrows(IllegalArgumentException.class, () -> LessonExerciseResult.received(sessionId, 10001, exerciseId, true, null, null, receivedAt));
        assertThrows(IllegalArgumentException.class, () -> LessonExerciseResult.received(sessionId, 1, exerciseId, true, "x".repeat(501), null, receivedAt));
        assertThrows(IllegalArgumentException.class, () -> LessonExerciseResult.received(sessionId, 1, exerciseId, true, null, -1, receivedAt));
        assertThrows(IllegalArgumentException.class, () -> LessonExerciseResult.received(sessionId, 1, exerciseId, true, null, 1000001, receivedAt));
        assertThrows(DataIntegrityViolationException.class, () -> results.saveAndFlush(
                LessonExerciseResult.received(sessionId, 1, exerciseId, true, null, null, receivedAt)));
        assertThrows(DataIntegrityViolationException.class, () -> sessions.saveAndFlush(
                LessonSession.start(sessionId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Lesson", LessonType.LESSON, List.of(), receivedAt)));
        sessions.saveAndFlush(LessonSession.start(sessionId, userId, UUID.randomUUID(), UUID.randomUUID(), "Lesson", LessonType.LESSON, List.of(exercise), receivedAt));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into lesson_exercise_results(session_id,sequence_number,exercise_id,correct,received_at) values (?,0,?,true,?)",
                sessionId, exerciseId, java.sql.Timestamp.from(receivedAt)));
        assertEquals(0, count("select count(*) from lesson_exercise_results where session_id = ?", sessionId));
    }

    @Test
    void migrationFromV16PreservesLegacyCompletionAndCreatesExpectedIndexes() {
        String schema = "tracking_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var previous = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).target("16").load();
        previous.migrate();
        String jdbcUrl = POSTGRES.getJdbcUrl();
        var legacy = new JdbcTemplate(new DriverManagerDataSource(jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?")
                + "currentSchema=" + schema, POSTGRES.getUsername(), POSTGRES.getPassword()));
        UUID userId = UUID.randomUUID(), courseId = UUID.randomUUID(), unitId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID(), attemptId = UUID.randomUUID();
        legacy.update("insert into users(id,name,email) values (?,'Legacy',?)", userId, userId + "@example.com");
        legacy.update("insert into courses(id,title) values (?,'Legacy')", courseId);
        legacy.update("insert into units(id,course_id,position,title) values (?,?,0,'Legacy')", unitId, courseId);
        legacy.update("insert into lessons(id,unit_id,position,title) values (?,?,0,'Legacy')", lessonId, unitId);
        legacy.update("insert into lesson_attempts(id,user_id,lesson_id,score,accuracy) values (?,?,?,75,80)", attemptId, userId, lessonId);
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).load().migrate();
        assertEquals(75, legacy.queryForObject("select score from " + schema + ".lesson_attempts where id=?", Integer.class, attemptId));
        assertEquals(0, legacy.queryForObject("select count(*) from " + schema + ".lesson_sessions", Integer.class));
        assertEquals(2, legacy.queryForObject("select count(*) from pg_indexes where schemaname=? and indexname in "
                + "('idx_lesson_sessions_user_received','idx_lesson_sessions_user_started')", Integer.class, schema));
        assertEquals(1, legacy.queryForObject("select count(*) from pg_indexes where schemaname=? and tablename='lesson_exercise_results' "
                + "and indexdef like 'CREATE UNIQUE INDEX%session_id, sequence_number%'", Integer.class, schema));
    }

    private UUID user() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users(id,name,email) values (?,'Tracking User',?)", id, id + "@example.com");
        return id;
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }
}
