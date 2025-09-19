package com.resilient.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * In-memory implementation of token blacklist for development profile.
 * <p>
 * Stores blacklisted JWT tokens in a concurrent map with expiry timestamps. Not suitable for production.
 * Used to simulate logout/session invalidation in dev/test environments.
 */
@Service
@Profile("dev")
public class InMemoryTokenBlacklistService extends TokenBlacklistService {

    /**
     * Record representing a blacklisted token with its expiry time.
     */
    private record BlacklistEntry(Instant expiry) {}

    private final Map<String, BlacklistEntry> blacklist = new ConcurrentHashMap<>();

    /**
     * Adds a token to the blacklist with a time-to-live (TTL).
     *
     * @param token The JWT token to blacklist
     * @param ttl The duration until the token expires from the blacklist
     * @return Mono that completes when the operation is done
     */
    @Override
    public Mono<Void> blacklistToken(String token, Duration ttl) {
        blacklist.put(token, new BlacklistEntry(Instant.now().plus(ttl)));
        return Mono.empty();
    }

    /**
     * Checks if a token is currently blacklisted.
     *
     * @param token The JWT token to check
     * @return Mono emitting true if blacklisted, false otherwise
     */
    @Override
    public Mono<Boolean> isTokenBlacklisted(String token) {
        BlacklistEntry entry = blacklist.get(token);
        if (entry != null && entry.expiry.isAfter(Instant.now())) {
            return Mono.just(true);
        }
        blacklist.remove(token); // cleanup expired
        return Mono.just(false);
    }
}
