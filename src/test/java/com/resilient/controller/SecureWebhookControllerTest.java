package com.resilient.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.resilient.config.TestSecurityConfig;
import com.resilient.security.ReactiveRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest(controllers = SecureWebhookController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("dev")
class SecureWebhookControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ReactiveRateLimiter rateLimiter;

    @Test
    void processEvent_happyPath() {
        // Given
        when(rateLimiter.isAllowed(anyString())).thenReturn(Mono.just(true));

        // When & Then
        webTestClient
                .post()
                .uri("/api/webhook/event")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"test\": \"data\"}")
                .header("x-webhook-secret", "change-me")
                .header("x-webhook-signature", "valid-signature")
                .header("x-webhook-timestamp", String.valueOf(System.currentTimeMillis()))
                .exchange()
                .expectStatus()
                .isAccepted();
    }
}
