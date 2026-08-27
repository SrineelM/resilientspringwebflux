package com.resilient.config;

import com.resilient.security.ReactiveJwtAuthenticationManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

/**
 * Main Spring Security configuration for WebFlux applications.
 *
 * <p>This configuration class establishes the security filter chain for the reactive application,
 * integrating JWT-based authentication, CORS support, and endpoint authorization rules.
 *
 * <p><b>Key Features:</b>
 * <ul>
 *   <li><b>JWT Authentication:</b> Stateless authentication using JSON Web Tokens</li>
 *   <li><b>CORS Support:</b> Cross-Origin Resource Sharing configuration</li>
 *   <li><b>Endpoint Authorization:</b> Fine-grained access control for different API paths</li>
 *   <li><b>Stateless Sessions:</b> No server-side session storage (NoOpServerSecurityContextRepository)</li>
 *   <li><b>CSRF Disabled:</b> Not needed for stateless JWT authentication</li>
 * </ul>
 *
 * <p><b>Security Strategy:</b>
 * <ul>
 *   <li>Public endpoints: /api/auth/**, /actuator/health, /actuator/prometheus</li>
 *   <li>Authenticated endpoints: /api/users/**, /api/webhook/**, all others</li>
 *   <li>JWT validation on every request via ReactiveJwtAuthenticationManager</li>
 *   <li>Security headers for protection against common attacks</li>
 * </ul>
 *
 * <p><b>Architecture Notes:</b>
 * This class integrates with:
 * <ul>
 *   <li>{@link ReactiveJwtAuthenticationManager} - JWT token validation and authentication</li>
 *   <li>{@link SecurityBeansConfig} - CORS configuration and password encoder</li>
 *   <li>{@link com.resilient.security.JwtUtil} - JWT parsing and validation utilities</li>
 * </ul>
 *
 * @see ReactiveJwtAuthenticationManager
 * @see SecurityBeansConfig
 * @author Senior Architect Team
 * @since 1.0.0
 */
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity // Enables @PreAuthorize, @PostAuthorize annotations
public class WebFluxSecurityConfig {

    /**
     * Configures the main security filter chain for the WebFlux application.
     *
     * <p>This bean defines how Spring Security handles authentication and authorization
     * for incoming HTTP requests in a reactive environment.
     *
     * <p><b>Configuration Details:</b>
     * <ol>
     *   <li><b>CORS:</b> Configured via injected CorsConfigurationSource from SecurityBeansConfig</li>
     *   <li><b>CSRF:</b> Disabled because we use stateless JWT authentication (no cookies/sessions)</li>
     *   <li><b>HTTP Basic/Form Login:</b> Disabled in favor of JWT</li>
     *   <li><b>Logout:</b> Disabled (logout handled via JWT blacklisting in JwtAuthController)</li>
     *   <li><b>Session Management:</b> Stateless - no server-side session storage</li>
     *   <li><b>Authorization Rules:</b>
     *     <ul>
     *       <li>POST /api/auth/** - Permit all (login, refresh, logout endpoints)</li>
     *       <li>GET /actuator/health, /actuator/prometheus - Permit all (health checks)</li>
     *       <li>GET/POST/PUT/DELETE /api/users/** - Authenticated users only</li>
     *       <li>POST /api/webhook/** - Authenticated users only</li>
     *       <li>All other endpoints - Authenticated users only</li>
     *     </ul>
     *   </li>
     *   <li><b>Security Headers:</b> Configured for protection against XSS, clickjacking, etc.</li>
     * </ol>
     *
     * @param http The ServerHttpSecurity object to configure
     * @param authManager The JWT authentication manager for token validation
     * @param corsConfigurationSource CORS configuration from SecurityBeansConfig
     * @return The configured SecurityWebFilterChain
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ReactiveJwtAuthenticationManager authManager,
            CorsConfigurationSource corsConfigurationSource) {

        return http
                // Enable CORS with configuration from SecurityBeansConfig
                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                // Disable CSRF (not needed for stateless JWT authentication)
                .csrf(ServerHttpSecurity.CsrfSpec::disable)

                // Disable HTTP Basic authentication (using JWT instead)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)

                // Disable form-based login (using JWT instead)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)

                // Disable default logout (handled via JWT blacklisting)
                .logout(ServerHttpSecurity.LogoutSpec::disable)

                // Disable request cache (stateless)
                .requestCache(ServerHttpSecurity.RequestCacheSpec::disable)

                // Set authentication manager for JWT validation
                .authenticationManager(authManager)

                // Use stateless security context (no sessions)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())

                // Configure security headers for protection against common attacks
                .headers(headers -> headers
                        // Content Security Policy - restrict resource loading to same origin
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
                        // Prevent clickjacking attacks by denying framing
                        .frameOptions(frameOptions -> frameOptions.mode(
                                org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter.Mode
                                        .DENY))
                        // Control referrer information sent to external sites
                        .referrerPolicy(referrer -> referrer.policy(
                                org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter
                                        .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // Disable browser features (camera, microphone, geolocation)
                        .permissionsPolicy(
                                permissions -> permissions.policy("camera=(), microphone=(), geolocation=()")))

                // Define authorization rules
                .authorizeExchange(exchanges -> exchanges
                        // Public endpoints - no authentication required

                        // Authentication endpoints (login, refresh, logout)
                        .pathMatchers(HttpMethod.POST, "/api/auth/**")
                        .permitAll()

                        // Health check endpoints for monitoring
                        .pathMatchers(HttpMethod.GET, "/actuator/health", "/actuator/prometheus", "/actuator/info")
                        .permitAll()

                        // H2 console (only in dev profile, should be disabled in prod)
                        .pathMatchers("/h2-console/**")
                        .permitAll()

                        // Swagger/OpenAPI endpoints
                        .pathMatchers("/webjars/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()

                        // Protected endpoints - authentication required

                        // User management endpoints
                        .pathMatchers("/api/users/**")
                        .authenticated()

                        // Webhook endpoints
                        .pathMatchers("/api/webhook/**")
                        .authenticated()

                        // Kafka demo endpoints
                        .pathMatchers("/api/kafka/**")
                        .authenticated()

                        // Streaming endpoints
                        .pathMatchers("/api/stream/**")
                        .authenticated()

                        // Baggage demo endpoints
                        .pathMatchers("/api/baggage/**")
                        .authenticated()

                        // All other endpoints require authentication by default
                        .anyExchange()
                        .authenticated())

                // Add JWT authentication filter to the chain
                .addFilterAt(new AuthenticationWebFilter(authManager), SecurityWebFiltersOrder.AUTHENTICATION)

                // Build the security filter chain
                .build();
    }
}
