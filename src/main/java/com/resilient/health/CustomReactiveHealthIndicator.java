package com.resilient.health;

import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionMetadata;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * A custom, non-blocking health indicator for the application's R2DBC database connection.
 *
 * <p>This component integrates with Spring Boot Actuator's health endpoint (`/actuator/health`). It
 * performs a quick check to verify that the application can successfully connect to the database.
 *
 * <p>Key features:
 * <ul>
 *   <li><b>Reactive and Non-Blocking:</b> Uses Project Reactor and R2DBC to perform the check
 *       without consuming a thread while waiting for the database response.</li>
 *   <li><b>Detailed Health Information:</b> On success, it reports the database as UP and includes
 *       details like vendor, version, and connection latency.</li>
 *   <li><b>Resilience:</b> Includes a timeout to prevent a slow database from making the health
 *       endpoint unresponsive. It also gracefully handles connection errors.</li>
 *   <li><b>Thread-Safe:</b> The check is executed on a separate, bounded elastic scheduler to
 *       avoid impacting the main application event loop threads.</li>
 * </ul>
 *
 * TODO: Review SecurityConfig and related beans for potential circular dependencies. A common
 * pattern is to move bean definitions (like PasswordEncoder, JwtUtil) into a separate @Configuration class.
 */
@Component
public class CustomReactiveHealthIndicator implements ReactiveHealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(CustomReactiveHealthIndicator.class);

    /** The R2DBC connection factory used to create database connections for the health check. */
    private final ConnectionFactory connectionFactory;

    /** A timeout to ensure the health check does not hang indefinitely. */
    private final Duration timeout = Duration.ofSeconds(5);

    /**
     * Constructs the health indicator with the required R2DBC connection factory.
     *
     * @param connectionFactory The R2DBC {@link ConnectionFactory} to be checked.
     */
    public CustomReactiveHealthIndicator(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /**
     * Performs the database health check.
     *
     * @return A {@link Mono} that emits the {@link Health} status of the database.
     */
    @Override
    public Mono<Health> health() {
        long start = System.nanoTime();
        // Use `usingWhen` for safe, automatic resource management. It ensures the connection is
        // always closed, even if errors occur.
        return Mono.usingWhen(
                        connectionFactory.create(), // 1. Acquire a database connection.
                        conn -> {
                            // 2. If connection is successful, get metadata and build an UP response.
                            ConnectionMetadata meta = conn.getMetadata();
                            return Mono.just(Health.up()
                                    .withDetail("database", "reachable")
                                    .withDetail("vendor", meta.getDatabaseProductName())
                                    .withDetail("version", meta.getDatabaseVersion())
                                    .withDetail("latencyMs", (System.nanoTime() - start) / 1_000_000L)
                                    .build());
                        },
                        conn -> conn.close()) // 3. Release the connection when the inner Mono completes.
                .timeout(timeout) // Apply a timeout to the entire operation.
                .doOnSuccess(h -> log.debug("DB health check passed in {} ms", (System.nanoTime() - start) / 1_000_000L))
                .onErrorResume(ex -> {
                    // If any error occurs (e.g., timeout, connection failure), build a DOWN response.
                    log.error("DB health check failed", ex);
                    return Mono.just(Health.down()
                            .withDetail("error", ex.getMessage())
                            .withDetail("latencyMs", (System.nanoTime() - start) / 1_000_000L)
                            .build());
                })
                // Run the entire check on a separate thread pool to avoid blocking the event loop.
                .subscribeOn(Schedulers.boundedElastic());
    }
}
