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
import com.resilient.security.secrets.SecretProvider;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;

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
    /** Optional list of rotating signing keys (Base64 or plain). First is current, rest previous. */
    private List<String> keys; // optional rotating keys support
    /** Absolute max session duration (seconds) for refresh logic; 0 disables enforcement. */
    private long maxSessionSeconds = 0;

    private SecretProvider secretProvider; // optional

    public void setSecret(String secret) { // legacy property support
        this.secret = secret;
    }

    public void setSecretProvider(SecretProvider secretProvider) {
        this.secretProvider = secretProvider;
        if (secretProvider != null) {
            this.secret = secretProvider.currentJwtSecret();
            this.keys = secretProvider.previousJwtSecrets().isEmpty()
                    ? List.of(secret)
                    : buildKeyList(secretProvider);
        }
    }

    private List<String> buildKeyList(SecretProvider provider) {
        List<String> prev = provider.previousJwtSecrets();
        if (prev == null || prev.isEmpty()) {
            return List.of(provider.currentJwtSecret());
        }
        ArrayList<String> combined = new ArrayList<>();
        combined.add(provider.currentJwtSecret());
        combined.addAll(prev);
        return List.copyOf(combined);
    }

    /** Allows scheduled refresh to update secrets if external source rotated. */
    public synchronized void refreshSecrets() {
        if (secretProvider != null) {
            this.secret = secretProvider.currentJwtSecret();
            this.keys = buildKeyList(secretProvider);
        }
    }

    @PostConstruct
    void init() {
        // Ensure key list initialized even if setSecretProvider came before property binding
        if (this.keys == null && this.secret != null) {
            this.keys = List.of(this.secret);
        }
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

    public void setKeys(List<String> keys) { // populated via configuration if provided
        this.keys = keys;
    }

    public void setMaxSessionSeconds(long maxSessionSeconds) {
        this.maxSessionSeconds = maxSessionSeconds;
    }

    private Key getKey() {
        if (keys != null && !keys.isEmpty()) {
            return toKey(keys.get(0));
        }
        return toKey(secret);
    }

    private Key toKey(String value) {
        byte[] bytes;
        try {
            bytes = Decoders.BASE64.decode(value);
        } catch (IllegalArgumentException ex) {
            bytes = value.getBytes(StandardCharsets.UTF_8);
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

    /**
     * Validates the token structure, signature, expiration, issuer, and audience
     * without requiring a specific username
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            Date exp = claims.getExpiration();
            String iss = claims.getIssuer();
            
            // Check expiration
            if (exp == null || exp.before(new Date())) return false;
            
            // Check issuer
            if (!Objects.equals(issuer, iss)) return false;
            
            // Check audience (if configured)
            if (audience != null && !audience.isEmpty()) {
                Object aud = claims.get("aud");
                if (aud instanceof List<?>) {
                    List<?> audList = (List<?>) aud;
                    return audList.stream()
                            .filter(Objects::nonNull)
                            .map(Object::toString)
                            .anyMatch(audience::contains);
                } else if (aud instanceof String) {
                    return audience.contains(aud.toString());
                }
                return false;
            }
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Attempt validation with previous keys (rotation support). */
    public boolean validateWithRotation(String token) {
        if (validateToken(token)) return true; // fast path with current key
        if (keys == null || keys.size() <= 1) return false;
        for (int i = 1; i < keys.size(); i++) {
            try {
                JwtParser parser = Jwts.parser().verifyWith((javax.crypto.SecretKey) toKey(keys.get(i))).build();
                parser.parseSignedClaims(token); // will throw if invalid
                return true;
            } catch (Exception ignore) {
                // try next
            }
        }
        return false;
    }

    /** Refresh a token (sliding window) preserving original_iat within maxSessionSeconds boundary. */
    public String refreshToken(String token) {
        Claims claims = extractAllClaims(token);
        Date exp = claims.getExpiration();
        if (exp == null || exp.before(new Date())) {
            throw new IllegalStateException("Token expired");
        }
        Instant now = Instant.now();
        Date originalIat = claims.get("original_iat", Date.class);
        if (originalIat == null) {
            originalIat = claims.getIssuedAt();
        }
        if (maxSessionSeconds > 0 && (now.toEpochMilli() - originalIat.getTime()) / 1000 > maxSessionSeconds) {
            throw new IllegalStateException("Session max duration exceeded");
        }
        // Build new token with new exp & iat but preserved original_iat
        Instant newExp = now.plusSeconds(ttlSeconds);
        var builder = Jwts.builder()
                .subject(claims.getSubject())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(newExp))
                .signWith(getKey());
        if (audience != null && !audience.isEmpty()) builder.claim("aud", audience);
        claims.forEach((k, v) -> {
            if (!List.of("sub", "iss", "aud", "iat", "exp").contains(k)) {
                builder.claim(k, v);
            }
        });
        builder.claim("original_iat", originalIat);
        return builder.compact();
    }

    /** Additional strict validation hook for extended claims. */
    public boolean validateExtendedClaims(String token, List<String> allowedClientIds, int minVersion) {
        try {
            Claims claims = extractAllClaims(token);
            // token type
            String type = claims.get("type", String.class);
            if (type != null && !type.equals("access")) return false;
            // client id
            String clientId = claims.get("client_id", String.class);
            if (clientId != null && allowedClientIds != null && !allowedClientIds.contains(clientId)) return false;
            // version
            Integer version = claims.get("version", Integer.class);
            if (version != null && version < minVersion) return false;
            return true;
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
