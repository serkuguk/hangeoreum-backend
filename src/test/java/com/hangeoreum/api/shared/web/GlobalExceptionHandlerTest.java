package com.hangeoreum.api.shared.web;

import com.hangeoreum.api.admin.api.AdminUserController;
import com.hangeoreum.api.identity.application.UserDto;
import com.hangeoreum.api.learning.domain.Lesson;
import com.hangeoreum.api.learning.domain.LessonType;
import com.hangeoreum.api.media.domain.Subtitle;
import com.hangeoreum.api.vocabulary.application.UserWordDto;
import com.hangeoreum.api.vocabulary.application.WordDto;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.json.JsonCompareMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import com.hangeoreum.api.identity.application.UserAdministrationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.io.IOException;
import java.net.SocketException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class GlobalExceptionHandlerTest {
    @Test
    void disconnectedVideoClientsDoNotWriteAnErrorBody() throws Exception {
        MockMvc mvc = standaloneSetup(new ContractController()).setControllerAdvice(new GlobalExceptionHandler()).build();
        for (boolean wrapped : List.of(false, true)) {
            for (boolean committed : List.of(false, true)) {
                mvc.perform(get("/contract/disconnect/" + wrapped + "/" + committed))
                        .andExpect(status().isOk())
                        .andExpect(content().string(committed ? "video" : ""))
                        .andExpect(response -> assertNotNull(response.getResolvedException()));
            }
        }
    }

    @Test
    void unrelatedIoFailuresStillReturnTheInternalErrorContract() throws Exception {
        MockMvc mvc = standaloneSetup(new ContractController()).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/contract/io-failure")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL"))
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }

    @Test
    void domainErrorsRetainTheValidationAndConflictHttpContracts() throws Exception {
        MockMvc mvc = standaloneSetup(new ContractController()).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/contract/validation")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.message").value("Subtitle requires 0 <= startMs < endMs"))
                .andExpect(jsonPath("$.details").isEmpty());
        mvc.perform(get("/contract/conflict")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Lesson needs at least one exercise to be published"))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    void relocatedDtosKeepEveryJsonProperty() throws Exception {
        MockMvc mvc = standaloneSetup(new ContractController()).build();
        mvc.perform(get("/contract/dto")).andExpect(status().isOk()).andExpect(content().json("""
                {"user":{"id":"00000000-0000-0000-0000-000000000001","name":"User","email":"user@example.com",
                  "avatarUrl":null,"role":"USER","startLevel":"BEGINNER","createdAt":"2026-01-01T00:00:00Z","isActive":true},
                 "vocabulary":{"id":"00000000-0000-0000-0000-000000000001","word":{
                  "id":"00000000-0000-0000-0000-000000000001","hangul":"가","romanization":"ga","translation":"go",
                  "partOfSpeech":null,"topicId":null,"exampleKo":null,"exampleTranslation":null,"grammarNote":null,
                  "audioUrl":null,"imageUrl":null},"level":1,"isDifficult":true,"dueDate":"2026-01-02","repetitions":1,"easeFactor":2.6}}
                """, JsonCompareMode.STRICT));
    }

    @RestController
    static class ContractController {
        private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

        @GetMapping("/contract/disconnect/{wrapped}/{committed}")
        void disconnect(@PathVariable boolean wrapped, @PathVariable boolean committed,
                        HttpServletResponse response) throws IOException {
            response.setContentType("video/mp4");
            if (committed) {
                response.getOutputStream().write("video".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                response.flushBuffer();
            }
            var failure = new ClientAbortException(new SocketException("Connection reset by peer"));
            if (wrapped) throw new IOException("Video response failed", failure);
            throw failure;
        }

        @GetMapping("/contract/io-failure")
        void ioFailure() throws IOException {
            throw new IOException("Media file could not be read");
        }

        @GetMapping("/contract/validation")
        void validation() {
            Subtitle.create(ID, "ko", (short) 0, "text", 1, 1);
        }

        @GetMapping("/contract/conflict")
        void conflict() {
            Lesson.create(ID, (short) 0, LessonType.STORY, "Lesson", (short) 10, true).publish(0, false);
        }

        @GetMapping("/contract/dto")
        Map<String, Object> dto() {
            return Map.of("user", new UserDto(ID, "User", "user@example.com", null, "USER", "BEGINNER",
                            Instant.parse("2026-01-01T00:00:00Z"), true),
                    "vocabulary", new UserWordDto(ID,
                            new WordDto(ID, "가", "ga", "go", null, null, null, null, null, null, null),
                            (short) 1, true, LocalDate.of(2026, 1, 2), 1, 2.6));
        }
    }

    @Test
    void requestBindingFailuresUseTheExistingInvalidInputContractBeforeCallingTheService() throws Exception {
        UserAdministrationService service = mock(UserAdministrationService.class);
        MockMvc mvc = standaloneSetup(new AdminUserController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        String base = "/api/v1/admin/users/" + UUID.randomUUID() + "/monitoring/history";
        for (String path : List.of(base, base + "?kind=sessions&page=invalid",
                base + "?kind=reviews&sessionId=invalid", "/api/v1/admin/users/invalid/monitoring")) {
            mvc.perform(get(path)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION"))
                    .andExpect(jsonPath("$.message").value("Invalid request values"))
                    .andExpect(jsonPath("$.details").isEmpty());
        }
        verifyNoInteractions(service);
    }
}
