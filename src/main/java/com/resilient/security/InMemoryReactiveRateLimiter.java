package com.resilient.security;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Simple in-memory sliding window limiter for test/local/dev profiles. */
@Service
@Profile({"test","local","dev"})
public class InMemoryReactiveRateLimiter implements ReactiveRateLimiter {
    private final Map<String,Window> windows = new ConcurrentHashMap<>();
    private final int limit = 5; // low threshold for tests
    private final long windowMillis = 2000;

    @Override
    public Mono<Boolean> isAllowed(String key) {
        long now = System.currentTimeMillis();
        Window w = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.start >= windowMillis) {
                return new Window(now,1);
            }
            existing.count++;
            return existing;
        });
        return Mono.just(w.count <= limit);
    }

    private static class Window { long start; int count; Window(long s,int c){start=s;count=c;} }
}
