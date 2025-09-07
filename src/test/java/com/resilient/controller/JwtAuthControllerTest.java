package com.resilient.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.resilient.config.TestSecurityConfig;
import com.resilient.security.JwtUtil;
import com.resilient.security.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@WebFluxTest(controllers = JwtAuthController.class)
@Import(TestSecurityConfig.class)
@TestPropertySource(properties = {"auth.demo.user=user", "auth.demo.pass-hash=hashed-pass"})
class JwtAuthControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    JwtUtil jwtUtil;

    @MockBean
    PasswordEncoder passwordEncoder;

    @MockBean
    TokenBlacklistService tokenBlacklistService;

    @MockBean
    @org.springframework.beans.factory.annotation.Qualifier("authScheduler")
    Scheduler authScheduler;

    @Test
    void login_happyPath() {
        // Password validation
        when(passwordEncoder.matches("pass", "hashed-pass")).thenReturn(true);

        // JWT generation & claims
        when(jwtUtil.generateToken(any(), any())).thenReturn("jwt-token");
        when(jwtUtil.getExpiration("jwt-token")).thenReturn(new Date(System.currentTimeMillis() + 1000000));

        Claims claims = Mockito.mock(Claims.class);
        when(claims.getSubject()).thenReturn("user");
        when(claims.getIssuer()).thenReturn("https://auth.dev.resilient.com");
        when(claims.get("aud")).thenReturn(List.of("resilient-app", "admin-portal"));
        when(claims.getAudience()).thenReturn(Set.of("resilient-app", "admin-portal"));
        when(claims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 1000000));
        when(claims.get("roles")).thenReturn(List.of("ROLE_USER"));
        when(claims.get("roles", List.class)).thenReturn(List.of("ROLE_USER"));
        when(claims.get("roles", String.class)).thenReturn("ROLE_USER");
        when(jwtUtil.extractAllClaims(any())).thenReturn(claims);

        webTestClient
                .post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"user\",\"password\":\"pass\"}")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.token")
                .isEqualTo("jwt-token");
    }

    @Test
    void login_invalidCredentials() {
        // Password mismatch
        when(passwordEncoder.matches("wrong", "hashed-pass")).thenReturn(false);

        webTestClient
                .post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"user\",\"password\":\"wrong\"}")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.error")
                .isEqualTo("Invalid credentials");
    }

    @Test
    void logout_happyPath() {
        when(jwtUtil.validateToken(any(), any())).thenReturn(true);
        when(jwtUtil.extractUsername(any())).thenReturn("user");

        Claims claims = Mockito.mock(Claims.class);
        when(claims.getSubject()).thenReturn("user");
        when(claims.getIssuer()).thenReturn("https://auth.dev.resilient.com");
        when(claims.get("aud")).thenReturn(List.of("resilient-app", "admin-portal"));
        when(claims.getAudience()).thenReturn(Set.of("resilient-app", "admin-portal"));
        when(claims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 1000000));
        when(claims.get("roles")).thenReturn(List.of("ROLE_USER"));
        when(claims.get("roles", List.class)).thenReturn(List.of("ROLE_USER"));
        when(claims.get("roles", String.class)).thenReturn("ROLE_USER");
        when(jwtUtil.extractAllClaims(any())).thenReturn(claims);

        // Blacklist stub
        when(jwtUtil.getRemainingValidity(any())).thenReturn(java.time.Duration.ofSeconds(60));
        when(tokenBlacklistService.blacklistToken(any(), any())).thenReturn(Mono.empty());

        webTestClient
                .post()
                .uri("/api/auth/logout")
                .header("Authorization", "Bearer jwt-token")
                .exchange()
                .expectStatus()
                .isNoContent();
    }
}
