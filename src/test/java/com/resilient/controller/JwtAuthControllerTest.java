// ...existing code...
// ...existing code...
package com.resilient.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.resilient.security.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
// ...existing code...

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(
        properties = {
            "management.endpoint.health.validate-group-membership=false",
            "logging.level.org.springframework.security=DEBUG"
        })
class JwtAuthControllerTest {
    @MockBean
    com.resilient.security.TokenBlacklistService tokenBlacklistService;

    @MockBean
    @org.springframework.beans.factory.annotation.Qualifier("authScheduler")
    reactor.core.scheduler.Scheduler authScheduler;

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    JwtUtil jwtUtil;

    @MockBean
    PasswordEncoder passwordEncoder;

    @Test
    void login_happyPath() {
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        JwtTestUtil.setupTokenGeneration(jwtUtil, "user", "jwt-token");
        JwtTestUtil.setupJwtMock(jwtUtil, "jwt-token", "user");
        Claims claims = Mockito.mock(Claims.class);
        when(claims.getSubject()).thenReturn("user");
        when(claims.getIssuer()).thenReturn("https://auth.dev.resilient.com");
        when(claims.get("aud")).thenReturn(java.util.List.of("resilient-app", "admin-portal"));
        when(claims.getAudience()).thenReturn(java.util.Set.of("resilient-app", "admin-portal"));
        when(claims.getExpiration()).thenReturn(new java.util.Date(System.currentTimeMillis() + 1000000));
        when(claims.get("roles")).thenReturn(java.util.List.of("ROLE_USER"));
        when(claims.get("roles", java.util.List.class)).thenReturn(java.util.List.of("ROLE_USER"));
        when(claims.get("roles", String.class)).thenReturn("ROLE_USER");
        when(jwtUtil.extractAllClaims(any())).thenReturn(claims);
        webTestClient
                .post()
                .uri("/api/auth/login")
                .bodyValue("{\"username\":\"user\",\"password\":\"pass\"}")
                .exchange()
                .expectStatus()
                .isOk(); // Token returned, body can be checked if needed
    }

    @Test
    void login_invalidCredentials() {
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        webTestClient
                .post()
                .uri("/api/auth/login")
                .bodyValue("{\"username\":\"user\",\"password\":\"wrong\"}")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void logout_happyPath() {
        when(jwtUtil.validateToken(any(), any())).thenReturn(true);
        when(jwtUtil.extractUsername(any())).thenReturn("user");
        Claims claims = Mockito.mock(Claims.class);
        when(claims.getSubject()).thenReturn("user");
        when(claims.getIssuer()).thenReturn("https://auth.dev.resilient.com");
        when(claims.get("aud")).thenReturn(java.util.List.of("resilient-app", "admin-portal"));
        when(claims.getAudience()).thenReturn(java.util.Set.of("resilient-app", "admin-portal"));
        when(claims.getExpiration()).thenReturn(new java.util.Date(System.currentTimeMillis() + 1000000));
        when(claims.get("roles")).thenReturn(java.util.List.of("ROLE_USER"));
        when(claims.get("roles", java.util.List.class)).thenReturn(java.util.List.of("ROLE_USER"));
        when(claims.get("roles", String.class)).thenReturn("ROLE_USER");
        when(jwtUtil.extractAllClaims(any())).thenReturn(claims);
        webTestClient
                .post()
                .uri("/api/auth/logout")
                .header("Authorization", "Bearer jwt-token")
                .exchange()
                .expectStatus()
                .isOk();
    }
}
