package com.resilient.controller;

import com.resilient.security.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
}
        Mockito.when(jwtUtil.extractAllClaims(Mockito.anyString())).thenReturn(claims);
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
}
