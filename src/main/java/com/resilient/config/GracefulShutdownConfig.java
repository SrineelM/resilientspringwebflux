package com.resilient.config;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.server.HttpServer;

/**
 * Configures graceful shutdown for Netty-based Spring WebFlux applications. For Spring Boot 3.2+,
 * prefer: server.shutdown=graceful spring.lifecycle.timeout-per-shutdown-phase=30s
 */
@Configuration
public class GracefulShutdownConfig {

    /**
     * Customizes the Netty reactive web server to support graceful shutdown and connection tuning.
     *
     * <p>This bean is only created if {@link HttpServer} (from Reactor Netty) is on the classpath,
     * ensuring this configuration only applies when Netty is the chosen web server.
     *
     * <p>The customizations include:
     * <ul>
     *   <li><b>idleTimeout:</b> Sets a 30-second timeout for idle connections. This helps in
     *       releasing resources from inactive clients.</li>
     *   <li><b>maxKeepAliveRequests:</b> Limits a single keep-alive connection to 100 requests
     *       before it's closed. This can help in load balancing scenarios by encouraging
     *       clients to re-establish connections.</li>
     *   <li><b>protocol:</b> Explicitly enables both H2 (HTTP/2) and HTTP/1.1 protocols.</li>
     * </ul>
     *
     * Note: While this customizer is useful, for modern Spring Boot versions (3.2+),
     * it's recommended to use the built-in properties like {@code server.shutdown=graceful}.
     * @return A {@link WebServerFactoryCustomizer} for the {@link NettyReactiveWebServerFactory}.
     */
    @Bean
    @ConditionalOnClass(HttpServer.class)
    public WebServerFactoryCustomizer<NettyReactiveWebServerFactory> nettyGracefulShutdownCustomizer() {
        return factory -> factory.addServerCustomizers(httpServer -> httpServer
                .idleTimeout(Duration.ofSeconds(30))
                .maxKeepAliveRequests(100) // Example tuning
                .protocol(HttpProtocol.H2, HttpProtocol.HTTP11));
    }
}
