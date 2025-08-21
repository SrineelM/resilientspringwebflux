package com.resilient.security;

import java.time.Duration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Profile("prod")
public class RedisTokenBlacklistService extends TokenBlacklistService {

    private final ReactiveValueOperations<String, String> valueOps;

    public RedisTokenBlacklistService(ReactiveStringRedisTemplate redisTemplate) {
        this.valueOps = redisTemplate.opsForValue();
    }

    @Override
    public Mono<Void> blacklistToken(String token, Duration ttl) {
        return valueOps.set(token, "BLACKLISTED", ttl).then();
    }

    @Override
    public Mono<Boolean> isTokenBlacklisted(String token) {
        return valueOps.get(token).map(val -> true).defaultIfEmpty(false);
    }
}
