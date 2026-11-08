package com.hangeoreum.api.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimitFilterTest {

    private static final int LIMIT = 10;

    private final AtomicLong now = new AtomicLong(0);
    private final AuthRateLimitFilter filter = new AuthRateLimitFilter(JsonMapper.builder().build(), LIMIT, now::get);

    private int login(String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    void blocksTheEleventhAttemptPerIpAndResetsNextMinute() throws Exception {
        for (int i = 0; i < LIMIT; i++) {
            assertThat(login("1.1.1.1")).isEqualTo(200);
        }
        assertThat(login("1.1.1.1")).isEqualTo(429);
        assertThat(login("2.2.2.2")).isEqualTo(200);

        now.set(60_000);
        assertThat(login("1.1.1.1")).isEqualTo(200);
    }

    @Test
    void ignoresOtherEndpoints() throws Exception {
        for (int i = 0; i <= LIMIT; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
