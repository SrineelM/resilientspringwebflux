/**
 * Interface for reactive rate limiting implementations.
 * <p>
 * Used to abstract over different rate limiter backends (e.g., Redis, in-memory) in a non-blocking way.
 * Implementations should return a Mono indicating if the request for a given key is allowed.
 */
package com.resilient.security;

import reactor.core.publisher.Mono;

public interface ReactiveRateLimiter {
    /**
     * Checks if a request for the given key is allowed under the current rate limit.
     *
     * @param key The user or IP key to check
     * @return Mono emitting true if allowed, false if rate limit exceeded
     */
    Mono<Boolean> isAllowed(String key);
}
