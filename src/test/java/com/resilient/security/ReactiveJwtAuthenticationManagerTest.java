package com.resilient.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
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

    ReactiveJwtAuthenticationManager manager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        manager = new ReactiveJwtAuthenticationManager(
                jwtUtil, scheduler, blacklistService, "issuer", Collections.singletonList("aud"));
    }

    @Test
    void authenticate_happyPath() {
        Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("user", "token");
        when(jwtUtil.validateToken(any(), any())).thenReturn(true);

        when(blacklistService.isTokenBlacklisted(any())).thenReturn(Mono.just(false));
        StepVerifier.create(manager.authenticate(auth))
                .expectNextMatches(a -> a.isAuthenticated())
                .verifyComplete();
    }
}
