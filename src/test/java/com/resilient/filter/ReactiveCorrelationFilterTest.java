package com.resilient.filter;

import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ReactiveCorrelationFilterTest {
    @Mock
    ServerWebExchange exchange;

    @Mock
    WebFilterChain chain;

    ReactiveCorrelationFilter filter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        filter = new ReactiveCorrelationFilter();
        when(chain.filter(exchange)).thenReturn(Mono.empty());
    }

    @Test
    void filter_happyPath() {
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    }
}
