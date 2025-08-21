package com.resilient.security;

import java.time.Duration;
import reactor.core.publisher.Mono;

/**
 * Base class for blacklisting JWT tokens.
 *
 * <p>Concrete implementations should provide storage-specific logic.
 */
public abstract class TokenBlacklistService {

    /**
     * Blacklists a token for the specified TTL.
     *
     * @param token JWT token string
     * @param ttl how long the token should remain blacklisted
     * @return Mono signaling completion
     */
    public abstract Mono<Void> blacklistToken(String token, Duration ttl);

    /**
     * Checks if a token is currently blacklisted.
     *
     * @param token JWT token string
     * @return Mono emitting true if blacklisted, false otherwise
     */
    public abstract Mono<Boolean> isTokenBlacklisted(String token);
}
