package com.resilient.security;

import static org.junit.jupiter.api.Assertions.*;

import io.jsonwebtoken.JwtException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtUtilTest {
    // Use constants for test data to improve readability and maintainability
    private static final String TEST_SECRET = "mysecretkeymysecretkeymysecretkeymysecretkey"; // 32 bytes
    private static final String TEST_ISSUER = "issuer";
    private static final List<String> TEST_AUDIENCE = Collections.singletonList("aud");

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        jwtUtil.setSecret(TEST_SECRET);
        jwtUtil.setIssuer(TEST_ISSUER);
        jwtUtil.setAudience(TEST_AUDIENCE);
        jwtUtil.setTtlSeconds(3600);
    }

    @Test
    void generateAndValidateToken_happyPath() {
        // when
        String token = jwtUtil.generateToken("user", Map.of());

        // then
        assertNotNull(token, "Generated token should not be null");

        boolean valid = jwtUtil.validateToken(token, "user");
        assertTrue(valid, "Token should be valid with the correct secret and issuer");

        String username = jwtUtil.extractUsername(token);
        assertEquals("user", username, "Extracted username should match the original");
    }

    @Test
    void validateToken_failsWhenExpired() {
        // given
        jwtUtil.setTtlSeconds(-1); // Set TTL to ensure it's already expired
        String expiredToken = jwtUtil.generateToken("user", Map.of());

        // when
        boolean valid = jwtUtil.validateToken(expiredToken, "user");

        // then
        assertFalse(valid, "Token should be invalid because it has expired");
    }

    @Test
    void validateToken_failsForInvalidSignature() {
        // given
        String token = jwtUtil.generateToken("user", Map.of());

        // when
        JwtUtil otherJwtUtil = new JwtUtil();
        // Configure the other util completely, just with a different secret
        otherJwtUtil.setSecret("anothersecretkeyanothersecretkeyanothersecretkey");
        otherJwtUtil.setIssuer(TEST_ISSUER);
        otherJwtUtil.setAudience(TEST_AUDIENCE);
        boolean valid = otherJwtUtil.validateToken(token, "user");

        // then
        assertFalse(valid, "Token validation should fail with a different secret key");
    }

    @Test
    void validateToken_failsForWrongIssuer() {
        // given
        // Create a token with a different issuer
        JwtUtil wrongIssuerUtil = new JwtUtil();
        wrongIssuerUtil.setSecret(TEST_SECRET);
        wrongIssuerUtil.setIssuer("wrong-issuer");
        wrongIssuerUtil.setAudience(TEST_AUDIENCE);
        String tokenWithWrongIssuer = wrongIssuerUtil.generateToken("user", Map.of());

        // when
        // Validate it with our main util, which expects TEST_ISSUER
        boolean valid = jwtUtil.validateToken(tokenWithWrongIssuer, "user");

        // then
        assertFalse(valid, "Token validation should fail because the token was signed by the wrong issuer");
    }

    @Test
    void getUsername_throwsExceptionForMalformedToken() {
        String malformedToken = "this.is.not.a.valid.jwt";
        assertThrows(
                JwtException.class,
                () -> jwtUtil.extractUsername(malformedToken),
                "Should throw JwtException for a malformed token");
    }
}
