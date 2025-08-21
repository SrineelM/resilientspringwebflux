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

    @Bean
    @ConditionalOnClass(HttpServer.class)
    public WebServerFactoryCustomizer<NettyReactiveWebServerFactory> nettyGracefulShutdownCustomizer() {
        return factory -> factory.addServerCustomizers(httpServer -> httpServer
                .idleTimeout(Duration.ofSeconds(30))
                .maxKeepAliveRequests(100) // Example tuning
                .protocol(HttpProtocol.H2, HttpProtocol.HTTP11));
    }
}
