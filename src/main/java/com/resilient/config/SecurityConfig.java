package com.resilient.config;

import com.resilient.security.ReactiveRateLimiter;
import com.resilient.security.EnhancedRateLimitingWebFilter;
import com.resilient.security.JwtUtil;
import com.resilient.security.secrets.SecretProvider;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
// Removed specific path matcher imports due to simplified CSRF logic
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Centralized security configuration for the WebFlux application.
 *
 * This class sets up a comprehensive security policy for reactive applications.
 */
@EnableWebFluxSecurity
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private final Environment environment;
    private final ReactiveAuthenticationManager jwtAuthenticationManager;
    private final ServerAuthenticationConverter jwtAuthenticationConverter;
    private final Optional<ReactiveRateLimiter> genericRateLimiter; // in-memory or redis

    // Public constants for endpoint paths
    public static final String LOGIN_PATH = "/api/auth/login";
    public static final String USERS_PATH = "/api/users/**";
    public static final String WEBHOOK_PATH = "/api/webhook/event";
    public static final String ACTUATOR_PATH = "/actuator/**";
    public static final String ACTUATOR_HEALTH_PATH = "/actuator/health";
    public static final String ACTUATOR_PROMETHEUS_PATH = "/actuator/prometheus";

    public SecurityConfig(
            Environment environment,
            ReactiveAuthenticationManager jwtAuthenticationManager,
            ServerAuthenticationConverter jwtAuthenticationConverter,
            Optional<ReactiveRateLimiter> genericRateLimiter) {
        this.environment = environment;
        this.jwtAuthenticationManager = jwtAuthenticationManager;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
        this.genericRateLimiter = genericRateLimiter;
    }

    // Ensure JwtUtil picks up active SecretProvider bean if present (post-construction wiring)
    @Bean
    public JwtUtil jwtUtil(JwtUtil util, Optional<SecretProvider> secretProvider) {
        secretProvider.ifPresent(util::setSecretProvider);
        return util;
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
            @Value("${security.csrf.enabled:false}") boolean csrfEnabled) {
        boolean isDev = Arrays.asList(environment.getActiveProfiles()).contains("dev");

    // Add enhanced rate limiting filter if a limiter implementation exists
    genericRateLimiter.ifPresent(limiter ->
        http.addFilterBefore(new EnhancedRateLimitingWebFilter(limiter), SecurityWebFiltersOrder.AUTHENTICATION));

        // Configure JWT authentication filter
        AuthenticationWebFilter jwtWebFilter = new AuthenticationWebFilter(jwtAuthenticationManager);
        jwtWebFilter.setServerAuthenticationConverter(jwtAuthenticationConverter);
        jwtWebFilter.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());

    // Selective CSRF: enabled only if property set; ignores stateless/Bearer endpoints.
    if (csrfEnabled) {
    // Define simple require matcher: protect only non-Bearer, non-auth/webhook/actuator paths
    ServerWebExchangeMatcher requireMatcher = exchange -> {
        String path = exchange.getRequest().getPath().value();
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        boolean bearer = authHeader != null && authHeader.startsWith("Bearer ");
        boolean ignoredPath = path.startsWith("/api/auth/") || path.startsWith("/actuator/") || path.startsWith("/api/webhook/");
        if (bearer || ignoredPath) {
            return ServerWebExchangeMatcher.MatchResult.notMatch();
        }
        return ServerWebExchangeMatcher.MatchResult.match();
    };
    http.csrf(csrf -> csrf
            .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
            .requireCsrfProtectionMatcher(requireMatcher));
    } else {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable);
    }
    return http
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .requestCache(ServerHttpSecurity.RequestCacheSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
    .cors(cors -> {}) // use CorsConfigurationSource bean
        .headers(headers -> headers
            .contentSecurityPolicy(csp -> csp.policyDirectives(getCspPolicy()))
            .frameOptions(frame -> frame.mode(org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter.Mode.DENY))
                        .xssProtection(x -> x.disable())
                        .contentTypeOptions(c -> c.disable())
                        .referrerPolicy(r -> r.policy(org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicy(p -> p.policy("camera=(), microphone=(), geolocation=()")))
                .addFilterAt(jwtWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .authorizeExchange(authorize -> {
                    authorize.pathMatchers(LOGIN_PATH).permitAll();
                    authorize.pathMatchers("/api/auth/refresh").authenticated();
                    authorize.pathMatchers(ACTUATOR_HEALTH_PATH).permitAll();
                    authorize.pathMatchers(ACTUATOR_PROMETHEUS_PATH).hasRole("MONITOR");
                    authorize.pathMatchers(ACTUATOR_PATH).hasRole("ADMIN");
                    authorize.pathMatchers(USERS_PATH).authenticated();

                    if (isDev) {
                        authorize.pathMatchers(WEBHOOK_PATH).permitAll();
                    }

                    authorize.anyExchange().denyAll();
                })
                .build();
    }

    private String getCspPolicy() {
        return "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; "
                + "img-src 'self' data:; font-src 'self'; style-src 'self'; script-src 'self'; "
                + "connect-src 'self'";
    }

    // Allowed origins handled in bean below

    /**
     * FIXED: Using reactive CorsConfigurationSource instead of servlet-based one
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.security.cors.allowed-origins:}") List<String> allowedOrigins, Environment env) {

        boolean isDev = Arrays.asList(env.getActiveProfiles()).contains("dev");

        CorsConfiguration cfg = new CorsConfiguration();

        // Add configured allowed origins
        if (allowedOrigins != null) {
            allowedOrigins.stream()
                    .map(String::trim)
                    .filter(origin -> !origin.isBlank())
                    .forEach(cfg::addAllowedOrigin);
        }

        // In development, allow common localhost origins if no specific origins are configured
        if (isDev && (allowedOrigins == null || allowedOrigins.isEmpty())) {
            cfg.addAllowedOrigin("http://localhost:3000");
            cfg.addAllowedOrigin("http://127.0.0.1:3000");
        }

        // Configure CORS settings
        cfg.setAllowCredentials(false);
        cfg.addAllowedHeader("*");
        cfg.addAllowedMethod("*");
        cfg.setMaxAge(3600L);

        // FIXED: Use reactive UrlBasedCorsConfigurationSource
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
