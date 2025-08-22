package com.resilient.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.server.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(
        properties = {
            "management.endpoint.health.validate-group-membership=false",
            "logging.level.org.springframework.security=DEBUG"
        })
class ReactiveStreamControllerTest {
    @Autowired
    private WebTestClient webTestClient;

    @Test
    void streamUsersSse_happyPath() {
        webTestClient
                .get()
                .uri("/stream/sse/users")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(Object.class)
                .hasSize(10);
    }

    @Test
    void streamUsersSse_validation() {
        Flux<ServerSentEvent<UserResponse>> eventFlux = webTestClient
                .get()
                .uri("/stream/sse/users")
                .exchange()
                .expectStatus().isOk()
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<UserResponse>>() {})
                .getResponseBody();

        StepVerifier.create(eventFlux.take(5))
                .expectNextCount(5)
                .verifyComplete();
    }
}
