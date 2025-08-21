package com.resilient.security;

import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RateLimitingWebFilterTest {
    @Mock
    RedisReactiveRateLimiter rateLimiter;

    @Mock
    ServerWebExchange exchange;

    @Mock
    WebFilterChain chain;

    @Mock
    ServerHttpRequest request;

    RateLimitingWebFilter filter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        filter = new RateLimitingWebFilter(rateLimiter);
        when(exchange.getRequest()).thenReturn(request);
        when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));
    }

    @Test
    void filter_happyPath_allowed() {
        when(rateLimiter.isAllowed("127.0.0.1")).thenReturn(Mono.just(true));
        when(chain.filter(exchange)).thenReturn(Mono.empty());
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    }

    @Test
    void filter_rateLimited_denied() {
        when(rateLimiter.isAllowed("127.0.0.1")).thenReturn(Mono.just(false));
        StepVerifier.create(filter.filter(exchange, chain))
                .expectErrorMatches(e -> e instanceof RuntimeException)
                .verify();
    }
}
