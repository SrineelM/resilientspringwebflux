package com.resilient.security;

import java.net.InetSocketAddress;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * An enhanced {@link WebFilter} that applies rate limiting with a smart key resolution strategy.
 * <p>
 * This filter protects the application from excessive requests by first attempting to rate-limit
 * based on the authenticated user's principal name. If the user is not authenticated, it falls
 * back to rate-limiting by the client's IP address. This provides more precise control over
 * authenticated traffic while still protecting public endpoints.
 * <p>
 * It works with any generic {@link ReactiveRateLimiter} implementation (e.g., in-memory for
 * development, Redis for production).
 */
public class EnhancedRateLimitingWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(EnhancedRateLimitingWebFilter.class);
    private final ReactiveRateLimiter rateLimiter;

    /**
     * Constructs the filter with a specific rate-limiting implementation.
     *
     * @param rateLimiter The reactive rate limiter service (e.g., {@link RedisReactiveRateLimiter}
     *                    or {@link InMemoryReactiveRateLimiter}).
     */
    public EnhancedRateLimitingWebFilter(ReactiveRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    /**
     * Intercepts incoming requests to apply rate-limiting logic.
     *
     * @param exchange The current server web exchange.
     * @param chain    The filter chain to pass control to the next filter.
     * @return A {@link Mono} that completes when the request is processed or is rejected due to
     *         rate limiting.
     */
    @Override
    public @NonNull Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        return resolveKey(exchange).flatMap(key -> rateLimiter.isAllowed(key).flatMap(allowed -> {
            if (Boolean.TRUE.equals(allowed)) {
                return chain.filter(exchange);
            }
            log.warn("Rate limit exceeded for key={}", key);
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }));
    }

    /**
     * Resolves the key to be used for rate limiting.
     * <p>
     * It first attempts to get a key from the authenticated user's principal. If the security
     * context is empty or the user is not authenticated, it falls back to using the request's
     * remote IP address.
     *
     * @param exchange The current server web exchange.
     * @return A {@link Mono} emitting the resolved key (e.g., "user:john.doe" or "ip:127.0.0.1").
     */
    private Mono<String> resolveKey(ServerWebExchange exchange) {
        // Attempt to get the user's principal name from the reactive security context.
        Mono<String> userKey = ReactiveSecurityContextHolder.getContext()
                // Get the Authentication object from the context.
                .map(ctx -> ctx.getAuthentication())
                // Only proceed if the user is actually authenticated.
                .filter(Authentication::isAuthenticated)
                // Create a rate-limiting key prefixed with "user:".
                .map(auth -> "user:" + auth.getName());

        // As a fallback, determine the client's IP address.
        String ip = Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(addr -> addr.getHostAddress())
                .orElse("unknown");
        // Create a rate-limiting key prefixed with "ip:".
        Mono<String> ipKey = Mono.just("ip:" + ip);

        // Prefer the user-based key, but switch to the IP-based key if the user key Mono is empty.
        return userKey.switchIfEmpty(ipKey);
    }
}
