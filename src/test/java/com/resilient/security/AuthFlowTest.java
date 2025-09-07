package com.resilient.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthFlowTest {
    @LocalServerPort
    int port;

    @Test
    void unauthenticatedAccessProtectedReturns401() {
        WebClient wc = WebClient.builder().baseUrl("http://localhost:" + port).build();
        StepVerifier.create(wc.get().uri("/secure/unknown").retrieve().bodyToMono(String.class))
                .expectErrorMatches(
                        err -> err.getMessage() != null && err.getMessage().contains("401"))
                .verify();
    }
}
