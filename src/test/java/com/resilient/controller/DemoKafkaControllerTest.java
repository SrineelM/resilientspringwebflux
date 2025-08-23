package com.resilient.controller;

import com.resilient.config.TestSecurityConfig;
import com.resilient.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = DemoKafkaController.class)
@Import(TestSecurityConfig.class)
@org.springframework.test.context.ActiveProfiles("local")
class DemoKafkaControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    JwtUtil jwtUtil;

    @Test
    void produce_happyPath() {
        JwtTestUtil.setupJwtMock(jwtUtil, "test-jwt-token", "testuser");
        webTestClient
                .post()
                .uri("/kafka/produce")
                .header("Authorization", "Bearer test-jwt-token")
                .bodyValue("test-message")
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(String.class)
                .isEqualTo("Message produced (simulated): test-message");
    }

    @Test
    void consumeMessages_happyPath() {
    JwtTestUtil.setupJwtMock(jwtUtil, "test-jwt-token", "testuser");

    reactor.core.publisher.Flux<String> responseBody = webTestClient
        .get()
        .uri("/kafka/consume")
        .header("Authorization", "Bearer test-jwt-token")
        .accept(org.springframework.http.MediaType.TEXT_EVENT_STREAM)
        .exchange()
        .returnResult(String.class)
        .getResponseBody();

    reactor.test.StepVerifier.create(responseBody.take(10))
        .expectNextCount(10)
        .verifyComplete();
    }
    // ...existing code...
}
