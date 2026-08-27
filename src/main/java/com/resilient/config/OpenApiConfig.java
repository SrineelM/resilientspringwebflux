package com.resilient.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Comprehensive configuration for OpenAPI 3 (Swagger) documentation.
 *
 * <p>This class defines the API metadata, security schemes (JWT Bearer, Webhook HMAC signatures),
 * server environments, tag taxonomies, and descriptions displayed in the Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        final String bearerAuthScheme = "bearerAuth";
        final String webhookSigScheme = "webhookSignature";
        final String webhookTimestampScheme = "webhookTimestamp";

        return new OpenAPI()
                .info(new Info()
                        .title("Resilient Spring WebFlux Reactive API")
                        .version("1.0.0")
                        .description(
                                """
                                ### Resilient Spring WebFlux Reference Architecture

                                This interactive API documentation exposes the reactive endpoints of the **Resilient Spring WebFlux POC**.

                                #### Key Architectural Pillars:
                                - **Reactive Non-Blocking Core:** Built on Spring WebFlux, Project Reactor, and Netty.
                                - **Resilience Patterns:** Circuit Breakers, Time Limiters, Retry, and Rate Limiting powered by Resilience4j.
                                - **Security:** Stateless JWT authentication, role-based access control (RBAC), and HMAC-signed webhooks with anti-replay protection.
                                - **Observability & Distributed Tracing:** Micrometer Tracing with W3C Trace Context and Baggage propagation.
                                - **Event-Driven & Streaming:** SSE (Server-Sent Events), NDJSON streaming, and Reactive Kafka integration.
                                """)
                        .contact(new Contact()
                                .name("Resilient Engineering Team")
                                .email("engineering@resilient-spring.io")
                                .url("https://github.com/resilient-spring-poc"))
                        .license(new License()
                                .name("Apache License 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("http://localhost:" + serverPort).description("Local Development Server"),
                        new Server()
                                .url("https://api.resilient.example.com")
                                .description("Production Gateway (Simulated)")))
                .tags(
                        List.of(
                                new Tag()
                                        .name("Authentication")
                                        .description(
                                                "JWT authentication, login credential validation, token refresh, and blacklisted session termination"),
                                new Tag()
                                        .name("User Management")
                                        .description(
                                                "CRUD operations, reactive SSE streaming, user status transitions, and search with circuit-breaker protection"),
                                new Tag()
                                        .name("Reactive Streaming")
                                        .description(
                                                "High-performance non-blocking streaming (SSE, NDJSON, and chunked binary streaming with ETag caching)"),
                                new Tag()
                                        .name("Secure Webhook")
                                        .description(
                                                "Secure webhook ingestion with timestamp anti-replay verification, HMAC SHA-256 signature checks, and rate limiting"),
                                new Tag()
                                        .name("Kafka Simulation")
                                        .description(
                                                "Simulated reactive Kafka message producer and SSE consumer endpoints for local development"),
                                new Tag()
                                        .name("Observability & Baggage")
                                        .description(
                                                "Distributed tracing context inspection and Micrometer Baggage propagation demonstration endpoints")))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        bearerAuthScheme,
                                        new SecurityScheme()
                                                .name("Authorization")
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description(
                                                        "Provide a valid JWT Bearer token obtained from `/api/auth/login`."))
                                .addSecuritySchemes(
                                        webhookSigScheme,
                                        new SecurityScheme()
                                                .name("x-webhook-signature")
                                                .type(SecurityScheme.Type.APIKEY)
                                                .in(SecurityScheme.In.HEADER)
                                                .description("HMAC SHA-256 hex signature of the webhook payload."))
                                .addSecuritySchemes(
                                        webhookTimestampScheme,
                                        new SecurityScheme()
                                                .name("x-webhook-timestamp")
                                                .type(SecurityScheme.Type.APIKEY)
                                                .in(SecurityScheme.In.HEADER)
                                                .description(
                                                        "Epoch millisecond timestamp of the webhook event (must be within 5 seconds).")))
                .addSecurityItem(new SecurityRequirement().addList(bearerAuthScheme))
                .externalDocs(new ExternalDocumentation()
                        .description("Project Documentation & Architecture Guide")
                        .url("https://github.com/resilient-spring-poc#readme"));
    }
}
