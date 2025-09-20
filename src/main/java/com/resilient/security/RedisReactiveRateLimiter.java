package com.resilient.security;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.StaticScriptSource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Redis-based reactive rate limiter implementation for production environments.
 * <p>
 * This service implements a sliding window rate limiting algorithm using Redis sorted sets.
 * It tracks request timestamps per IP address and enforces a maximum number of requests
 * within a configurable time window. The implementation is distributed and reactive,
 * suitable for high-throughput WebFlux applications.
 * <p>
 * Key features:
 * <ul>
 *   <li>Sliding window: Allows bursts within the window while maintaining overall limits.</li>
 *   <li>Distributed: Works across multiple application instances via Redis.</li>
 *   <li>Reactive: Returns Mono&lt;Boolean&gt; for non-blocking operation.</li>
 *   <li>Fault-tolerant: Falls back to allowing requests on Redis errors.</li>
 * </ul>
 * <p>
 * Configuration:
 * <ul>
 *   <li>webhook.rate-limit: Max requests per window (default: 30)</li>
 *   <li>webhook.rate-limit-window-seconds: Window size in seconds (default: 60)</li>
 * </ul>
 */
@Service
@Profile("prod") // Only active in production profile
public class RedisReactiveRateLimiter implements ReactiveRateLimiter {

    // Reactive Redis template for executing commands asynchronously
    private final ReactiveStringRedisTemplate redisTemplate;

    // Maximum number of requests allowed per IP within the time window
    @Value("${webhook.rate-limit:30}")
    private int rateLimit;

    // Size of the sliding window in seconds
    @Value("${webhook.rate-limit-window-seconds:60}")
    private int windowSeconds;

    /**
     * Constructor injecting the reactive Redis template.
     *
     * @param redisTemplate The reactive Redis template for executing commands
     */
    public RedisReactiveRateLimiter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // Redis Lua script for atomic sliding window rate limiting
    // Uses a sorted set where scores are timestamps (milliseconds)
    private static final DefaultRedisScript<Long> SLIDING_WINDOW_SCRIPT;

    static {
        SLIDING_WINDOW_SCRIPT = new DefaultRedisScript<>();
        SLIDING_WINDOW_SCRIPT.setResultType(Long.class); // Script returns 1 (allowed) or 0 (denied)
        SLIDING_WINDOW_SCRIPT.setScriptSource(
                new StaticScriptSource(
                        // Remove expired entries (older than current time - window size)
                        "redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1]-ARGV[2]);"
                        // Count remaining entries in the window
                        + "local count = redis.call('ZCARD', KEYS[1]);"
                        // If under limit, add current timestamp and allow
                        + "if count < tonumber(ARGV[3]) then "
                        + "  redis.call('ZADD', KEYS[1], ARGV[1], ARGV[1]);"
                        + "  redis.call('EXPIRE', KEYS[1], math.floor(ARGV[2]/1000));"
                        + "  return 1 "
                        // Else, deny
                        + "else return 0 end"));
    }

    /**
     * Checks if a request from the given IP is allowed based on rate limits.
     * <p>
     * This method executes the Redis Lua script atomically to:
     * 1. Remove expired timestamps from the sliding window.
     * 2. Check if the current request count is below the limit.
     * 3. If allowed, add the current timestamp to the window.
     * 4. Set an expiration on the key to prevent memory leaks.
     *
     * @param ip The IP address to rate limit (used as part of the Redis key)
     * @return Mono&lt;Boolean&gt; emitting true if allowed, false if rate limited
     */
    public Mono<Boolean> isAllowed(String ip) {
        // Create a unique key for this IP's rate limit data
        String key = "rate_limit:" + ip;

        // Current timestamp in milliseconds
        long now = System.currentTimeMillis();

        // Execute the Redis script with parameters
        return redisTemplate
                .execute(
                        SLIDING_WINDOW_SCRIPT,
                        List.of(key), // KEYS[1]: the Redis key
                        List.of(
                                String.valueOf(now), // ARGV[1]: current time (ms)
                                String.valueOf(windowSeconds * 1000L), // ARGV[2]: window size (ms)
                                String.valueOf(rateLimit))) // ARGV[3]: max requests
                .single() // Expect single result from script
                .map(result -> result == 1L) // 1 = allowed, 0 = denied
                .onErrorResume(e -> {
                    // On Redis errors, allow the request to avoid blocking legitimate traffic
                    // In production, consider logging the error and potentially denying after thresholds
                    return Mono.just(true);
                });
    }
}
