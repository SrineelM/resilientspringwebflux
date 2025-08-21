package com.resilient.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Profile("dev")
public class InMemoryTokenBlacklistService extends TokenBlacklistService {

    private record BlacklistEntry(Instant expiry) {}

    private final Map<String, BlacklistEntry> blacklist = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> blacklistToken(String token, Duration ttl) {
        blacklist.put(token, new BlacklistEntry(Instant.now().plus(ttl)));
        return Mono.empty();
    }

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
