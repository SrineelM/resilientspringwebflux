package com.resilient.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import io.micrometer.core.instrument.MeterRegistry;

@Service
@Profile({"dev", "local"})
public class InMemoryReactiveRateLimiter implements ReactiveRateLimiter {

    private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();

    @Value("${webhook.rate-limit:30}")
    private int rateLimit;

    private final MeterRegistry meterRegistry;

    public InMemoryReactiveRateLimiter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<Boolean> isAllowed(String ip) {
        AtomicInteger count = requestCounts.computeIfAbsent(ip, k -> new AtomicInteger(0));
        boolean allowed = count.incrementAndGet() <= rateLimit;
        
        // Record metrics
        if (allowed) {
            meterRegistry.counter("rate_limiter.allowed", "ip", ip).increment();
        } else {
            meterRegistry.counter("rate_limiter.blocked", "ip", ip).increment();
        }
        
        return Mono.just(allowed);
    }
}
