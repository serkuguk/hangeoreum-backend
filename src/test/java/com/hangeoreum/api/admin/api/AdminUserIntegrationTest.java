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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Autowired com.hangeoreum.api.learning.application.LearningQueryService learningQueries;
    @Autowired com.hangeoreum.api.vocabulary.application.VocabularyQueryService vocabularyQueries;
    @Autowired com.hangeoreum.api.gamification.application.ActivityQueryService activityQueries;
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

        mvc.perform(get("/api/v1/admin/metrics").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalUsers").isNumber());

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
        mvc.perform(get(url).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payments[0].id").value(paymentId.toString()))
                .andExpect(jsonPath("$.subscription").isNotEmpty()).andExpect(jsonPath("$.lessonsCompleted").isNumber())
                .andExpect(jsonPath("$.wordsInSrs").isNumber()).andExpect(jsonPath("$.totalXp").isNumber());
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

    @Test
    void movedAdminControllersPreserveEditorRoutesAndMissingLessonResponse() throws Exception {
        User editor = User.register("Editor", "boundary-" + UUID.randomUUID() + "@example.com", "hash");
        editor.changeRole(UserRole.EDITOR);
        editor = users.saveAndFlush(editor);
        String token = auth.issueTokens(editor).accessToken();
        for (String path : java.util.List.of("courses", "words", "speakers")) {
            mvc.perform(get("/api/v1/admin/" + path)).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/admin/" + path).header("Authorization", bearer(token)))
                    .andExpect(status().isOk());
        }
        String missingLetter = "/api/v1/admin/alphabet/" + UUID.randomUUID();
        mvc.perform(put(missingLetter).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(missingLetter).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/lessons/" + UUID.randomUUID() + "/full")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/admin/words?page=-1").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/words?size=0").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/words?size=101").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
    }


    @Test
    @org.springframework.transaction.annotation.Transactional
    void ownerQueriesKeepPublishedCourseAlphabetAndLegacyProgressSeparate() throws Exception {
        UUID user = monitoringUser(), course = UUID.randomUUID(), unit = UUID.randomUUID(), emptyUnit = UUID.randomUUID();
        jdbc.update("insert into courses(id,title,is_published,created_at) values (?, 'Monitor course', true, '1970-01-01')", course);
        assertEquals(0, learningQueries.monitoringCourse(user).total());
        org.junit.jupiter.api.Assertions.assertNull(learningQueries.monitoringCourse(user).percent());
        jdbc.update("insert into units(id,course_id,position,title,is_published) values (?, ?, 0, 'Unit', true), (?, ?, 1, 'Empty unit', true)", unit, course, emptyUnit, course);
        UUID complete = UUID.randomUUID(), hidden = UUID.randomUUID();
        jdbc.update("insert into lessons(id,unit_id,position,title,is_published) values (?, ?, 0, 'Completed', true), (?, ?, 1, 'Hidden legacy', false)", complete, unit, hidden, unit);
        jdbc.update("insert into lesson_progress(user_id,lesson_id,status,score,accuracy,attempts) values (?, ?, 'COMPLETED', 80, 70, 4), (?, ?, 'COMPLETED', 90, 90, 2)", user, complete, user, hidden);
        UUID letter = UUID.randomUUID();
        jdbc.update("insert into alphabet_letters(id,jamo,romanization,letter_group,position) values (?, 'xx', 'x', 'VOWEL_BASIC', 999)", letter);
        jdbc.update("insert into user_letter_progress(user_id,letter_id) values (?, ?)", user, letter);
        var summary = learningQueries.monitoringSummary(user);
        assertEquals(course, summary.course().id());
        assertEquals(1, summary.course().completed());
        assertEquals(1, summary.course().total());
        assertEquals(100.0, summary.course().percent());
        org.junit.jupiter.api.Assertions.assertTrue(summary.course().units().getFirst().completed());
        assertFalse(summary.course().units().getLast().completed());
        assertEquals(1, summary.alphabet().learned());
        assertEquals(2, summary.progressCount());
        assertEquals(0, summary.completionCount());
        org.junit.jupiter.api.Assertions.assertNull(summary.firstTrackedAt());
        org.junit.jupiter.api.Assertions.assertNull(summary.lastLessonSession());
        var page = learningQueries.monitoringProgress(user, 0, 1);
        assertEquals(1, page.getNumberOfElements());
        assertEquals(2, page.getTotalElements());
        var rows = learningQueries.monitoringProgress(user, 0, 50).getContent();
        assertEquals(1, rows.stream().filter(row -> !row.inCurrentCourseMap()).count());
        assertEquals(4, rows.stream().filter(row -> row.lessonId().equals(complete)).findFirst().orElseThrow().attempts());
        String token = monitoringAdminToken();
        mvc.perform(get(monitoringUrl(user)).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.course.id").value(course.toString()))
                .andExpect(jsonPath("$.course.completed").value(1)).andExpect(jsonPath("$.course.total").value(1))
                .andExpect(jsonPath("$.course.percent").value(100.0)).andExpect(jsonPath("$.alphabet.learned").value(1))
                .andExpect(jsonPath("$.counts.progress").value(2)).andExpect(jsonPath("$.counts.completions").value(0))
                .andExpect(jsonPath("$.firstTrackedAt").isEmpty()).andExpect(jsonPath("$.lastLessonSession").isEmpty())
                .andExpect(jsonPath("$.coverage").isNotEmpty()).andExpect(jsonPath("$.generatedAt").isNotEmpty());
        mvc.perform(get(monitoringUrl(user) + "/history").header("Authorization", token).param("kind", "progress"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].source", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("LessonProgress"))));
        org.junit.jupiter.api.Assertions.assertTrue(rows.stream().allMatch(row -> "LessonProgress".equals(row.source())));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void ownerQueriesUseSessionCountsSnapshotsGapsAndMatchingReceipts() throws Exception {
        UUID user = monitoringUser(), other = monitoringUser(), course = UUID.randomUUID(), unit = UUID.randomUUID(), lesson = UUID.randomUUID();
        jdbc.update("insert into courses(id,title) values (?, 'Private history')", course);
        jdbc.update("insert into units(id,course_id,position,title) values (?, ?, 0, 'Unit')", unit, course);
        jdbc.update("insert into lessons(id,unit_id,position,title) values (?, ?, 0, 'Legacy title')", lesson, unit);
        UUID session = UUID.fromString("00000000-0000-0000-0000-000000000001"), latest = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID exercise = UUID.randomUUID(), unobserved = UUID.randomUUID();
        String snapshot = "[{\"exerciseId\":\"" + exercise + "\",\"kind\":\"TYPE_WORD\",\"position\":0,\"context\":\"Immutable\"},{\"exerciseId\":\"" + unobserved + "\",\"kind\":\"TYPE_WORD\",\"position\":1,\"context\":\"Unknown\"}]";
        jdbc.update("insert into lesson_sessions(id,user_id,lesson_id,course_id,lesson_title,lesson_type,exercises,started_at,last_received_at) values (?, ?, ?, ?, 'Snapshot title', 'LESSON', cast(? as jsonb), '2026-01-01T00:00:00Z', '2026-01-03T00:00:00Z'), (?, ?, ?, ?, 'Newest', 'LESSON', '[]', '2026-01-02T00:00:00Z', '2026-01-03T00:00:00Z')", session, user, lesson, course, snapshot, latest, user, lesson, course);
        jdbc.update("insert into lesson_exercise_results(session_id,sequence_number,exercise_id,correct,received_at) values (?, 1, ?, false, now()), (?, 3, ?, true, now())", session, exercise, session, exercise);
        jdbc.update("insert into lesson_attempts(id,user_id,lesson_id,score,accuracy,created_at) values (?, ?, ?, 80, 70, '2026-01-01')", session, user, lesson);
        jdbc.update("insert into lesson_attempts(id,user_id,lesson_id,score,accuracy,status,saved_at,created_at) values (?, ?, ?, 100, 100, 'COMPLETED', now(), '2026-01-02')", latest, user, lesson);
        var summary = learningQueries.monitoringSummary(user);
        assertEquals(java.time.Instant.parse("2026-01-01T00:00:00Z"), summary.firstTrackedAt());
        assertEquals(latest, summary.lastLessonSession().id());
        assertEquals("COMPLETED", summary.lastLessonSession().receiptStatus());
        var detail = learningQueries.monitoringSession(user, session, 0, 1);
        assertEquals("Snapshot title", detail.session().lessonTitle());
        assertEquals("IN_PROGRESS", detail.session().receiptStatus());
        org.junit.jupiter.api.Assertions.assertNull(detail.session().receiptSavedAt());
        assertEquals(2, detail.session().savedAnswers());
        assertEquals(1, detail.session().repeatedExercises());
        assertEquals(1, detail.results().getNumberOfElements());
        assertEquals(2, detail.results().getTotalElements());
        assertEquals(1, detail.observedIncorrectCount());
        assertEquals(0.5, detail.observedIncorrectRatio());
        assertEquals(java.util.List.of(unobserved), detail.exercisesWithoutSavedCorrect());
        assertEquals(2, detail.sequenceGaps().getFirst().firstSequence());
        assertEquals(2, detail.sequenceGaps().getFirst().lastSequence());
        assertEquals("Immutable", detail.results().getContent().getFirst().context());
        assertEquals("client-reported", detail.results().getContent().getFirst().source());
        String token = monitoringAdminToken();
        mvc.perform(get(monitoringUrl(user)).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lastLessonSession.id").value(latest.toString()))
                .andExpect(jsonPath("$.lastLessonSession.receiptStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.firstTrackedAt").value("2026-01-01T00:00:00Z"));
        mvc.perform(get(monitoringUrl(user) + "/sessions/" + session).header("Authorization", token).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.session.lessonTitle").value("Snapshot title"))
                .andExpect(jsonPath("$.session.receiptStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.session.savedAnswers").value(2)).andExpect(jsonPath("$.results.totalElements").value(2))
                .andExpect(jsonPath("$.results.content.length()").value(1)).andExpect(jsonPath("$.results.page").value(0))
                .andExpect(jsonPath("$.results.content[0].source").value("client-reported"))
                .andExpect(jsonPath("$.observedIncorrectCount").value(1)).andExpect(jsonPath("$.observedIncorrectRatio").value(0.5))
                .andExpect(jsonPath("$.sequenceGaps[0].firstSequence").value(2))
                .andExpect(jsonPath("$.exercisesWithoutSavedCorrect[0]").value(unobserved.toString()));
        mvc.perform(get(monitoringUrl(other) + "/sessions/" + session).header("Authorization", token))
                .andExpect(status().isNotFound());
        mvc.perform(get(monitoringUrl(user) + "/sessions/" + UUID.randomUUID()).header("Authorization", token))
                .andExpect(status().isNotFound());
        mvc.perform(get(monitoringUrl(user) + "/history").header("Authorization", token).param("kind", "completions"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(latest.toString()))
                .andExpect(jsonPath("$.content[0].source").value("client-reported"));
        assertThrows(com.hangeoreum.api.shared.web.ApiException.class, () -> learningQueries.monitoringSession(other, session, 0, 20));
        assertEquals(0, learningQueries.monitoringSessions(other, 0, 20).getTotalElements());
        assertEquals(2, learningQueries.monitoringCompletions(user, 0, 1).getTotalElements());
        assertEquals(latest, learningQueries.monitoringCompletions(user, 0, 1).getContent().getFirst().id());
        assertThrows(com.hangeoreum.api.shared.web.ApiException.class, () -> learningQueries.monitoringSessions(user, -1, 20));
        assertThrows(com.hangeoreum.api.shared.web.ApiException.class, () -> learningQueries.monitoringProgress(user, 0, 51));
        assertThrows(com.hangeoreum.api.shared.web.ApiException.class, () -> learningQueries.monitoringCompletions(user, 0, 0));
        assertThrows(com.hangeoreum.api.shared.web.ApiException.class, () -> learningQueries.monitoringSession(user, session, 0, 51));
        assertEquals(2, jdbc.update("delete from lesson_attempts where user_id=? and lesson_id=? and id in (?,?)",
                user, lesson, session, latest));
        jdbc.update("delete from lessons where id=?", lesson);
        mvc.perform(get(monitoringUrl(user) + "/sessions/" + session).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.session.lessonTitle").value("Snapshot title"));
        mvc.perform(delete("/api/v1/admin/users/" + user).header("Authorization", token)).andExpect(status().isNoContent());
        users.flush();
        assertEquals(0, jdbc.queryForObject("select count(*) from lesson_sessions where user_id=?", Integer.class, user));
        assertEquals(0, jdbc.queryForObject("select count(*) from lesson_exercise_results where session_id=?", Integer.class, session));
        mvc.perform(get(monitoringUrl(user)).header("Authorization", token)).andExpect(status().isNotFound());

    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void ownerQueriesBoundReviewsDifficultWordsAndThirtyServerDates() throws Exception {
        UUID user = monitoringUser(), other = monitoringUser(), word = UUID.randomUUID(), word2 = UUID.randomUUID(), session = UUID.randomUUID();
        jdbc.update("insert into words(id,hangul,romanization,translation) values (?, 'one', 'one', 'One'), (?, 'two', 'two', 'Two')", word, word2);
        jdbc.update("insert into user_words(user_id,word_id,is_difficult) values (?, ?, true), (?, ?, true)", user, word, user, word2);
        jdbc.update("insert into review_sessions(id,user_id,mode,total,correct) values (?, ?, 'FLASHCARDS', 1, 0)", session, user);
        jdbc.update("insert into review_session_cards(session_id,word_id,position) values (?, ?, 0), (?, ?, 1)", session, word, session, word2);
        jdbc.update("insert into review_answers(session_id,word_id,quality,is_correct) values (?, ?, 2, false)", session, word);
        var review = vocabularyQueries.monitoringReviews(user, 0, 1).getContent().getFirst();
        assertEquals(2, review.issuedCount());
        assertEquals(1, review.answeredCount());
        assertEquals(0, review.correctCount());
        org.junit.jupiter.api.Assertions.assertNull(review.finishedAt());
        var answer = vocabularyQueries.monitoringReviewAnswers(user, session, 0, 1).getContent().getFirst();
        assertEquals("one", answer.hangul());
        assertEquals(2, answer.quality());
        assertFalse(answer.isCorrect());
        assertThrows(com.hangeoreum.api.shared.web.ApiException.class, () -> vocabularyQueries.monitoringReviewAnswers(other, session, 0, 20));
        var difficult = vocabularyQueries.monitoringDifficultWords(user, 0, 1);
        assertEquals(2, difficult.getTotalElements());
        assertEquals(1, difficult.getNumberOfElements());
        org.junit.jupiter.api.Assertions.assertTrue(difficult.hasNext());
        assertEquals(2, vocabularyQueries.monitoringSummary(user).difficultWordCount());
        assertEquals(1, vocabularyQueries.monitoringSummary(user).reviewCount());
        assertEquals(0, vocabularyQueries.monitoringSummary(other).reviewCount());
        assertThrows(com.hangeoreum.api.shared.web.ApiException.class, () -> vocabularyQueries.monitoringDifficultWords(user, 0, 51));
        assertThrows(com.hangeoreum.api.shared.web.ApiException.class, () -> vocabularyQueries.monitoringReviews(user, -1, 20));
        assertThrows(com.hangeoreum.api.shared.web.ApiException.class, () -> vocabularyQueries.monitoringReviewAnswers(user, session, 0, 0));
        java.time.LocalDate today = java.time.LocalDate.now();
        jdbc.update("insert into daily_activity(user_id,activity_date,xp_earned,lessons_completed,reviews_done) values (?, ?, 25, 2, 3), (?, ?, 99, 0, 0)", user, today, user, today.minusDays(30));
        jdbc.update("insert into streaks(user_id,current,longest,last_active_date) values (?, 2, 4, ?)", user, today);
        var activity = activityQueries.monitoringActivity(user);
        assertEquals(today, activity.serverDate());
        assertEquals(30, activity.days().size());
        assertEquals(today.minusDays(29), activity.days().getFirst().date());
        assertEquals(25, activity.days().getLast().xpEarned());
        assertEquals(2, activity.days().getLast().lessonsCompleted());
        assertEquals(3, activity.days().getLast().reviewsDone());
        assertEquals(25, activity.days().stream().mapToInt(day -> day.xpEarned()).sum());
        assertEquals(2, activity.currentStreak());
        assertEquals(4, activity.longestStreak());
        assertEquals(0, activityQueries.monitoringActivity(other).days().stream().mapToInt(day -> day.xpEarned()).sum());
        String token = monitoringAdminToken();
        mvc.perform(get(monitoringUrl(user)).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.counts.reviews").value(1))
                .andExpect(jsonPath("$.difficultWords.totalElements").value(2))
                .andExpect(jsonPath("$.activity.days.length()").value(30)).andExpect(jsonPath("$.activity.currentStreak").value(2));
        mvc.perform(get(monitoringUrl(user) + "/history").header("Authorization", token).param("kind", "reviews"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].issuedCount").value(2))
                .andExpect(jsonPath("$.content[0].answeredCount").value(1)).andExpect(jsonPath("$.content[0].finishedAt").isEmpty());
        mvc.perform(get(monitoringUrl(user) + "/history").header("Authorization", token)
                        .param("kind", "reviews").param("sessionId", session.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].quality").value(2))
                .andExpect(jsonPath("$.content[0].isCorrect").value(false)).andExpect(jsonPath("$.content[0].source").value("SRS-quality"));
        mvc.perform(get(monitoringUrl(other) + "/history").header("Authorization", token)
                        .param("kind", "reviews").param("sessionId", session.toString())).andExpect(status().isNotFound());
        mvc.perform(get(monitoringUrl(user) + "/history").header("Authorization", token).param("kind", "difficult-words").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void monitoringHttpEnforcesAdminTargetAndBoundedParameters() throws Exception {
        UUID target = monitoringUser();
        String admin = monitoringAdminToken();
        UUID adminId = users.findAll().stream().filter(row -> row.getRole() == UserRole.ADMIN).findFirst().orElseThrow().getId();
        mvc.perform(get(monitoringUrl(adminId)).header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(adminId.toString()));
        User regular = users.findById(target).orElseThrow();
        String regularToken = bearer(auth.issueTokens(regular).accessToken());
        for (String suffix : java.util.List.of("", "/history?kind=sessions", "/sessions/" + UUID.randomUUID())) {
            mvc.perform(get(monitoringUrl(target) + suffix)).andExpect(status().isUnauthorized());
            mvc.perform(get(monitoringUrl(target) + suffix).header("Authorization", regularToken)).andExpect(status().isForbidden());
            mvc.perform(get(monitoringUrl(UUID.randomUUID()) + suffix).header("Authorization", admin)).andExpect(status().isNotFound());
        }
        regular.changeRole(UserRole.EDITOR);
        users.saveAndFlush(regular);
        mvc.perform(get(monitoringUrl(target)).header("Authorization", regularToken)).andExpect(status().isForbidden());
        regular.setActive(false);
        users.saveAndFlush(regular);
        mvc.perform(get(monitoringUrl(target)).header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.isActive").value(false)).andExpect(jsonPath("$.counts.sessions").value(0));
        for (String kind : java.util.List.of("sessions", "completions", "progress", "reviews", "difficult-words")) {
            String history = monitoringUrl(target) + "/history";
            mvc.perform(get(history).header("Authorization", admin).param("kind", kind))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.totalElements").value(0));
            mvc.perform(get(history).header("Authorization", admin).param("kind", kind).param("size", "50"))
                    .andExpect(status().isOk());
            for (String size : java.util.List.of("0", "51", "-1")) {
                mvc.perform(get(history).header("Authorization", admin).param("kind", kind).param("size", size)).andExpect(status().isBadRequest());
            }
            mvc.perform(get(history).header("Authorization", admin).param("kind", kind).param("page", "-1")).andExpect(status().isBadRequest());
            if (!kind.equals("reviews")) mvc.perform(get(history).header("Authorization", admin).param("kind", kind)
                    .param("sessionId", UUID.randomUUID().toString())).andExpect(status().isBadRequest());
        }
        for (String kind : java.util.List.of("SESSIONS", "unknown", "")) {
            mvc.perform(get(monitoringUrl(target) + "/history").header("Authorization", admin).param("kind", kind)).andExpect(status().isBadRequest());
        }
        mvc.perform(get(monitoringUrl(target) + "/history").header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/users/not-a-uuid/monitoring").header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(get(monitoringUrl(target) + "/sessions/" + UUID.randomUUID()).header("Authorization", admin).param("size", "51"))
                .andExpect(status().isBadRequest());
    }

    private String monitoringAdminToken() {
        User admin = users.findAll().stream().filter(user -> user.getRole() == UserRole.ADMIN).findFirst().orElseGet(() -> {
            User created = User.register("Monitoring admin", "monitor-admin-" + UUID.randomUUID() + "@example.com", "hash");
            created.changeRole(UserRole.ADMIN);
            return users.saveAndFlush(created);
        });
        return bearer(auth.issueTokens(admin).accessToken());
    }

    private static String monitoringUrl(UUID id) { return "/api/v1/admin/users/" + id + "/monitoring"; }

    private UUID monitoringUser() {
        return users.saveAndFlush(User.register("Monitor", "monitor-" + UUID.randomUUID() + "@example.com", "hash")).getId();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
