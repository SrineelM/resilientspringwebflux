package com.resilient.security;

import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

public class RateLimitingWebFilter implements WebFilter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingWebFilter.class);
    private final RedisReactiveRateLimiter rateLimiter;

    public RateLimitingWebFilter(RedisReactiveRateLimiter rateLimiter) {
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "RedisReactiveRateLimiter cannot be null");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        final String ip = Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(java.net.InetSocketAddress::getAddress)
                .map(java.net.InetAddress::getHostAddress)
                .orElse("unknown");

        return rateLimiter.isAllowed(ip).flatMap(allowed -> {
            if (Boolean.TRUE.equals(allowed)) {
                return chain.filter(exchange);
            }
            logger.warn("Rate limit exceeded for IP: {}", ip);
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        });
    }
}
