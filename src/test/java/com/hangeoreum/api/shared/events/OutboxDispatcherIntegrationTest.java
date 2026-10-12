package com.hangeoreum.api.shared.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringJUnitConfig(OutboxDispatcherIntegrationTest.TestConfig.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=none")
@Testcontainers
class OutboxDispatcherIntegrationTest {
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

    @Autowired OutboxDispatcher dispatcher;
    @Autowired OutboxEventRepository repository;
    @Autowired RecordingHandler handler;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        repository.deleteAll();
        handler.reset(false, null, null);
    }

    @Test
    void dispatcherCommitsCompletionAndIndependentHandlerClaim() {
        OutboxEvent event = save("test.event");

        dispatcher.dispatch();
        dispatcher.dispatch();

        OutboxEvent stored = repository.findById(event.getId()).orElseThrow();
        assertEquals("COMPLETED", stored.getStatus());
        assertNotNull(stored.getProcessedAt());
        assertEquals(0, stored.getAttempts());
        assertEquals(1, handler.calls());
        assertEquals(1, claims(event.getId()));
    }

    @Test
    void handlerRollbackStillCommitsRetryAndBackoff() {
        OutboxEvent event = save("test.event");
        handler.reset(true, null, null);
        Instant before = Instant.now();

        dispatcher.dispatch();

        OutboxEvent failed = repository.findById(event.getId()).orElseThrow();
        assertEquals("PENDING", failed.getStatus());
        assertEquals(1, failed.getAttempts());
        assertEquals("handler failed", failed.getLastError());
        assertTrue(failed.getAvailableAt().isAfter(before.plusSeconds(1)));
        assertEquals(0, claims(event.getId()));
        dispatcher.dispatch();
        assertEquals(1, handler.calls());

        jdbc.update("update outbox_events set available_at = now() - interval '1 second' where id = ?", event.getId());
        handler.reset(false, null, null);
        dispatcher.dispatch();
        assertEquals("COMPLETED", repository.findById(event.getId()).orElseThrow().getStatus());
        assertEquals(1, claims(event.getId()));
    }

    @Test
    void concurrentDispatcherSkipsAnEventLockedByAnotherDispatcher() throws Exception {
        OutboxEvent event = save("test.event");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        handler.reset(false, entered, release);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(dispatcher::dispatch);
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                var second = executor.submit(dispatcher::dispatch);
                second.get(5, TimeUnit.SECONDS);
                assertEquals(1, handler.calls());
            } finally {
                release.countDown();
            }
            first.get(5, TimeUnit.SECONDS);
        }
        assertEquals("COMPLETED", repository.findById(event.getId()).orElseThrow().getStatus());
        assertEquals(1, claims(event.getId()));
    }

    @Test
    void missingHandlerCommitsFailureAttempt() {
        OutboxEvent event = save("unknown.event");
        dispatcher.dispatch();
        OutboxEvent stored = repository.findById(event.getId()).orElseThrow();
        assertEquals("PENDING", stored.getStatus());
        assertEquals(1, stored.getAttempts());
        assertEquals("No handler for outbox event unknown.event", stored.getLastError());
    }

    private OutboxEvent save(String type) {
        return repository.saveAndFlush(OutboxEvent.pending(UUID.randomUUID(), type, "{}"));
    }

    private int claims(UUID eventId) {
        return jdbc.queryForObject("select count(*) from processed_events where event_id = ?", Integer.class, eventId);
    }

    // Load only shared events: no scheduler can race the manual dispatcher calls.
    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({OutboxDispatcher.class, OutboxEventProcessor.class})
    static class TestConfig {
        @Bean
        RecordingHandler handler(ProcessedEventRepository processedEvents) {
            return new RecordingHandler(processedEvents);
        }
    }

    public static class RecordingHandler implements OutboxEventHandler {
        private final ProcessedEventRepository processedEvents;
        private final AtomicInteger calls = new AtomicInteger();
        private boolean fail;
        private CountDownLatch entered;
        private CountDownLatch release;

        RecordingHandler(ProcessedEventRepository processedEvents) {
            this.processedEvents = processedEvents;
        }

        void reset(boolean fail, CountDownLatch entered, CountDownLatch release) {
            calls.set(0);
            this.fail = fail;
            this.entered = entered;
            this.release = release;
        }

        int calls() { return calls.get(); }
        @Override public String type() { return "test.event"; }

        @Override
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void handle(UUID eventId, String payload) {
            if (processedEvents.claim("test.handler", eventId) == 0) return;
            calls.incrementAndGet();
            if (entered != null) {
                entered.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("handler timed out");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }
            if (fail) throw new IllegalStateException("handler failed");
        }
    }
}
