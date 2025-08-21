package com.resilient.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

class RedisTokenBlacklistServiceTest {
    @Mock
    ReactiveStringRedisTemplate redisTemplate;

    @Mock
    ReactiveValueOperations<String, String> valueOps;

    RedisTokenBlacklistService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new RedisTokenBlacklistService(redisTemplate);
    }

    @Test
    void blacklistToken_happyPath() {
        when(valueOps.set(any(), eq("BLACKLISTED"), any(Duration.class))).thenReturn(Mono.just(true));
        StepVerifier.create(service.blacklistToken("token123", Duration.ofMinutes(10)))
                .verifyComplete();
    }

    @Test
    void isTokenBlacklisted_happyPath() {
        when(valueOps.get("token123")).thenReturn(Mono.just("BLACKLISTED"));
        StepVerifier.create(service.isTokenBlacklisted("token123"))
                .expectNext(true)
                .verifyComplete();
    }
}
