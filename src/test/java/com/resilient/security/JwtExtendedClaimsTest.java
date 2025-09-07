package com.resilient.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class JwtExtendedClaimsTest {
    @Autowired
    JwtUtil jwtUtil;

    @Test
    void extendedClaimsValidate() {
        String token = jwtUtil.generateToken(
                "user1",
                Map.of(
                        "type", "access",
                        "client_id", "clientA",
                        "version", 2));
        assertTrue(jwtUtil.validateExtendedClaims(token, List.of("clientA", "clientB"), 1));
        assertFalse(jwtUtil.validateExtendedClaims(token, List.of("other"), 1));
        assertFalse(jwtUtil.validateExtendedClaims(token, List.of("clientA"), 3));
    }
}
