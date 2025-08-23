package com.resilient.controller;

import com.resilient.security.JwtUtil;
import com.resilient.security.TokenBlacklistService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/** Production-grade JWT auth controller with login + logout (blacklist). */
@RestController
@RequestMapping("/api/auth")
public class JwtAuthController {


    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final ReactiveUserDetailsService userDetailsService;
    private final TokenBlacklistService blacklistService; // abstract base with TTL

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

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    /** Login: validates credentials and issues a JWT with roles. */
    @PostMapping("/login")
    public Mono<ResponseEntity<Map<String, Object>>> login(@Valid @RequestBody LoginRequest req) {
    return userDetailsService.findByUsername(req.username())
        .filter(ud -> passwordEncoder.matches(req.password(), ud.getPassword()))
        .map(ud -> {
            List<String> roles = ud.getAuthorities().stream().map(a -> a.getAuthority()).toList();
            String token = jwtUtil.generateToken(req.username(), Map.of("roles", roles));
            Map<String, Object> body = new HashMap<>();
            body.put("token", token);
            body.put("expires_in", jwtUtil.getExpiration(token));
            return ResponseEntity.ok(body);
        })
        .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("error", "Invalid credentials"))));
    }

    /** Logout: extracts bearer token and blacklists it until its natural expiry. */
    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(
            @RequestHeader(name = "Authorization", required = false) String authHeader) {
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        String token = authHeader.substring(7).trim();
        if (!StringUtils.hasText(token)) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        // Compute the remaining validity and blacklist for that TTL
        Duration ttl = jwtUtil.getRemainingValidity(token);
        if (ttl.isZero() || ttl.isNegative()) {
            // Already expired; nothing to blacklist
            return Mono.just(ResponseEntity.noContent().build());
        }
        return blacklistService
                .blacklistToken(token, ttl)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .onErrorResume(ex -> {
                    // Fail-closed: if blacklisting fails, send 500
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .build());
                });
    }

    /** Refresh token: issues a new token if within session window and not blacklisted. */
    @PostMapping("/refresh")
    public Mono<ResponseEntity<Map<String, Object>>> refresh(
            @RequestHeader(name = "Authorization", required = false) String authHeader) {
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        String token = authHeader.substring(7).trim();
        if (!StringUtils.hasText(token)) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return Mono.fromCallable(() -> jwtUtil.validateWithRotation(token))
                .flatMap(valid -> {
                    if (!valid) return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
                    String newToken;
                    try {
                        newToken = jwtUtil.refreshToken(token);
                    } catch (Exception e) {
                        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
                    }
                    Map<String, Object> body = new HashMap<>();
                    body.put("token", newToken);
                    body.put("expires_in", jwtUtil.getExpiration(newToken));
                    return Mono.just(ResponseEntity.ok(body));
                });
    }
}
