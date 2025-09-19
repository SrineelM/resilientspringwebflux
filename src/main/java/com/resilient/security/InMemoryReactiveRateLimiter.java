package com.resilient.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Simple in-memory sliding window rate limiter for test/local/dev profiles.
 * <p>
 * This implementation is NOT suitable for production as it does not scale across multiple instances.
 * It is used for deterministic testing and local development only.
 *
 * <p>Each key (user or IP) is tracked in a fixed-size window. If the number of requests exceeds the limit
 * within the window, further requests are denied until the window resets.
 */
@Service
@Profile({"test", "local", "dev"})
public class InMemoryReactiveRateLimiter implements ReactiveRateLimiter {
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int limit = 5; // low threshold for tests
    private final long windowMillis = 2000;

    /**
     * Checks if a request for the given key is allowed under the current rate limit.
     *
     * @param key The user or IP key to check
     * @return Mono emitting true if allowed, false if rate limit exceeded
     */
    @Override
    public Mono<Boolean> isAllowed(String key) {
        long now = System.currentTimeMillis();
        Window w = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.start >= windowMillis) {
                return new Window(now, 1);
            }
            existing.count++;
            return existing;
        });
        return Mono.just(w.count <= limit);
    }

    /**
     * Internal class representing a sliding window for a key.
     */
    private static class Window {
        long start;
        int count;

        Window(long s, int c) {
            start = s;
            count = c;
        }
    }
}
