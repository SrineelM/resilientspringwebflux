package com.resilient.config;

import com.resilient.security.JwtUtil;
import com.resilient.security.secrets.SecretProvider;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Defines standalone security-related beans to avoid circular dependencies with
 * {@link SecurityConfig}.
 */
@Configuration
public class SecurityBeansConfig {

    /**
     * Ensures the {@link JwtUtil} bean is aware of any {@link SecretProvider} for key rotation.
     * This post-construction wiring allows for dynamic secret management.
     *
     * @param util The base JwtUtil bean.
     * @param secretProvider An optional secret provider for key rotation.
     * @return The configured JwtUtil bean.
     */
    @Bean
    public JwtUtil jwtUtil(JwtUtil util, Optional<SecretProvider> secretProvider) {
        secretProvider.ifPresent(util::setSecretProvider);
        return util;
    }

    /**
     * Configures Cross-Origin Resource Sharing (CORS) for the application.
     *
     * <p>This bean defines the CORS policy, which controls how web pages from different
     * origins can interact with this application's APIs. It is automatically picked up
     * by Spring Security's {@code .cors()} configuration.
     *
     * <p>The configuration is driven by the {@code app.security.cors.allowed-origins} property.
     * If this property is not set and the 'dev' profile is active, it defaults to allowing
     * common local development origins (e.g., http://localhost:3000).
     *
     * @param allowedOrigins A list of allowed origins from application properties.
     * @param env The Spring environment, used to check for active profiles.
     * @return A {@link CorsConfigurationSource} for reactive applications.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.security.cors.allowed-origins:}") List<String> allowedOrigins, Environment env) {
        boolean isDev = Arrays.asList(env.getActiveProfiles()).contains("dev");
        CorsConfiguration cfg = new CorsConfiguration();

        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            allowedOrigins.stream().map(String::trim).filter(s -> !s.isBlank()).forEach(cfg::addAllowedOrigin);
        } else if (isDev) {
            cfg.addAllowedOrigin("http://localhost:3000");
            cfg.addAllowedOrigin("http://127.0.0.1:3000");
        }

        cfg.setAllowCredentials(false);
        cfg.addAllowedHeader("*");
        cfg.addAllowedMethod("*");
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    /**
     * Creates the application's primary {@link PasswordEncoder}.
     *
     * <p>This uses {@link PasswordEncoderFactories#createDelegatingPasswordEncoder()}, which is the
     * recommended modern approach. It supports multiple encoding algorithms and prefixes stored
     * passwords with an ID (e.g., "{bcrypt}") to allow for seamless password hashing upgrades
     * in the future without breaking existing user credentials.
     *
     * @return A delegating {@link PasswordEncoder} instance.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
