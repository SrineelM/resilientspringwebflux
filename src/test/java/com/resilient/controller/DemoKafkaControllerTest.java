package com.resilient.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
class DemoKafkaControllerTest {
    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    JwtUtil jwtUtil;

    @Test
    void produce_happyPath() {
        // Simplified with utility class
        JwtTestUtil.setupJwtMock(jwtUtil, "test-jwt-token", "testuser");
        
        webTestClient
                .post()
                .uri("/kafka/produce")
                .header("Authorization", "Bearer test-jwt-token")
                .bodyValue("test-message")
                .exchange()
                .expectStatus()
                .isAccepted()
                .expectBody(String.class)
                .isEqualTo("Message produced (simulated): test-message");
    }

    @Test
    void consumeMessages_happyPath() {
        // Simplified with utility class
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
        when(claims.get("roles")).thenReturn(java.util.List.of("ROLE_USER"));
        when(jwtUtil.extractAllClaims(org.mockito.ArgumentMatchers.eq("test-jwt-token")))
                .thenReturn(claims);
        when(jwtUtil.extractUsername(org.mockito.ArgumentMatchers.eq("test-jwt-token")))
                .thenReturn("testuser");
        when(jwtUtil.validateToken(org.mockito.ArgumentMatchers.eq("test-jwt-token"), any()))
                .thenReturn(true);
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
