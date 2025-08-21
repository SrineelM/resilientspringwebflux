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
        Mockito.when(jwtUtil.validateToken(Mockito.anyString(), Mockito.any())).thenReturn(true);
        Mockito.when(jwtUtil.extractUsername(Mockito.anyString())).thenReturn("testuser");
        Claims claims = Mockito.mock(Claims.class);
        Mockito.when(claims.getSubject()).thenReturn("testuser");
        Mockito.when(claims.getIssuer()).thenReturn("https://auth.dev.resilient.com");
        Mockito.when(claims.get("aud")).thenReturn(java.util.List.of("resilient-app", "admin-portal"));
        Mockito.when(claims.getExpiration()).thenReturn(new java.util.Date(System.currentTimeMillis() + 1000000));
        Mockito.when(claims.get("roles")).thenReturn(java.util.List.of("ROLE_USER"));
        Mockito.when(jwtUtil.extractAllClaims(Mockito.anyString())).thenReturn(claims);
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
        Mockito.when(jwtUtil.validateToken(Mockito.anyString(), Mockito.any())).thenReturn(true);
        Mockito.when(jwtUtil.extractUsername(Mockito.anyString())).thenReturn("testuser");
        Claims claims = Mockito.mock(Claims.class);
        Mockito.when(claims.getSubject()).thenReturn("testuser");
        Mockito.when(claims.getIssuer()).thenReturn("https://auth.dev.resilient.com");
        Mockito.when(claims.get("aud")).thenReturn(java.util.List.of("resilient-app", "admin-portal"));
        Mockito.when(claims.getExpiration()).thenReturn(new java.util.Date(System.currentTimeMillis() + 1000000));
        Mockito.when(claims.get("roles")).thenReturn(java.util.List.of("ROLE_USER"));
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
