package com.resilient.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RedisReactiveRateLimiterTest {
    @Mock
    ReactiveStringRedisTemplate redisTemplate;

    @Mock
    ReactiveValueOperations<String, String> valueOps;

    RedisReactiveRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        rateLimiter = new RedisReactiveRateLimiter(redisTemplate);
    }

    @Test
    void isAllowed_happyPath_firstRequest() {
        when(valueOps.increment(any())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(any(), any(Duration.class))).thenReturn(Mono.just(true));
        StepVerifier.create(rateLimiter.isAllowed("127.0.0.1")).expectNext(true).verifyComplete();
    }

    @Test
    void isAllowed_happyPath_withinLimit() {
        when(valueOps.increment(any())).thenReturn(Mono.just(5L));
        StepVerifier.create(rateLimiter.isAllowed("127.0.0.1")).expectNext(true).verifyComplete();
    }
}
