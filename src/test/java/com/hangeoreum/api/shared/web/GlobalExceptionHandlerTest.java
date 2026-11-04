package com.hangeoreum.api.shared.web;

import com.hangeoreum.api.admin.api.AdminUserController;
import com.hangeoreum.api.identity.application.UserAdministrationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class GlobalExceptionHandlerTest {
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
