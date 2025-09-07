package com.resilient.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.test.StepVerifier;

class ReactiveJwtAuthenticationManagerTest {
    @Mock
    JwtUtil jwtUtil;

    @Mock
    TokenBlacklistService blacklistService;

    @Mock
    Scheduler scheduler;

    @Mock
    Claims claims;

    ReactiveJwtAuthenticationManager manager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        manager = new ReactiveJwtAuthenticationManager(
                jwtUtil,
                scheduler,
                blacklistService,
                "issuer", // expectedIssuer
                List.of("aud"), // allowedAudiences
                List.of(), // allowedClientIds
                0 // minTokenVersion
                );
    }

    @Test
    @DisplayName("convert should return authenticated principal for valid token")
    void convert_happyPath() {
        String token = "validToken";
        String username = "testUser";

        // Mock JWT behavior
        when(jwtUtil.extractUsername(token)).thenReturn(username);
        when(jwtUtil.validateToken(token, username)).thenReturn(true);
        when(jwtUtil.validateWithRotation(token)).thenReturn(false);
        when(jwtUtil.extractAllClaims(token)).thenReturn(claims);
        when(jwtUtil.validateExtendedClaims(any(), any(), any())).thenReturn(true);

        when(claims.getIssuer()).thenReturn("issuer");
        when(claims.get("aud")).thenReturn("aud");
        when(claims.get("roles")).thenReturn(List.of("ROLE_USER"));

        when(blacklistService.isTokenBlacklisted(token)).thenReturn(Mono.just(false));

        // Mock request with Authorization header
        ServerWebExchange exchange = MockServerWebExchange.from(
                org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/api/test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

        StepVerifier.create(manager.convert(exchange))
                .expectNextMatches(auth -> {
                    if (!(auth.getPrincipal() instanceof User user)) {
                        return false;
                    }
                    return user.getUsername().equals(username)
                            && auth.isAuthenticated()
                            && auth.getAuthorities().stream()
                                    .anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("convert should return empty when Authorization header is missing")
    void convert_whenNoAuthHeader_shouldReturnEmpty() {
        // Mock a request without an Authorization header
        ServerWebExchange exchange = MockServerWebExchange.from(
                org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/api/test"));

        StepVerifier.create(manager.convert(exchange)).verifyComplete();
    }
}
