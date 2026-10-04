package com.hangeoreum.api;

import com.hangeoreum.api.identity.application.AuthService;
import com.hangeoreum.api.identity.domain.User;
import com.hangeoreum.api.identity.domain.UserRole;
import com.hangeoreum.api.identity.infrastructure.PasswordResetMailSender;
import com.hangeoreum.api.identity.infrastructure.UserRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.stripe.webhook-secret=whsec_test_hardening", "app.outbox.fixed-delay-ms=3600000"})
@AutoConfigureMockMvc
@Testcontainers
class DatabaseHardeningIntegrationTest {
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

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @MockitoBean PasswordResetMailSender mailSender;

    @Test
    void recurringWebhooksAreAtomicAndDeduplicatedAcrossEvents() throws Exception {
        UUID user = user(UserRole.USER).getId();
        String sub = "sub_" + UUID.randomUUID();
        String invoice = invoice(sub);
        String paid = event("invoice.paid", invoice);
        assertEquals(503, webhook(paid)); // not acknowledged before checkout
        String checkout = event("checkout.session.completed", checkout(user, sub, "pro_month"));
        assertEquals(List.of(200, 200), concurrent(() -> webhook(checkout), () -> webhook(checkout)));
        assertEquals(1, count("select count(*) from subscriptions where provider_sub_id = ?", sub));
        assertEquals(0, count("select count(*) from payments where user_id = ?", user));
        assertEquals(List.of(200, 200), concurrent(() -> webhook(paid), () -> webhook(event("invoice.paid", invoice))));
        assertEquals(1, count("select count(*) from payments where user_id = ?", user));
        assertEquals(200, webhook(paid));
        assertEquals(200, webhook(event("checkout.session.completed", checkout(user, sub, "pro_month"))));
        assertEquals(1, count("select count(*) from subscriptions where provider_sub_id = ?", sub));
        String failed = event("invoice.payment_failed", invoice);
        assertEquals(List.of(200, 200), concurrent(() -> webhook(failed), () -> webhook(failed)));
        assertEquals(1, count("select count(*) from notifications where user_id = ?", user));
    }

    @Test
    void failedEventRollsBackReceiptAndLifetimeDoesNotDuplicate() throws Exception {
        UUID user = user(UserRole.USER).getId();
        String sub = "sub_" + UUID.randomUUID();
        assertEquals(200, webhook(event("checkout.session.completed", checkout(user, sub, "pro_month"))));
        String receipt = "evt_" + UUID.randomUUID();
        String invalid = "{\"id\":\"" + receipt + "\",\"type\":\"invoice.paid\",\"data\":{\"object\":"
                + invoice(sub).replace("100", "-100") + "}}";
        assertEquals(400, webhook(invalid));
        assertEquals(0, count("select count(*) from billing_webhook_events where event_id = ?", receipt));
        assertEquals(200, webhook(invalid.replace("-100", "100")));

        UUID lifetimeUser = user(UserRole.USER).getId();
        String object = checkout(lifetimeUser, null, "pro_lifetime");
        assertEquals(List.of(200, 200), concurrent(
                () -> webhook(event("checkout.session.completed", object)),
                () -> webhook(event("checkout.session.completed", object))));
        assertEquals(1, count("select count(*) from subscriptions where user_id = ?", lifetimeUser));
        assertEquals(1, count("select count(*) from payments where user_id = ?", lifetimeUser));
        mvc.perform(post("/api/v1/billing/webhook/stripe").header("Stripe-Signature", "bad")
                .contentType(MediaType.APPLICATION_JSON).content(event("invoice.paid", invoice(sub))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void selfDeletionRetainsLedgerAndLateEventsWorkWithoutUser() throws Exception {
        User user = user(UserRole.USER);
        String token = token(user);
        String sub = "sub_" + UUID.randomUUID();
        String object = checkout(user.getId(), sub, "pro_month");
        assertEquals(200, webhook(event("checkout.session.completed", object)));
        assertEquals(200, webhook(event("invoice.paid", invoice(sub))));
        mvc.perform(delete("/api/v1/me").header("Authorization", token)).andExpect(status().isConflict());
        assertEquals(200, webhook(event("customer.subscription.deleted", "{\"id\":\"" + sub + "\"}")));
        UUID subscription = jdbc.queryForObject("select id from subscriptions where provider_sub_id = ?", UUID.class, sub);
        jdbc.update("insert into user_settings(user_id) values (?)", user.getId());
        UUID lesson = lesson(unit(course(), 0), 0, "LESSON");
        jdbc.update("insert into lesson_progress(user_id,lesson_id) values (?,?)", user.getId(), lesson);
        mvc.perform(delete("/api/v1/me").header("Authorization", token)).andExpect(status().isNoContent());
        assertFalse(users.existsById(user.getId()));
        assertEquals(0, count("select count(*) from refresh_tokens where user_id = ?", user.getId()));
        assertEquals(0, count("select count(*) from lesson_progress where user_id = ?", user.getId()));
        assertEquals(0, count("select count(*) from user_settings where user_id = ?", user.getId()));
        assertEquals(1, count("select count(*) from subscriptions where id = ? and user_id is null", subscription));
        assertEquals(1, count("select count(*) from payments where subscription_id = ? and user_id is null", subscription));
        assertEquals(200, webhook(event("invoice.paid", invoice(sub))));
        assertEquals(200, webhook(event("invoice.payment_failed", invoice(sub))));
        assertEquals(2, count("select count(*) from payments where subscription_id = ? and user_id is null", subscription));
        assertEquals(409, webhook(event("checkout.session.completed", object)));
        mvc.perform(get("/api/v1/me").header("Authorization", token)).andExpect(status().isUnauthorized());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("delete from subscriptions where id = ?", subscription));
    }

    @Test
    void deletionAndCheckoutUseTheSameAccountLock() throws Exception {
        for (int i = 0; i < 4; i++) {
            User user = user(UserRole.USER);
            String token = token(user);
            String sub = "sub_" + UUID.randomUUID();
            String checkout = event("checkout.session.completed", checkout(user.getId(), sub, "pro_month"));
            List<Integer> results = concurrent(
                    () -> mvc.perform(delete("/api/v1/me").header("Authorization", token)).andReturn().getResponse().getStatus(),
                    () -> webhook(checkout));
            assertTrue(results.equals(List.of(204, 409)) || results.equals(List.of(409, 200)), results.toString());
            assertEquals(users.existsById(user.getId()) ? 1 : 0,
                    count("select count(*) from subscriptions where provider_sub_id=?", sub));
            assertEquals(0, count("select count(*) from subscriptions where provider_sub_id=? and user_id is null", sub));
        }
    }

    @Test
    void paymentCannotReferenceAnotherUsersSubscription() {
        UUID first = user(UserRole.USER).getId();
        UUID second = user(UserRole.USER).getId();
        UUID subscription = UUID.randomUUID();
        jdbc.update("insert into subscriptions(id,user_id,plan_id,provider) select ?,?,id,'STRIPE' from plans where code='pro_month'",
                subscription, first);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into payments(user_id,subscription_id,provider,amount_cents) values (?,?,'STRIPE',0)", second, subscription));
    }

    @Test
    void reorderSupportsSwapsCyclesAndRejectsInvalidRequests() throws Exception {
        String editor = token(user(UserRole.EDITOR));
        UUID course = course();
        UUID a = unit(course, 0), b = unit(course, 1), c = unit(course, 2);
        String url = "/api/v1/admin/units/reorder";
        request(post(url), editor, reorder(a, 1, b, 0)).andExpect(status().isOk());
        request(post(url), editor, reorder(a, 2, b, 1, c, 0)).andExpect(status().isOk());
        assertEquals(2, count("select position from units where id = ?", a));
        request(post(url), editor, reorder(a, 0)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
        assertEquals(2, count("select position from units where id = ?", a));
        request(post(url), editor, reorder(a, 0, a, 1)).andExpect(status().isBadRequest());
        request(post(url), editor, reorder(a, -1)).andExpect(status().isBadRequest());
        request(post(url), editor, reorder(UUID.randomUUID(), 0)).andExpect(status().isNotFound());
        request(post(url), editor, reorder(a, 0, unit(course(), 0), 1)).andExpect(status().isBadRequest());
        request(post(url), editor, "[]").andExpect(status().isOk());
        UUID x = lesson(a, 0, "LESSON"), y = lesson(a, 1, "LESSON"), z = lesson(a, 2, "LESSON");
        String lessonUrl = "/api/v1/admin/lessons/reorder";
        request(post(lessonUrl), editor, reorder(x, 1, y, 0)).andExpect(status().isOk());
        request(post(lessonUrl), editor, reorder(x, 2, y, 1, z, 0)).andExpect(status().isOk());
        request(post(lessonUrl), editor, reorder(x, 0)).andExpect(status().isConflict());
        request(post(lessonUrl), editor, reorder(x, 0, lesson(b, 0, "LESSON"), 1)).andExpect(status().isBadRequest());
        request(post(lessonUrl), editor, reorder(x, 0, x, 1)).andExpect(status().isBadRequest());
        request(post(lessonUrl), editor, reorder(UUID.randomUUID(), 0)).andExpect(status().isNotFound());
        request(post(lessonUrl), editor, reorder(x, -1)).andExpect(status().isBadRequest());
        request(post(lessonUrl), editor, "[]").andExpect(status().isOk());
        request(post(url), token(user(UserRole.USER)), "[]").andExpect(status().isForbidden());
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("[]")).andExpect(status().isUnauthorized());
    }

    @Test
    void storyLinksAndReplacementPreserveInvariants() throws Exception {
        String editor = token(user(UserRole.EDITOR));
        UUID unit = unit(course(), 0), lesson = lesson(unit, 0, "STORY"), clip = clip("STORY");
        String url = "/api/v1/admin/lessons/" + lesson + "/story";
        String body = story(clip);
        request(put(url), editor, body).andExpect(status().isOk());
        request(put(url), editor, body).andExpect(status().isOk());
        assertEquals(1, count("select count(*) from story_lines l join stories s on s.id=l.story_id where s.lesson_id=?", lesson));
        request(put("/api/v1/admin/lessons/" + lesson), editor, lessonBody(unit, "LESSON")).andExpect(status().isConflict());
        request(put("/api/v1/admin/clips/" + clip), editor, "{\"kind\":\"WORD\"}").andExpect(status().isConflict());
        request(put(url), editor, story(clip("WORD"))).andExpect(status().isConflict());
        request(put("/api/v1/admin/lessons/" + lesson(unit, 1, "LESSON") + "/story"), editor, body)
                .andExpect(status().isConflict());
        request(put(url), editor, body.replace("\"endMs\":1", "\"endMs\":0")).andExpect(status().isBadRequest());
        request(put(url), editor, story(null)).andExpect(status().isOk());
    }

    @Test
    void concurrentStoryCreationAndTypeChangesCannotCreateIncompatibleLinks() throws Exception {
        String editor = token(user(UserRole.EDITOR));
        UUID unit = unit(course(), 0);
        for (int i = 0; i < 4; i++) {
            UUID lesson = lesson(unit, i, "STORY"), clip = clip("STORY");
            String storyUrl = "/api/v1/admin/lessons/" + lesson + "/story";
            List<Integer> result = concurrent(
                    () -> request(put(storyUrl), editor, story(clip)).andReturn().getResponse().getStatus(),
                    () -> request(put("/api/v1/admin/clips/" + clip), editor, "{\"kind\":\"WORD\"}")
                            .andReturn().getResponse().getStatus());
            assertEquals(List.of(200, 409), result.stream().sorted().toList());
            UUID other = lesson(unit, i + 10, "STORY");
            result = concurrent(
                    () -> request(put("/api/v1/admin/lessons/" + other + "/story"), editor, story(null)).andReturn().getResponse().getStatus(),
                    () -> request(put("/api/v1/admin/lessons/" + other), editor, lessonBody(unit, "LESSON")).andReturn().getResponse().getStatus());
            assertEquals(List.of(200, 409), result.stream().sorted().toList());
        }
        assertEquals(0, count("select count(*) from stories s join lessons l on l.id=s.lesson_id where l.type<>'STORY'"));
        assertEquals(0, count("select count(*) from stories s join media_clips c on c.id=s.clip_id where c.kind<>'STORY'"));
    }

    @Test
    void emailUniquenessSurvivesConcurrentRegistrationAndMixedCaseStorage() throws Exception {
        String email = "Email-" + UUID.randomUUID() + "@example.com";
        String registration = "{\"name\":\"Test\",\"email\":\"" + email + "\",\"password\":\"password123\"}";
        List<Integer> statuses = concurrent(
                () -> request(post("/api/v1/auth/register"), null, registration).andReturn().getResponse().getStatus(),
                () -> request(post("/api/v1/auth/register"), null, registration.toLowerCase(java.util.Locale.ROOT)).andReturn().getResponse().getStatus());
        assertEquals(List.of(201, 409), statuses.stream().sorted().toList());
        request(post("/api/v1/auth/register"), null, registration).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
        jdbc.update("update users set email=? where lower(email)=lower(?)", email, email);
        request(post("/api/v1/auth/login"), null, "{\"email\":\"" + email.toUpperCase(java.util.Locale.ROOT)
                + "\",\"password\":\"password123\"}").andExpect(status().isOk());
        request(post("/api/v1/auth/password-reset/request"), null, "{\"email\":\"" + email + "\"}")
                .andExpect(status().isAccepted());
        assertEquals(1, count("select count(*) from password_reset_tokens t join users u on u.id=t.user_id where u.email=?", email));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into users(name,email) values ('Duplicate',?)", email.toLowerCase(java.util.Locale.ROOT)));
    }

    @Test
    void databaseRejectsInvalidValuesAndAcceptsBoundaries() {
        UUID user = user(UserRole.USER).getId(), lesson = lesson(unit(course(), 0), 0, "STORY"), clip = clip("STORY");
        UUID word = UUID.randomUUID(), review = UUID.randomUUID(), story = UUID.randomUUID();
        jdbc.update("insert into words(id,hangul,romanization,translation) values (?,'가','ga','go')", word);
        jdbc.update("insert into user_words(user_id,word_id) values (?,?)", user, word);
        jdbc.update("insert into lesson_progress(user_id,lesson_id,score,accuracy) values (?,?,0,100)", user, lesson);
        jdbc.update("insert into review_sessions(id,user_id,mode) values (?,?,'FLASHCARDS')", review, user);
        jdbc.update("insert into streaks(user_id) values (?)", user);
        jdbc.update("insert into subtitles(clip_id,lang,position,text,start_ms,end_ms) values (?,'ko',0,'test',0,1)", clip);
        jdbc.update("insert into stories(id,lesson_id,title) values (?,?,'Test')", story, lesson);
        jdbc.update("insert into story_lines(story_id,position,text_ko,text_translation) values (?,0,'가','go')", story);
        jdbc.update("insert into payments(user_id,provider,amount_cents) values (?,'STRIPE',0)", user);
        List<String> invalid = List.of(
                "update lesson_progress set score=101", "update lesson_progress set accuracy=-1", "update lesson_progress set attempts=0",
                "update user_words set interval_days=-1", "update user_words set repetitions=-1",
                "update review_sessions set total=-1", "update review_sessions set correct=-1", "update review_sessions set correct=total+1",
                "update review_sessions set xp_earned=-1", "update review_sessions set finished_at=started_at-interval '1 second'",
                "update subtitles set start_ms=-1", "update subtitles set end_ms=start_ms",
                "update story_lines set start_ms=0,end_ms=null", "update story_lines set start_ms=null,end_ms=1",
                "update story_lines set start_ms=0,end_ms=0", "update story_lines set start_ms=-1,end_ms=1",
                "update plans set price_cents=-1", "update payments set amount_cents=-1",
                "update streaks set current=-1", "update streaks set current=longest+1",
                "update lessons set xp_reward=-1", "update media_clips set duration_ms=-1");
        for (String sql : invalid) assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(sql), sql);
        jdbc.update("update lesson_progress set score=null,accuracy=null where user_id=?", user);
        jdbc.update("update story_lines set start_ms=0,end_ms=1 where story_id=?", story);
        jdbc.update("update story_lines set start_ms=null,end_ms=null where story_id=?", story);
        jdbc.update("update review_sessions set finished_at=started_at where id=?", review);
        jdbc.update("update media_clips set duration_ms=0 where id=?", clip);
        jdbc.update("update media_clips set duration_ms=null where id=?", clip);
    }

    @Test
    void upgradeFromV8PreservesRowsAndBackfillsPaymentProvider() throws Exception {
        jdbc.execute("create database hardening_upgrade");
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/hardening_upgrade";
        Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()).target("8").load().migrate();
        try (var connection = java.sql.DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("insert into users(id,name,email) values ('00000000-0000-0000-0000-000000000001','Upgrade','Upgrade@example.com')");
            statement.executeUpdate("insert into payments(user_id,amount_cents) values ('00000000-0000-0000-0000-000000000001',100)");
        }
        assertEquals(5, Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()).load().migrate().migrationsExecuted);
        try (var connection = java.sql.DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement();
             var rows = statement.executeQuery("select count(*) from payments where provider='STRIPE' and amount_cents=100")) {
            assertTrue(rows.next());
            assertEquals(1, rows.getInt(1));
        }
    }

    private User user(UserRole role) {
        User user = User.register("Test", UUID.randomUUID() + "@example.com", "hash");
        user.changeRole(role);
        return users.saveAndFlush(user);
    }

    private String token(User user) { return "Bearer " + auth.issueTokens(user).accessToken(); }
    private int count(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }

    private UUID course() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into courses(id,title) values (?,'Test')", id);
        return id;
    }

    private UUID unit(UUID course, int position) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into units(id,course_id,position,title) values (?,?,?,'Test')", id, course, position);
        return id;
    }

    private UUID lesson(UUID unit, int position, String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into lessons(id,unit_id,position,title,type) values (?,?,?,'Test',cast(? as lesson_type))", id, unit, position, type);
        return id;
    }

    private UUID clip(String kind) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into media_clips(id,kind) values (?,cast(? as clip_kind))", id, kind);
        return id;
    }

    private static String reorder(Object... values) {
        var items = new java.util.ArrayList<String>();
        for (int i = 0; i < values.length; i += 2) items.add("{\"id\":\"" + values[i] + "\",\"position\":" + values[i+1] + "}");
        return "[" + String.join(",", items) + "]";
    }

    private static String story(UUID clip) {
        return "{\"title\":\"Test\",\"clipId\":" + (clip == null ? "null" : "\"" + clip + "\"")
                + ",\"lines\":[{\"position\":0,\"textKo\":\"가\",\"textTranslation\":\"go\",\"startMs\":0,\"endMs\":1}]}";
    }

    private static String lessonBody(UUID unit, String type) {
        return "{\"unitId\":\"" + unit + "\",\"position\":0,\"type\":\"" + type + "\",\"title\":\"Test\",\"xpReward\":0}";
    }

    private static String checkout(UUID user, String sub, String plan) {
        return "{\"id\":\"cs_" + UUID.randomUUID() + "\",\"client_reference_id\":\"" + user
                + "\",\"subscription\":" + (sub == null ? "null" : "\"" + sub + "\"")
                + ",\"metadata\":{\"planCode\":\"" + plan + "\"}}";
    }

    private static String invoice(String sub) {
        return "{\"id\":\"in_" + UUID.randomUUID() + "\",\"subscription\":\"" + sub + "\",\"amount_paid\":100,\"currency\":\"usd\"}";
    }

    private static String event(String type, String object) {
        return "{\"id\":\"evt_" + UUID.randomUUID() + "\",\"type\":\"" + type + "\",\"data\":{\"object\":" + object + "}}";
    }

    private int webhook(String payload) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("whsec_test_hardening".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
        return mvc.perform(post("/api/v1/billing/webhook/stripe").contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=" + timestamp + ",v1=" + signature).content(payload))
                .andReturn().getResponse().getStatus();
    }

    private org.springframework.test.web.servlet.ResultActions request(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        if (token != null) request.header("Authorization", token);
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static List<Integer> concurrent(Callable<Integer> first, Callable<Integer> second) throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(5, TimeUnit.SECONDS); return first.call(); });
            var b = executor.submit(() -> { start.await(5, TimeUnit.SECONDS); return second.call(); });
            return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        }
    }
}
