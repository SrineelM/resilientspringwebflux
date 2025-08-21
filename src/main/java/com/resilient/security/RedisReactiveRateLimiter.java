package com.resilient.security;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Profile("prod")
public class RedisReactiveRateLimiter implements ReactiveRateLimiter {

    private final ReactiveStringRedisTemplate redisTemplate;

    @Value("${webhook.rate-limit:30}")
    private int rateLimit;

    @Value("${webhook.rate-limit-window-seconds:60}")
    private int windowSeconds;

    public RedisReactiveRateLimiter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Mono<Boolean> isAllowed(String ip) {
        String key = "rate_limit:" + ip;
        return redisTemplate.opsForValue().increment(key).flatMap(count -> {
            if (count == 1) {
                // Set TTL only when first request in window
                return redisTemplate
                        .expire(key, Duration.ofSeconds(windowSeconds))
                        .thenReturn(true);
            }
            return Mono.just(count <= rateLimit);
        });
    }
}
