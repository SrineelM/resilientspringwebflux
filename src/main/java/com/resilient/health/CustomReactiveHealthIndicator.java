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

@Component
public class CustomReactiveHealthIndicator implements ReactiveHealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(CustomReactiveHealthIndicator.class);

    private final ConnectionFactory connectionFactory;
    private final Duration timeout = Duration.ofSeconds(5);

    public CustomReactiveHealthIndicator(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Mono<Health> health() {
        long start = System.nanoTime();
        return Mono.usingWhen(
                        connectionFactory.create(),
                        conn -> {
                            ConnectionMetadata meta = conn.getMetadata();
                            String vendor = meta.getDatabaseProductName();
                            String version = meta.getDatabaseVersion();
                            return Mono.just(Health.up()
                                    .withDetail("database", "reachable")
                                    .withDetail("vendor", vendor)
                                    .withDetail("version", version)
                                    .withDetail("latencyMs", (System.nanoTime() - start) / 1_000_000)
                                    .build());
                        },
                        conn -> conn.close())
                .timeout(timeout)
                .doOnSuccess(h -> log.debug("DB health check passed in {} ms", (System.nanoTime() - start) / 1_000_000))
                .onErrorResume(ex -> {
                    log.error("DB health check failed", ex);
                    return Mono.just(Health.down()
                            .withDetail("error", ex.getMessage())
                            .withDetail("latencyMs", (System.nanoTime() - start) / 1_000_000)
                            .build());
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
