package com.resilient.security;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class InMemoryTokenBlacklistServiceTest {
    @Test
    void blacklistToken_happyPath() {
        InMemoryTokenBlacklistService service = new InMemoryTokenBlacklistService();
        StepVerifier.create(service.blacklistToken("token123", Duration.ofMinutes(10)))
                .verifyComplete();
        StepVerifier.create(service.isTokenBlacklisted("token123"))
                .expectNext(true)
                .verifyComplete();
    }
}
