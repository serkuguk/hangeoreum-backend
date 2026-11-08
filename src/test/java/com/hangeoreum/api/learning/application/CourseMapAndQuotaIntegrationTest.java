package com.hangeoreum.api.learning.application;

import com.hangeoreum.api.shared.events.OutboxDispatcher;
import com.hangeoreum.api.shared.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Found in the local e2e run: an empty unit on the map, and a free learner losing a whole lesson to the daily limit. */
@SpringBootTest
@Testcontainers
class CourseMapAndQuotaIntegrationTest {
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

    @Autowired LearningService learning;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean OutboxDispatcher dispatcher;

    @Test
    void hidesUnitsWithoutPublishedLessonsAndBlocksFreeLearnerOverTheDailyLimitAtStart() {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID liveUnit = UUID.randomUUID();
        UUID draftUnit = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        jdbc.update("insert into users (id, name, email) values (?, 'Map User', ?)", userId, userId + "@example.com");
        jdbc.update("insert into courses (id, title, is_published) values (?, 'Course', true)", courseId);
        jdbc.update("insert into units (id, course_id, position, title, is_published) values (?, ?, 1, 'Live', true)", liveUnit, courseId);
        jdbc.update("insert into units (id, course_id, position, title, is_published) values (?, ?, 2, 'Drafts only', true)", draftUnit, courseId);
        jdbc.update("insert into lessons (id, unit_id, position, title, is_free, is_published) values (?, ?, 1, 'Lesson', true, true)", lessonId, liveUnit);
        jdbc.update("insert into lessons (id, unit_id, position, title, is_free, is_published) values (?, ?, 1, 'Draft', true, false)", UUID.randomUUID(), draftUnit);

        var units = learning.getCourseMap(userId).units();
        assertEquals(1, units.size());
        assertEquals(liveUnit, units.getFirst().id());

        jdbc.update("insert into daily_activity (user_id, activity_date, lesson_reservations) values (?, ?, 1)", userId, LocalDate.now());
        ApiException refused = assertThrows(ApiException.class, () -> learning.startSession(userId, lessonId, UUID.randomUUID()));
        assertEquals("LIMIT_REACHED", refused.getCode());
    }
}
