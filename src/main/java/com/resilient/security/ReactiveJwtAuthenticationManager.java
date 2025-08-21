package com.resilient.security;

import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * ReactiveJwtAuthenticationManager validates JWT tokens and converts them to Spring Security
 * Authentication objects.
 *
 * <p>Integrates with Spring Security for reactive authentication. Extracts JWT tokens from the
 * Authorization header, validates them, parses user roles for role-based access control, and checks
 * issuer/audience.
 *
 * <p>Key points: - Uses a bounded elastic scheduler for blocking JWT parsing operations. - Handles
 * errors gracefully and logs JWT processing issues. - Implements both ReactiveAuthenticationManager
 * and ServerAuthenticationConverter for WebFlux.
 */
@Component
public class ReactiveJwtAuthenticationManager implements ReactiveAuthenticationManager, ServerAuthenticationConverter {

    private static final Logger log = LoggerFactory.getLogger(ReactiveJwtAuthenticationManager.class);

    private final JwtUtil jwtUtil;
    private final Scheduler authScheduler;
    private final TokenBlacklistService blacklistService;

    private final String expectedIssuer;
    private final List<String> allowedAudiences;

    @Autowired
    public ReactiveJwtAuthenticationManager(
            JwtUtil jwtUtil,
            @Qualifier("authScheduler") Scheduler authScheduler,
            @Autowired(required = false) TokenBlacklistService blacklistService,
            @Value("${security.jwt.issuer}") String expectedIssuer,
            @Value("${security.jwt.audience}") List<String> allowedAudiences) {

        this.jwtUtil = Objects.requireNonNull(jwtUtil, "jwtUtil must not be null");
        this.authScheduler = authScheduler != null ? authScheduler : Schedulers.boundedElastic();
        this.blacklistService = blacklistService;

        this.expectedIssuer = Objects.requireNonNull(expectedIssuer, "issuer must not be null")
                .trim();
        this.allowedAudiences = allowedAudiences != null
                ? allowedAudiences.stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList()
                : Collections.emptyList();
    }

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            return Mono.empty();
        }
        String token = authHeader.substring(7).trim();
        if (!StringUtils.hasText(token)) {
            return Mono.empty();
        }
        return Mono.just(token)
                .publishOn(authScheduler)
                .flatMap(this::checkBlacklistThenValidate)
                .onErrorResume(ex -> {
                    log.warn("JWT processing error: {}", ex.getMessage());
                    return Mono.empty();
                })
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(ex -> {
                    log.error("JWT processing timeout or error: {}", ex.getMessage());
                    return Mono.empty();
                });
    }

    /** Checks blacklist (if enabled) before validating and creating Authentication. */
    private Mono<Authentication> checkBlacklistThenValidate(String token) {
        if (blacklistService != null) {
            return blacklistService.isTokenBlacklisted(token).flatMap(isBlacklisted -> {
                if (isBlacklisted) {
                    log.debug("Rejected blacklisted token");
                    return Mono.empty();
                }
                return validateAndCreateAuth(token);
            });
        }
        return validateAndCreateAuth(token);
    }

    /** Validates JWT, checks issuer/audience, parses roles, and returns Authentication. */
    private Mono<Authentication> validateAndCreateAuth(String token) {
        try {
            String username = jwtUtil.extractUsername(token);
            if (!StringUtils.hasText(username) || !jwtUtil.validateToken(token, username)) {
                return Mono.empty();
            }
            Claims claims = jwtUtil.extractAllClaims(token);
            if (!validateIssuerAndAudience(claims)) {
                return Mono.empty();
            }
            Object rawRoles = claims.get("roles");
            List<String> roles;
            if (rawRoles instanceof List<?>) {
                roles = ((List<?>) rawRoles)
                        .stream()
                                .filter(Objects::nonNull)
                                .map(Object::toString)
                                .collect(Collectors.toUnmodifiableList());
            } else if (rawRoles instanceof String) {
                String strRoles = ((String) rawRoles).trim();
                if (strRoles.contains(",")) {
                    roles = List.of(strRoles.split(","));
                } else {
                    roles = List.of(strRoles);
                }
            } else {
                roles = Collections.emptyList();
            }
            roles = roles.stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableList());
            List<SimpleGrantedAuthority> authorities =
                    roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toUnmodifiableList());
            User userDetails = new User(username, "[PROTECTED]", authorities);
            return Mono.just(new UsernamePasswordAuthenticationToken(userDetails, null, authorities));
        } catch (Exception e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            return Mono.empty();
        }
    }

    /** Validates JWT issuer and audience claims. */
    private boolean validateIssuerAndAudience(Claims claims) {
        String issuer = claims.getIssuer();
        Object rawAudiences = claims.get("aud");
        List<String> audiences;
        if (rawAudiences instanceof List<?>) {
            audiences = ((List<?>) rawAudiences)
                    .stream().filter(Objects::nonNull).map(Object::toString).collect(Collectors.toUnmodifiableList());
        } else if (rawAudiences instanceof String) {
            String strAud = ((String) rawAudiences).trim();
            if (strAud.contains(",")) {
                audiences = List.of(strAud.split(","));
            } else {
                audiences = List.of(strAud);
            }
        } else {
            audiences = Collections.emptyList();
        }
        audiences =
                audiences.stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableList());
        // Validate issuer
        if (!expectedIssuer.equals(issuer)) {
            log.warn("JWT issuer mismatch: expected={}, found={}", expectedIssuer, issuer);
            return false;
        }
        // Validate audience
        if (audiences.isEmpty() || audiences.stream().noneMatch(allowedAudiences::contains)) {
            log.warn("JWT audience not allowed: {}", audiences);
            return false;
        }
        return true;
    }

    /** Authenticates the given Authentication object (pass-through). */
    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        return Mono.justOrEmpty(authentication);
    }
}
