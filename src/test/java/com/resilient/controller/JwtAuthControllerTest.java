package com.resilient.controller;

import com.resilient.config.TestSecurityConfig;
import com.resilient.security.JwtUtil;
import io.jsonwebtoken.Claims;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = JwtAuthController.class)
@Import(TestSecurityConfig.class)
class JwtAuthControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    JwtUtil jwtUtil;

    @MockBean
    PasswordEncoder passwordEncoder;

    @MockBean
    com.resilient.security.TokenBlacklistService tokenBlacklistService;

    @MockBean
    @org.springframework.beans.factory.annotation.Qualifier("authScheduler")
    reactor.core.scheduler.Scheduler authScheduler;

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
                .expectStatus().isOk();
    }

    @Test
    void login_invalidCredentials() {
        when(passwordEncoder.matches(any(), any())).thenReturn(false);
        webTestClient
                .post()
                .uri("/api/auth/login")
                .bodyValue("{\"username\":\"user\",\"password\":\"wrong\"}")
                .exchange()
                .expectStatus().isUnauthorized();
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
                .expectStatus().isOk();
    }
}
