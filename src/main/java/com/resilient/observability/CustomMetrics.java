package com.resilient.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * CustomMetrics records application-specific metrics using Micrometer.
 *
 * <p>Metrics are exposed via the /actuator/prometheus endpoint for scraping. Designed for
 * performance: counters and timers are pre-registered.
 */
@Component
public class CustomMetrics {

    // Metric name constants
    private static final String USER_CREATED_COUNTER = "user_created_events_total";
    private static final String USER_CREATE_TIMER = "user_create_duration_millis";

    private final Counter userCreatedCounter;
    private final Timer userCreateTimer;

    public CustomMetrics(MeterRegistry registry) {
        // Pre-register counters/timers with optional tags
        this.userCreatedCounter = Counter.builder(USER_CREATED_COUNTER)
                .description("Total number of user creation events")
                .tag("service", "user-service") // Example static tag
                .register(registry);

        this.userCreateTimer = Timer.builder(USER_CREATE_TIMER)
                .description("Time taken to create a user in milliseconds")
                .tag("service", "user-service")
                .publishPercentileHistogram() // Useful for latency distribution
                .register(registry);
    }

    /** Increment the user creation counter. */
    public void countUserCreatedEvent() {
        userCreatedCounter.increment();
    }

    /**
     * Record the time taken to create a user.
     *
     * @param millis Duration in milliseconds
     */
    public void recordUserLatency(long millis) {
        userCreateTimer.record(millis, TimeUnit.MILLISECONDS);
    }
}
