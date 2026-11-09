package com.hangeoreum.api.shared.security;

import com.hangeoreum.api.shared.web.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * Brute-force guard for the credential endpoints: at most {@code app.auth.rate-limit-per-minute} POSTs per client IP per minute.
 * ponytail: fixed one-minute window in this JVM's memory — enough for a single instance;
 * move to Bucket4j + Redis when the API runs on more than one node.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/password-reset/request",
            "/api/v1/auth/password-reset/confirm");

    private final ObjectMapper objectMapper;
    private final int limit;
    private final LongSupplier clock;
    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
    private volatile long window;

    @Autowired
    public AuthRateLimitFilter(ObjectMapper objectMapper, @Value("${app.auth.rate-limit-per-minute}") int limit) {
        this(objectMapper, limit, System::currentTimeMillis);
    }

    AuthRateLimitFilter(ObjectMapper objectMapper, int limit, LongSupplier clock) {
        this.objectMapper = objectMapper;
        this.limit = limit;
        this.clock = clock;
        this.window = clock.getAsLong() / 60_000;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long minute = clock.getAsLong() / 60_000;
        if (minute != window) {
            window = minute;
            hits.clear();
        }
        // remoteAddr is the real client IP behind nginx thanks to server.forward-headers-strategy=native
        int count = hits.computeIfAbsent(request.getRemoteAddr(), ip -> new AtomicInteger()).incrementAndGet();
        if (count > limit) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    new ErrorResponse("TOO_MANY_REQUESTS", "Too many attempts, try again in a minute", Map.of()));
            return;
        }
        chain.doFilter(request, response);
    }
}
