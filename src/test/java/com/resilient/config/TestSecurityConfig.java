package com.resilient.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@TestConfiguration
public class TestSecurityConfig {
    @Bean
    SecurityWebFilterChain testSecurityFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .logout(ServerHttpSecurity.LogoutSpec::disable)
            .requestCache(ServerHttpSecurity.RequestCacheSpec::disable)
            .cors(ServerHttpSecurity.CorsSpec::disable)
            .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
            .build();
    }
}
