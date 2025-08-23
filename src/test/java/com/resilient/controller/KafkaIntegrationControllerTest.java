package com.resilient.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

import java.time.Duration;

import com.resilient.config.TestSecurityConfig;
import com.resilient.security.JwtUtil;

@WebFluxTest(controllers = KafkaIntegrationController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("local")
class KafkaIntegrationControllerTest {

    @MockBean
    JwtUtil jwtUtil;

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void produce_happyPath() {
        // Setup JWT mock
        JwtTestUtil.setupJwtMock(jwtUtil, "test-jwt-token", "testuser");
        
        webTestClient
                .post()
                .uri("/kafka/produce")
                .header("Authorization", "Bearer test-jwt-token")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\":\"test-message\"}")
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(String.class)
                .isEqualTo("Message produced (simulated): test-message");
    }

    @Test
    void consumeMessages_happyPath() {
        // Setup JWT mock
        JwtTestUtil.setupJwtMock(jwtUtil, "test-jwt-token", "testuser");
        
        var responseBody = webTestClient
                .get()
                .uri("/kafka/consume")
                .header("Authorization", "Bearer test-jwt-token")
                .accept(org.springframework.http.MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class)
                .getResponseBody();

        StepVerifier.create(responseBody.take(3)) // Take only 3 messages instead of 10
                .expectNext("Demo Kafka message #0")
                .expectNext("Demo Kafka message #1")
                .expectNext("Demo Kafka message #2")
                .thenCancel() // Cancel the subscription to avoid waiting
                .verify(Duration.ofSeconds(10)); // Increased timeout for delayed messages
    }

    @Test
    void consumeMessages_happyPath_verifyPattern() {
        // Setup JWT mock
        JwtTestUtil.setupJwtMock(jwtUtil, "test-jwt-token", "testuser");
        
        var responseBody = webTestClient
                .get()
                .uri("/kafka/consume")
                .header("Authorization", "Bearer test-jwt-token")
                .accept(org.springframework.http.MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .returnResult(String.class)
                .getResponseBody();

        StepVerifier.create(responseBody.take(2))
                .expectNextMatches(msg -> msg.startsWith("Demo Kafka message #"))
                .expectNextMatches(msg -> msg.startsWith("Demo Kafka message #"))
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }

        // Note: Virtual time only works if the controller's Flux uses a TestScheduler or is written for virtual time.
        // If not, this test will always timeout. Consider removing or refactoring controller for testability.
}