package com.resilient.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;

@SpringBootTest
@ActiveProfiles("test")
class JwtUtilSmokeTest {
    @Autowired JwtUtil jwtUtil;

    @Test
    void generateAndValidate() {
        String token = jwtUtil.generateToken("alice", Map.of("type","access","client_id","c1","version",1));
        assertTrue(jwtUtil.validateToken(token));
        assertEquals("alice", jwtUtil.extractUsername(token));
    }
}
