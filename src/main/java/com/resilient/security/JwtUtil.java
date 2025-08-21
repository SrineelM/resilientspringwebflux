package com.resilient.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JwtUtil: token generation & parsing with issuer/audience and TTL helpers. Uses HS256; ensure a
 * strong secret (>= 256-bit).
 */
@Component
@ConfigurationProperties(prefix = "security.jwt")
public class JwtUtil {

    private String secret;
    private String issuer;
    private List<String> audience;
    private long ttlSeconds = 3600;

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public void setAudience(List<String> audience) {
        this.audience = audience;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    private Key getKey() {
        byte[] bytes;
        try {
            bytes = Decoders.BASE64.decode(secret);
        } catch (IllegalArgumentException ex) {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 32) throw new IllegalStateException("JWT secret must be at least 256 bits");
        return Keys.hmacShaKeyFor(bytes);
    }

    /** Generate a JWT with subject, iss, aud(list), iat, exp, and custom claims. */
    public String generateToken(String subject, Map<String, Object> extraClaims) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttlSeconds);
        var builder = Jwts.builder()
                .subject(subject)
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(getKey());
        if (audience != null && !audience.isEmpty()) builder.claim("aud", audience);
        if (extraClaims != null) extraClaims.forEach(builder::claim);
        return builder.compact();
    }

    /** Generate a refresh token with longer expiration. */
    public String generateRefreshToken(String subject) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttlSeconds * 24);
        return Jwts.builder()
                .subject(subject)
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(getKey())
                .compact();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public Claims extractAllClaims(String token) {
        JwtParser parser =
                Jwts.parser().verifyWith((javax.crypto.SecretKey) getKey()).build();
        return parser.parseSignedClaims(token).getPayload();
    }

    public boolean validateToken(String token, String expectedUsername) {
        try {
            Claims claims = extractAllClaims(token);
            String subject = claims.getSubject();
            Date exp = claims.getExpiration();
            String iss = claims.getIssuer();
            if (!Objects.equals(expectedUsername, subject)) return false;
            if (exp == null || exp.before(new Date())) return false;
            return Objects.equals(issuer, iss);
        } catch (Exception e) {
            return false;
        }
    }

    /** Remaining validity until expiration; zero/negative if expired or invalid. */
    public Duration getRemainingValidity(String token) {
        try {
            Date exp = extractAllClaims(token).getExpiration();
            long seconds = (exp.getTime() - System.currentTimeMillis()) / 1000;
            return seconds > 0 ? Duration.ofSeconds(seconds) : Duration.ZERO;
        } catch (Exception e) {
            return Duration.ZERO;
        }
    }

    /** Convenience for clients/tests. */
    public Date getExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }
}
