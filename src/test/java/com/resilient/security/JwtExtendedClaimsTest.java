package com.resilient.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class JwtExtendedClaimsTest {
    @Autowired
    JwtUtil jwtUtil;

    @Test
    void extendedClaimsValidate() {
        String token = jwtUtil.generateToken("user1", Map.of(
                "type","access",
                "client_id","clientA",
                "version", 2));
        assertTrue(jwtUtil.validateExtendedClaims(token, List.of("clientA","clientB"), 1));
        assertFalse(jwtUtil.validateExtendedClaims(token, List.of("other"), 1));
        assertFalse(jwtUtil.validateExtendedClaims(token, List.of("clientA"), 3));
    }
}
