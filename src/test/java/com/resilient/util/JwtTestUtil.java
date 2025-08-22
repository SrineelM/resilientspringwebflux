package com.resilient.util;

import com.resilient.security.JwtUtil;
import io.jsonwebtoken.Claims;
import org.mockito.Mockito;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class JwtTestUtil {
    
    /**
     * Sets up common JWT mocking behavior for tests
     * 
     * @param jwtUtil The mocked JwtUtil instance
     * @param token The token string to use in the test
     * @param username The username to associate with the token
     */
    public static void setupJwtMock(JwtUtil jwtUtil, String token, String username) {
        when(jwtUtil.validateToken(anyString(), any())).thenReturn(true);
        when(jwtUtil.extractUsername(anyString())).thenReturn(username);
        
        Claims claims = Mockito.mock(Claims.class);
        when(claims.getSubject()).thenReturn(username);
        when(claims.getIssuer()).thenReturn("https://auth.dev.resilient.com");
        when(claims.get("aud")).thenReturn(List.of("resilient-app", "admin-portal"));
        when(claims.getAudience()).thenReturn(Set.of("resilient-app", "admin-portal"));
        when(claims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 1000000));
        when(claims.get("roles")).thenReturn(List.of("ROLE_USER"));
        when(jwtUtil.extractAllClaims(anyString())).thenReturn(claims);
    }
    
    /**
     * Sets up JWT mocking for token generation
     *
     * @param jwtUtil The mocked JwtUtil instance
     * @param username The username to use for token generation
     * @param token The token to return from generation
     */
    public static void setupTokenGeneration(JwtUtil jwtUtil, String username, String token) {
        when(jwtUtil.generateToken(anyString(), any(Map.class))).thenReturn(token);
    }
}
