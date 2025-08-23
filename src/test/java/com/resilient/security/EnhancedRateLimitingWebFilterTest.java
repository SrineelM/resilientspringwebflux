package com.resilient.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnhancedRateLimitingWebFilterTest {
    @Mock ReactiveRateLimiter limiter;
    @Mock WebFilterChain chain;

    @InjectMocks EnhancedRateLimitingWebFilter filter;

    @Test
    void passesWhenAllowed() {
        when(limiter.isAllowed(any())).thenReturn(Mono.just(true));
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    }
}
