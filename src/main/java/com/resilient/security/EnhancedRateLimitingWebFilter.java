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
 * Enhanced rate limiting supporting authenticated user key first, then IP fallback.
 * Works with generic {@link ReactiveRateLimiter} implementation (Redis/InMemory).
 */
public class EnhancedRateLimitingWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(EnhancedRateLimitingWebFilter.class);
    private final ReactiveRateLimiter rateLimiter;

    public EnhancedRateLimitingWebFilter(ReactiveRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

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

    private Mono<String> resolveKey(ServerWebExchange exchange) {
        Mono<String> userKey = ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(auth -> "user:" + auth.getName());

        String ip = Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(addr -> addr.getHostAddress())
                .orElse("unknown");
        Mono<String> ipKey = Mono.just("ip:" + ip);

        return userKey.switchIfEmpty(ipKey);
    }
}
