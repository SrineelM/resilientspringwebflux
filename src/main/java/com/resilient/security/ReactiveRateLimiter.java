package com.resilient.security;

import reactor.core.publisher.Mono;

public interface ReactiveRateLimiter {
    // Common rate limiting methods
    Mono<Boolean> isAllowed(String key);
}
