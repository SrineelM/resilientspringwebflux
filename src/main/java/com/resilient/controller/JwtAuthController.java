package com.resilient.controller;

import com.resilient.security.JwtUtil;
import com.resilient.security.TokenBlacklistService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Handles JWT-based authentication and session management.
 *
 * <p>This controller provides endpoints for user login, secure logout via token blacklisting,
 * and token refreshing. It integrates with Spring Security and follows reactive principles.
 */
@RestController
@RequestMapping("/api/auth")
public class JwtAuthController {

    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final ReactiveUserDetailsService userDetailsService;
    private final TokenBlacklistService blacklistService; // abstract base with TTL

    /**
     * Constructs the controller with necessary security components.
     *
     * @param jwtUtil Utility for creating and validating JWTs.
     * @param passwordEncoder Service for encoding and verifying passwords.
     * @param userDetailsService Service to load user-specific data.
     * @param blacklistService Service to manage blacklisted (logged-out) tokens. This can be
     *                         an in-memory implementation for development or a distributed one
     *                         (like Redis) for production.
     */
    public JwtAuthController(
            JwtUtil jwtUtil,
            PasswordEncoder passwordEncoder,
            ReactiveUserDetailsService userDetailsService,
            TokenBlacklistService blacklistService // can be in-memory (dev) or Redis (prod)
            ) {
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
        this.blacklistService = blacklistService;
    }

    /**
     * Data Transfer Object for the login request body.
     * Ensures username and password are not blank.
     */
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    /**
     * Handles user login by validating credentials and issuing a JWT.
     *
     * @param req The login request containing the username and password.
     * @return A {@link Mono} containing a {@link ResponseEntity}. On success, it returns a 200 OK
     *         with the JWT and its expiration time. On failure, it returns a 401 Unauthorized.
     */
    @PostMapping("/login")
    public Mono<ResponseEntity<Map<String, Object>>> login(@Valid @RequestBody LoginRequest req) {
        // Find the user by their username from the user details service.
        return userDetailsService
                .findByUsername(req.username())
                // Filter the stream: only continue if the provided password matches the stored hash.
                .filter(ud -> passwordEncoder.matches(req.password(), ud.getPassword()))
                // If authentication is successful, map the user details to a response entity.
                .map(ud -> {
                    // Extract user roles (authorities) to include in the token.
                    List<String> roles = ud.getAuthorities().stream()
                            .map(a -> a.getAuthority())
                            .toList();
                    // Generate a new JWT for the user, including their roles as a claim.
                    String token = jwtUtil.generateToken(req.username(), Map.of("roles", roles));
                    // Prepare the response body.
                    Map<String, Object> body = new HashMap<>();
                    body.put("token", token);
                    body.put("expires_in", jwtUtil.getExpiration(token));
                    // Return an HTTP 200 OK response with the token.
                    return ResponseEntity.ok(body);
                })
                // If the stream was empty (user not found or password didn't match), switch to this Mono.
                .switchIfEmpty(Mono.just(
                        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"))));
    }

    /**
     * Handles user logout by blacklisting the provided JWT.
     * The token is blacklisted for its remaining validity period, preventing reuse.
     *
     * @param authHeader The 'Authorization' header containing the "Bearer" token.
     * @return A {@link Mono} completing with a 204 No Content on success, 400 Bad Request if the
     *         token is missing/malformed, or 500 Internal Server Error if blacklisting fails.
     */
    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(
            @RequestHeader(name = "Authorization", required = false) String authHeader) {
        // Validate that the Authorization header is present and starts with "Bearer ".
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            return Mono.just(ResponseEntity.badRequest().build()); // Return 400 if header is invalid.
        }
        // Extract the token string by removing the "Bearer " prefix.
        String token = authHeader.substring(7).trim();
        // Ensure the extracted token is not empty.
        if (!StringUtils.hasText(token)) {
            return Mono.just(ResponseEntity.badRequest().build()); // Return 400 if token is empty.
        }

        // Calculate the token's remaining time-to-live (TTL).
        Duration ttl = jwtUtil.getRemainingValidity(token);
        // If the token is already expired, there's no need to blacklist it.
        if (ttl.isZero() || ttl.isNegative()) {
            return Mono.just(ResponseEntity.noContent().build()); // Return 204 No Content.
        }
        // Add the token to the blacklist with its remaining TTL.
        return blacklistService
                .blacklistToken(token, ttl)
                // After blacklisting succeeds, chain to a Mono that completes the HTTP response.
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                // If an error occurs during blacklisting (e.g., Redis is down), handle it.
                .onErrorResume(ex -> {
                    // Fail-closed security: if we can't blacklist, deny the logout to signal a problem.
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .build());
                });
    }

    /**
     * Refreshes an existing JWT, issuing a new one with a renewed expiration.
     * This requires the old token to be valid and not blacklisted.
     *
     * @param authHeader The 'Authorization' header containing the "Bearer" token to be refreshed.
     * @return A {@link Mono} containing a 200 OK with the new token on success, or 401 Unauthorized on failure.
     */
    @PostMapping("/refresh")
    public Mono<ResponseEntity<Map<String, Object>>> refresh(
            @RequestHeader(name = "Authorization", required = false) String authHeader) {
        // Validate the Authorization header format.
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()); // Return 401 if invalid.
        }
        // Extract the token string.
        String token = authHeader.substring(7).trim();
        if (!StringUtils.hasText(token)) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()); // Return 401 if token is empty.
        }
        // Wrap the validation logic, which might throw an exception, in a Mono.
        return Mono.fromCallable(() -> jwtUtil.validateWithRotation(token)).flatMap(valid -> {
            // If the token is not valid (expired, bad signature, or blacklisted), return 401.
            if (!valid)
                return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
            String newToken;
            try {
                // Generate a new token based on the claims of the old one.
                newToken = jwtUtil.refreshToken(token);
            } catch (Exception e) {
                // If refreshing fails for any reason, treat it as an authorization failure.
                return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
            }
            // Prepare the response body with the new token.
            Map<String, Object> body = new HashMap<>();
            body.put("token", newToken);
            body.put("expires_in", jwtUtil.getExpiration(newToken));
            // Return a 200 OK with the new token details.
            return Mono.just(ResponseEntity.ok(body));
        });
    }
}
