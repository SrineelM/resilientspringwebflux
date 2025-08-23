package com.resilient.controller;

import com.resilient.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(
        properties = {
            "management.endpoint.health.validate-group-membership=false",
            "logging.level.org.springframework.security=DEBUG"
        })
class KafkaIntegrationControllerTest {
    @MockBean
    JwtUtil jwtUtil;

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void produce_happyPath() {
        // Use the utility class instead of duplicate setup
        JwtTestUtil.setupJwtMock(jwtUtil, "test-jwt-token", "testuser");
        
        webTestClient
                .post()
                .uri("/kafka/produce")
                .header("Authorization", "Bearer test-jwt-token")
                .bodyValue("{\"message\":\"test-message\"}")
                .exchange()
                .expectStatus()
                .isAccepted()
                .expectBody(String.class)
                .isEqualTo("Message produced (simulated): test-message");
    }

    @Test
    void consumeMessages_happyPath() {
        // Use the utility class instead of duplicate setup
        JwtTestUtil.setupJwtMock(jwtUtil, "test-jwt-token", "testuser");
        
        webTestClient
                .get()
                .uri("/kafka/consume")
                .header("Authorization", "Bearer test-jwt-token")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(String.class)
                .hasSize(10);
    }

    @Test
    void produce_invalidToken_shouldReturnUnauthorized() {
        // Simulate invalid JWT
        org.mockito.Mockito.when(jwtUtil.validateToken(org.mockito.Mockito.anyString(), org.mockito.Mockito.any())).thenReturn(false);

        webTestClient
                .post()
                .uri("/kafka/produce")
                .header("Authorization", "Bearer invalid-token")
                .bodyValue("test-message")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void consumeMessages_invalidToken_shouldReturnUnauthorized() {
        org.mockito.Mockito.when(jwtUtil.validateToken(org.mockito.Mockito.anyString(), org.mockito.Mockito.any())).thenReturn(false);

        webTestClient
                .get()
                .uri("/kafka/consume")
                .header("Authorization", "Bearer invalid-token")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }
    // ...existing code...
}
