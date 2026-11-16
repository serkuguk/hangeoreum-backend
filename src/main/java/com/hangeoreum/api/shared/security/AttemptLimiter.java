package com.hangeoreum.api.shared.security;

import com.hangeoreum.api.shared.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-account guard against password guessing: the per-IP {@link AuthRateLimitFilter} does not stop
 * an attacker who spreads guesses for one account over many addresses.
 * ponytail: fixed 15-minute window in this JVM's memory, same ceiling as AuthRateLimitFilter —
 * move to Redis when the API runs on more than one node.
 */
@Component
public class AttemptLimiter {

    static final int MAX_FAILURES = 10;
    private static final long WINDOW_MS = 15 * 60_000;

    private final Map<String, AtomicInteger> failures = new ConcurrentHashMap<>();
    private volatile long window = System.currentTimeMillis() / WINDOW_MS;

    /** Call before checking the password; throws 429 once the key has used up its failures. */
    public void requireAllowed(String key) {
        long current = System.currentTimeMillis() / WINDOW_MS;
        if (current != window) {
            window = current;
            failures.clear();
        }
        AtomicInteger count = failures.get(key);
        if (count != null && count.get() >= MAX_FAILURES) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
                    "Too many failed attempts, try again in 15 minutes");
        }
    }

    /** Counts every call, not only failures: caps how many mails one address can be sent. */
    public void record(String key) {
        requireAllowed(key);
        failed(key);
    }

    public void failed(String key) {
        failures.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
    }

    public void succeeded(String key) {
        failures.remove(key);
    }
}
