package com.resilient.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Profile({"dev", "local"})
public class InMemoryReactiveRateLimiter implements ReactiveRateLimiter {

    private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();

    @Value("${webhook.rate-limit:30}")
    private int rateLimit;

    public Mono<Boolean> isAllowed(String ip) {
        AtomicInteger count = requestCounts.computeIfAbsent(ip, k -> new AtomicInteger(0));
        if (count.incrementAndGet() > rateLimit) {
            return Mono.just(false);
        }
        return Mono.just(true);
    }
}
