package com.resilient.security;

import com.resilient.security.secrets.SecretProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
            this.keys = secretProvider.previousJwtSecrets().isEmpty() ? List.of(secret) : buildKeyList(secretProvider);
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
    /**
     * Initializes the `keys` list if it hasn't been set by a {@link SecretProvider} or configuration.
     * Ensures that even with a single `secret` property, there's a list to work with.
     */
    void init() {
        // Ensure key list initialized even if setSecretProvider came before property binding
        if (this.keys == null && this.secret != null) {
            this.keys = List.of(this.secret);
        }
    }
    /**
     * Sets the issuer claim for JWTs.
     * @param issuer The issuer string.
     */
    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    /**
     * Sets the audience claim for JWTs.
     * @param audience A list of audience strings.
     */
    public void setAudience(List<String> audience) {
        this.audience = audience;
    }

    /**
     * Sets the time-to-live (TTL) for generated JWTs.
     * @param ttlSeconds The TTL in seconds.
     */
    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * Sets the list of rotating keys. This property is typically populated via
     * `@ConfigurationProperties` if keys are defined directly in application properties.
     * If a {@link SecretProvider} is used, this property might be overridden.
     *
     * @param keys A list of secret key strings.
     */
    public void setKeys(List<String> keys) { // populated via configuration if provided
        this.keys = keys;
    }

    /**
     * Sets the maximum session duration for refresh token logic.
     * @param maxSessionSeconds The maximum session duration in seconds.
     */
    public void setMaxSessionSeconds(long maxSessionSeconds) {
        this.maxSessionSeconds = maxSessionSeconds;
    }

    /**
     * Retrieves the current active signing key.
     * It prioritizes the `keys` list (first element) if available, otherwise falls back to the `secret` field.
     *
     * @return The {@link Key} object for signing.
     */
    private Key getKey() {
        if (keys != null && !keys.isEmpty()) {
            return toKey(keys.get(0));
        }
        return toKey(secret);
    }

    /**
     * Converts a secret string into a {@link Key} object suitable for JWT signing.
     * It attempts to decode the string as Base64 first; if that fails, it treats it as a plain string.
     * Enforces a minimum key length of 256 bits (32 bytes) for HS256.
     *
     * @param value The secret string (Base64 encoded or plain).
     * @return The generated {@link Key}.
     * @throws IllegalStateException if the key is too short.
     */
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

    /**
     * Generates a new JWT with the specified subject, issuer, audience, issued-at time,
     * expiration time, and any additional custom claims.
     *
     * @param subject The subject (user identifier) of the token.
     * @param extraClaims A map of additional claims to include in the token payload.
     * @return The compact, signed JWT string.
     */
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

    /**
     * Generates a refresh token with a longer expiration time (24 times the standard TTL).
     *
     * @param subject The subject (user identifier) of the token.
     * @return The compact, signed refresh token string.
     */
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

    /**
     * Extracts the subject (username) from a given JWT.
     *
     * @param token The JWT string.
     * @return The username (subject) from the token.
     * @throws io.jsonwebtoken.JwtException if the token is invalid or malformed.
     */
    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Extracts all claims (payload) from a given JWT.
     *
     * @param token The JWT string.
     * @return The {@link Claims} object containing all claims.
     * @throws io.jsonwebtoken.JwtException if the token is invalid or malformed.
     */
    public Claims extractAllClaims(String token) {
        JwtParser parser =
                Jwts.parser().verifyWith((javax.crypto.SecretKey) getKey()).build();
        return parser.parseSignedClaims(token).getPayload();
    }

    /**
     * Validates a JWT against a specific expected username, its signature, expiration, and issuer.
     */
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
     * Validates the token's signature, expiration, issuer, and audience.
     * This method does not require an `expectedUsername` and is useful for general token validity checks.
     *
     * @param token The JWT string.
     * @return {@code true} if the token is valid, {@code false} otherwise.
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

    /**
     * Attempts to validate a JWT, first with the current active key, and if that fails,
     * then with any configured previous keys. This supports seamless key rotation.
     *
     * @param token The JWT string.
     * @return {@code true} if the token is valid with any of the configured keys,
     *         {@code false} otherwise.
     */
    public boolean validateWithRotation(String token) {
        if (validateToken(token)) return true; // fast path with current key
        if (keys == null || keys.size() <= 1) return false;
        for (int i = 1; i < keys.size(); i++) {
            try {
                JwtParser parser = Jwts.parser()
                        .verifyWith((javax.crypto.SecretKey) toKey(keys.get(i)))
                        .build();
                parser.parseSignedClaims(token); // will throw if invalid
                return true;
            } catch (Exception ignore) {
                // try next
            }
        }
        return false;
    }

    /**
     * Refreshes an existing JWT by issuing a new one with an updated expiration time.
     * This implements a "sliding window" session management: the session is extended,
     * but an absolute maximum session duration (from the original issued-at time) is enforced.
     *
     * @param token The existing JWT to refresh.
     * @return A new, refreshed JWT string.
     * @throws IllegalStateException if the token is expired or the maximum session duration is exceeded.
     */
    public String refreshToken(String token) {
        Claims claims = extractAllClaims(token);
        Date exp = claims.getExpiration();
        if (exp == null || exp.before(new Date())) {
            throw new IllegalStateException("Token expired");
        }
        Instant now = Instant.now();
        Date originalIat = claims.get("original_iat", Date.class);
        // If 'original_iat' claim is not present, use the token's 'issuedAt' as the original.
        if (originalIat == null) {
            originalIat = claims.getIssuedAt();
        }
        if (maxSessionSeconds > 0 && (now.toEpochMilli() - originalIat.getTime()) / 1000 > maxSessionSeconds) {
            // Enforce maximum session duration.
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
        // Add audience claim if configured.
        if (audience != null && !audience.isEmpty()) builder.claim("aud", audience);
        // Copy all other claims from the original token, except standard ones that are being re-generated.
        claims.forEach((k, v) -> {
            if (!List.of("sub", "iss", "aud", "iat", "exp").contains(k)) {
                builder.claim(k, v);
            }
        });
        // Ensure the 'original_iat' claim is present in the new token.
        builder.claim("original_iat", originalIat);
        return builder.compact();
    }

    /**
     * Provides an additional strict validation hook for extended claims within a JWT.
     * This allows for custom business logic validation beyond standard JWT checks.
     *
     * @param token The JWT string.
     * @param allowedClientIds An optional list of client IDs that are allowed. If null or empty,
     *                         this check is skipped.
     * @param minVersion The minimum allowed version for the 'version' claim. If 0, this check is skipped.
     * @return {@code true} if all extended claims pass validation or are not configured for validation,
     *         {@code false} otherwise.
     */
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

    /**
     * Calculates the remaining validity duration of a JWT until its expiration.
     *
     * @param token The JWT string.
     * @return A {@link Duration} representing the remaining time. Returns {@link Duration#ZERO}
     *         if the token is already expired or invalid.
     */
    public Duration getRemainingValidity(String token) {
        try {
            Date exp = extractAllClaims(token).getExpiration();
            long seconds = (exp.getTime() - System.currentTimeMillis()) / 1000;
            return seconds > 0 ? Duration.ofSeconds(seconds) : Duration.ZERO;
        } catch (Exception e) {
            return Duration.ZERO;
        }
    }

    /**
     * Retrieves the expiration {@link Date} from a JWT.
     *
     * @param token The JWT string.
     * @return The expiration {@link Date}.
     */
    public Date getExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }
}
