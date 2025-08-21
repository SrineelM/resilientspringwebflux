package com.resilient.config;

import io.r2dbc.h2.H2ConnectionFactory;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import reactor.core.publisher.Mono;

/**
 * Unified R2DBC configuration for both local (H2) and production (Postgres).
 *
 * <p>Features: - Local profile uses H2 in-memory DB with connection pooling for fast startup and
 * testing. - Production profile uses Postgres with connection pooling and externalized credentials.
 * - Local DB schema initialized via schema.sql; in production use Flyway or Liquibase instead. -
 * Profiles isolate beans to prevent accidental misconfiguration.
 */
@Configuration
public class DatabaseConfig {

    /** Dev and Local profiles: Connection factory with H2 in-memory + connection pool. */
    @Bean
    @Profile({"dev", "local"})
    public ConnectionFactory h2ConnectionFactory() {
        ConnectionPoolConfiguration config = ConnectionPoolConfiguration.builder(H2ConnectionFactory.inMemory("testdb"))
                .maxSize(10)
                .build();
        return new ConnectionPool(config);
    }

    /** Dev and Local profiles: schema.sql initializer for H2. */
    @Bean
    @Profile({"dev", "local"})
    public ConnectionFactoryInitializer h2Initializer(ConnectionFactory h2ConnectionFactory) {
        ConnectionFactoryInitializer initializer = new ConnectionFactoryInitializer();
        initializer.setConnectionFactory(h2ConnectionFactory);
        initializer.setDatabasePopulator(new ResourceDatabasePopulator(new ClassPathResource("schema.sql")));
        return initializer;
    }

    /** Test and Production profiles: Postgres connection factory with connection pooling. */
    @Bean
    @Profile({"test", "prod"})
    public ConnectionFactory postgresConnectionFactory(
            @Value("${spring.r2dbc.url}") String url,
            @Value("${spring.r2dbc.username}") String username,
            @Value("${spring.r2dbc.password}") String password) {

        ConnectionFactoryOptions options = ConnectionFactoryOptions.parse(url)
                .mutate()
                .option(ConnectionFactoryOptions.USER, username)
                .option(ConnectionFactoryOptions.PASSWORD, password)
                .build();

        ConnectionPoolConfiguration config = ConnectionPoolConfiguration.builder(ConnectionFactories.get(options))
                .maxSize(20)
                .build();
        return new ConnectionPool(config);
    }

    /** Dev and Local profiles: Reactive DB health indicator for H2. */
    @Bean(name = "db")
    @Profile({"dev", "local"})
    public ReactiveHealthIndicator reactiveDataSourceHealthIndicator(ConnectionFactory connectionFactory) {
        return () -> Mono.from(connectionFactory.create())
                .flatMap(conn -> Mono.from(conn.close()).thenReturn(Health.up().build()))
                .onErrorResume(ex -> Mono.just(Health.down(ex).build()));
    }

    // ⚠️ For production, schema migrations should be handled with Flyway or Liquibase
    // instead of ResourceDatabasePopulator.
}
