package com.resilient.security;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TokenBlacklistServiceTest {
    static class TestTokenBlacklistService extends TokenBlacklistService {
        private final Set<String> blacklist = new HashSet<>();

        @Override
        public Mono<Void> blacklistToken(String token, Duration ttl) {
            blacklist.add(token);
            return Mono.empty();
        }

        @Override
        public Mono<Boolean> isTokenBlacklisted(String token) {
            return Mono.just(blacklist.contains(token));
        }
    }

    @Test
    void blacklistToken_happyPath() {
        TestTokenBlacklistService service = new TestTokenBlacklistService();
        StepVerifier.create(service.blacklistToken("token123", Duration.ofMinutes(10)))
                .verifyComplete();
        StepVerifier.create(service.isTokenBlacklisted("token123"))
                .expectNext(true)
                .verifyComplete();
    }
}
