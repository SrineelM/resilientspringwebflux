package com.resilient.security;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class InMemoryReactiveRateLimiterTest {
    InMemoryReactiveRateLimiter limiter = new InMemoryReactiveRateLimiter();

    @Test
    void allowsBasic() {
        StepVerifier.create(limiter.isAllowed("k"))
                .expectNext(true)
                .verifyComplete();
    }
}
package com.resilient.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

@SpringBootTest
class InMemoryReactiveRateLimiterTest {
    @Autowired
    InMemoryReactiveRateLimiter rateLimiter;

    @Test
    void isAllowed_happyPath() {
        StepVerifier.create(rateLimiter.isAllowed("127.0.0.1")).expectNext(true).verifyComplete();
        for (int i = 1; i < 30; i++) {
            StepVerifier.create(rateLimiter.isAllowed("127.0.0.1"))
                    .expectNext(true)
                    .verifyComplete();
        }
        StepVerifier.create(rateLimiter.isAllowed("127.0.0.1"))
                .expectNext(false)
                .verifyComplete();
    }
}
