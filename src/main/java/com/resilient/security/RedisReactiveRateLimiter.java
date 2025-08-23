package com.resilient.security;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.StaticScriptSource;
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

    private static final DefaultRedisScript<Long> SLIDING_WINDOW_SCRIPT;
    static {
        SLIDING_WINDOW_SCRIPT = new DefaultRedisScript<>();
        SLIDING_WINDOW_SCRIPT.setResultType(Long.class);
        SLIDING_WINDOW_SCRIPT.setScriptSource(new StaticScriptSource(
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1]-ARGV[2]);" +
            "local count = redis.call('ZCARD', KEYS[1]);" +
            "if count < tonumber(ARGV[3]) then " +
            "  redis.call('ZADD', KEYS[1], ARGV[1], ARGV[1]);" +
            "  redis.call('EXPIRE', KEYS[1], math.floor(ARGV[2]/1000));" +
            "  return 1 " +
            "else return 0 end"));
    }

    public Mono<Boolean> isAllowed(String ip) {
        String key = "rate_limit:" + ip;
        long now = System.currentTimeMillis();
    return redisTemplate.execute(SLIDING_WINDOW_SCRIPT,
            List.of(key),
            List.of(
                String.valueOf(now), // ARGV[1] current millis
                String.valueOf(windowSeconds * 1000L), // ARGV[2] window size ms
                String.valueOf(rateLimit)))
                .single()
                .map(result -> result == 1L)
                .onErrorResume(e -> {
                    // Fallback: allow on error to avoid blocking legitimate traffic; could choose deny.
                    return Mono.just(true);
                });
    }
}
