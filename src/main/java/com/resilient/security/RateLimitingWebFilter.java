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

/**
 * WebFilter for applying Redis-backed rate limiting by client IP address.
 * <p>
 * This filter checks if the incoming request's IP address is allowed by the RedisReactiveRateLimiter.
 * If the rate limit is exceeded, it returns HTTP 429 (Too Many Requests).
 * <p>
 * Used in production environments for distributed rate limiting.
 */
public class RateLimitingWebFilter implements WebFilter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingWebFilter.class);
    private final RedisReactiveRateLimiter rateLimiter;

    /**
     * Constructs the filter with a Redis-backed rate limiter.
     *
     * @param rateLimiter The RedisReactiveRateLimiter to use
     */
    public RateLimitingWebFilter(RedisReactiveRateLimiter rateLimiter) {
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "RedisReactiveRateLimiter cannot be null");
    }

    /**
     * Filters incoming requests, applying rate limiting by IP address.
     *
     * @param exchange The current server web exchange
     * @param chain The filter chain
     * @return Mono that completes when the request is processed or rejected
     */
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
