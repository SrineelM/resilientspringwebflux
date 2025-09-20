
// Package for security components
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
 * ReactiveJwtAuthenticationManager validates JWT tokens and converts them to Spring Security Authentication objects.
 * <p>
 * Integrates with Spring Security for reactive authentication. Extracts JWT tokens from the Authorization header,
 * validates them, parses user roles for role-based access control, and checks issuer/audience and extended claims.
 * <p>
 * Key features:
 * <ul>
 *   <li>Uses a bounded elastic scheduler for blocking JWT parsing operations.</li>
 *   <li>Handles errors gracefully and logs JWT processing issues.</li>
 *   <li>Implements both ReactiveAuthenticationManager and ServerAuthenticationConverter for WebFlux.</li>
 *   <li>Supports token blacklisting for logout/revocation scenarios.</li>
 *   <li>Supports multiple audiences, client IDs, and token versioning.</li>
 * </ul>
 */
@Component

// Main authentication manager for JWT in a reactive (WebFlux) context
public class ReactiveJwtAuthenticationManager implements ReactiveAuthenticationManager, ServerAuthenticationConverter {


    // Logger for authentication events and errors
    private static final Logger log = LoggerFactory.getLogger(ReactiveJwtAuthenticationManager.class);


    // Utility for JWT parsing and validation
    private final JwtUtil jwtUtil;
    // Scheduler for offloading blocking JWT parsing
    private final Scheduler authScheduler;
    // Optional service for checking if a token is blacklisted
    private final TokenBlacklistService blacklistService;

    // Expected JWT issuer (from config)
    private final String expectedIssuer;
    // Allowed audiences for the JWT (from config)
    private final List<String> allowedAudiences;
    // Allowed client IDs for extended claim validation (from config)
    private final List<String> allowedClientIds;
    // Minimum token version for extended claim validation
    private final int minTokenVersion;


    /**
     * Constructor for dependency injection and configuration.
     *
     * @param jwtUtil Utility for JWT parsing/validation
     * @param authScheduler Scheduler for blocking JWT operations (can be null)
     * @param blacklistService Optional service for token blacklisting
     * @param expectedIssuer Expected JWT issuer (from config)
     * @param allowedAudiences Allowed JWT audiences (from config)
     * @param allowedClientIds Allowed client IDs for extended claim validation (from config)
     * @param minTokenVersion Minimum token version for extended claim validation
     */
    @Autowired
    public ReactiveJwtAuthenticationManager(
        JwtUtil jwtUtil,
        @Qualifier("authScheduler") Scheduler authScheduler,
        @Autowired(required = false) TokenBlacklistService blacklistService,
        @Value("${security.jwt.issuer}") String expectedIssuer,
        @Value("${security.jwt.audience}") List<String> allowedAudiences,
        @Value("${security.jwt.allowed-client-ids:}") List<String> allowedClientIds,
        @Value("${security.jwt.min-version:0}") int minTokenVersion) {

    // Validate and set required dependencies
    this.jwtUtil = Objects.requireNonNull(jwtUtil, "jwtUtil must not be null");
    // Use provided scheduler or default to bounded elastic for blocking operations
    this.authScheduler = authScheduler != null ? authScheduler : Schedulers.boundedElastic();
    // Optional blacklist service for token revocation
    this.blacklistService = blacklistService;

    // Validate and trim issuer configuration
    this.expectedIssuer = Objects.requireNonNull(expectedIssuer, "issuer must not be null").trim();
    // Process allowed audiences: trim, filter empty, and collect to immutable list
    this.allowedAudiences = allowedAudiences != null
        ? allowedAudiences.stream().map(String::trim).filter(s -> !s.isEmpty()).toList()
        : Collections.emptyList();
    // Process allowed client IDs similarly
    this.allowedClientIds = allowedClientIds != null
        ? allowedClientIds.stream().map(String::trim).filter(s -> !s.isEmpty()).toList()
        : Collections.emptyList();
    // Minimum token version for extended claims validation
    this.minTokenVersion = minTokenVersion;
    }


    /**
     * Converts a ServerWebExchange to a Spring Security Authentication object by extracting and validating a JWT.
     * <p>
     * This method is called by the WebFlux security filter chain.
     *
     * @param exchange The current server web exchange
     * @return Mono emitting a valid Authentication or empty if not authenticated
     */
    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        // Extract the Authorization header from the request
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (!StringUtils.hasText(authHeader)) {
            // No Authorization header present, return empty (no authentication)
            return Mono.empty();
        }
        final String bearerHeader = authHeader; // local non-null after hasText check
        if (bearerHeader == null || !bearerHeader.startsWith("Bearer ")) {
            // Header doesn't start with "Bearer ", invalid format
            return Mono.empty();
        }
        // Extract the token part after "Bearer "
        String token = bearerHeader.substring(7).trim();
        if (!StringUtils.hasText(token)) {
            // Token is empty after trimming
            return Mono.empty();
        }
        // Process the token on the auth scheduler to avoid blocking the event loop
        return Mono.just(token)
                .publishOn(authScheduler)
                .flatMap(this::checkBlacklistThenValidate)
                .onErrorResume(ex -> {
                    // Log JWT processing errors and return empty (authentication fails)
                    log.warn("JWT processing error: {}", ex.getMessage());
                    return Mono.empty();
                })
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(ex -> {
                    // Handle timeouts or other errors, log and fail authentication
                    log.error("JWT processing timeout or error: {}", ex.getMessage());
                    return Mono.empty();
                });
    }


    /**
     * Checks blacklist (if enabled) before validating and creating Authentication.
     *
     * @param token The JWT token to check and validate
     * @return Mono emitting Authentication if valid and not blacklisted, empty otherwise
     */
    private Mono<Authentication> checkBlacklistThenValidate(String token) {
        if (blacklistService != null) {
            // If blacklist service is available, check if token is blacklisted
            return blacklistService.isTokenBlacklisted(token).flatMap(isBlacklisted -> {
                if (isBlacklisted) {
                    // Token is blacklisted (e.g., logged out), reject authentication
                    log.debug("Rejected blacklisted token");
                    return Mono.empty();
                }
                // Token not blacklisted, proceed to validation
                return validateAndCreateAuth(token);
            });
        }
        // No blacklist service configured, skip check and validate directly
        return validateAndCreateAuth(token);
    }


    /**
     * Validates JWT, checks issuer/audience, parses roles, and returns Authentication.
     * <p>
     * This method performs all JWT validation steps, including signature, expiration, issuer, audience,
     * extended claims, and role extraction. If valid, returns a Spring Security Authentication object.
     *
     * @param token The JWT token to validate
     * @return Mono emitting Authentication if valid, empty otherwise
     */
    private Mono<Authentication> validateAndCreateAuth(String token) {
        try {
            // Extract username from JWT for basic validation
            String username = jwtUtil.extractUsername(token);
            // Perform structural validation (signature, expiration, etc.)
            boolean structuralValid = StringUtils.hasText(username)
                    && (jwtUtil.validateToken(token, username) || jwtUtil.validateWithRotation(token));
            if (!structuralValid) {
                // Token is structurally invalid, fail authentication
                return Mono.empty();
            }
            // Extract all claims from the token
            Claims claims = jwtUtil.extractAllClaims(token);
            // Validate issuer and audience claims
            if (!validateIssuerAndAudience(claims)) {
                // Issuer or audience mismatch, fail authentication
                return Mono.empty();
            }
            // Extended claims (client id, version, type) enforcement when configuration present
            List<String> clientIdsToEnforce = allowedClientIds.isEmpty() ? null : allowedClientIds;
            if (!jwtUtil.validateExtendedClaims(token, clientIdsToEnforce, minTokenVersion)) {
                // Extended claims validation failed, log and fail
                log.warn("JWT extended claims validation failed");
                return Mono.empty();
            }
            // Parse roles from claims (supports both List and comma-separated String)
            Object rawRoles = claims.get("roles");
            List<String> roles;
            if (rawRoles instanceof List<?>) {
                // Roles are provided as a list
                roles = ((List<?>) rawRoles)
                        .stream()
                                .filter(Objects::nonNull)
                                .map(Object::toString)
                                .collect(Collectors.toUnmodifiableList());
            } else if (rawRoles instanceof String) {
                // Roles are provided as a comma-separated string
                String strRoles = ((String) rawRoles).trim();
                if (strRoles.contains(",")) {
                    roles = List.of(strRoles.split(","));
                } else {
                    roles = List.of(strRoles);
                }
            } else {
                // No roles found, use empty list
                roles = Collections.emptyList();
            }
            // Clean up roles: trim and filter empty strings
            roles = roles.stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableList());
            // Convert roles to Spring Security authorities
            List<SimpleGrantedAuthority> authorities =
                    roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toUnmodifiableList());
            // Create UserDetails object with username and authorities (password is protected)
            User userDetails = new User(username, "[PROTECTED]", authorities);
            // Return authenticated token with user details and authorities
            return Mono.just(new UsernamePasswordAuthenticationToken(userDetails, null, authorities));
        } catch (Exception e) {
            // Any exception during validation, log and fail authentication
            log.warn("Invalid JWT: {}", e.getMessage());
            return Mono.empty();
        }
    }


    /**
     * Validates JWT issuer and audience claims.
     * <p>
     * Ensures the token was issued by the expected issuer and is intended for one of the allowed audiences.
     *
     * @param claims The JWT claims to validate
     * @return true if issuer and audience are valid, false otherwise
     */
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
        audiences = audiences.stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableList());
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


    /**
     * Authenticates the given Authentication object (pass-through).
     * <p>
     * This method is required by the ReactiveAuthenticationManager interface. In this implementation,
     * it simply returns the provided Authentication object as-is, since all validation is done in convert().
     *
     * @param authentication The authentication object to authenticate
     * @return Mono emitting the same authentication object if not null
     */
    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        return Mono.justOrEmpty(authentication);
    }
}
