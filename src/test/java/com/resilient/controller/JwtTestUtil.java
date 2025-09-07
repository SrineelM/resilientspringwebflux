package com.resilient.controller;

import com.resilient.security.JwtUtil;
import io.jsonwebtoken.Claims;
import org.mockito.Mockito;

public class JwtTestUtil {
    public static void setupJwtMock(JwtUtil jwtUtil, String token, String username) {
        Claims claims = Mockito.mock(Claims.class);
        Mockito.when(claims.get("roles")).thenReturn(java.util.List.of("ROLE_USER"));
        Mockito.when(jwtUtil.extractAllClaims(Mockito.eq(token))).thenReturn(claims);
        Mockito.when(jwtUtil.extractUsername(Mockito.eq(token))).thenReturn(username);
        Mockito.when(jwtUtil.validateToken(Mockito.eq(token), Mockito.any())).thenReturn(true);
    }

    public static void setupTokenGeneration(JwtUtil jwtUtil, String username, String token) {
        Mockito.when(jwtUtil.generateToken(Mockito.eq(username), Mockito.anyMap()))
                .thenReturn(token);
    }
}
