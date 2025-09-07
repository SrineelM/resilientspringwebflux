package com.resilient.rate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RateLimiterTest {
    @LocalServerPort
    int port;

    @Value("${test.ratelimiter.uri:/public/ping}")
    String testUri;

    @Test
    void excessiveRequestsShouldTriggerLimit() {
        WebClient wc = WebClient.builder().baseUrl("http://localhost:" + port).build();
        // Fire a burst of requests; depending on limiter config might get 429 or all 200 in test profile.
        StepVerifier.create(Flux.range(0, 10)
                        .flatMap(i -> wc.get()
                                .uri(testUri)
                                .exchangeToMono(
                                        resp -> Mono.just(resp.statusCode().value())))
                        .collectList())
                .assertNext(list -> {
                    long tooMany = list.stream().filter(code -> code == 429).count();
                    long ok = list.stream().filter(code -> code == 200).count();
                    if (tooMany == 0) throw new AssertionError("Expected at least one 429 status");
                    if (ok == 0) throw new AssertionError("Expected some 200 statuses");
                })
                .verifyComplete();
    }
}
