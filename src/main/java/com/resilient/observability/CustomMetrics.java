package com.resilient.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * CustomMetrics records application-specific metrics using Micrometer.
 * <p>
 * This class demonstrates how to define and use custom metrics (counters and timers)
 * in a Spring Boot application. Metrics are exposed via the /actuator/prometheus endpoint
 * for scraping by Prometheus or other monitoring systems.
 * <p>
 * <b>Key concepts for beginners:</b>
 * <ul>
 *   <li><b>Counter:</b> Tracks the number of times an event occurs (e.g., user created).</li>
 *   <li><b>Timer:</b> Measures the duration of an operation (e.g., user creation latency).</li>
 *   <li>Metrics are registered with a MeterRegistry and can be tagged for filtering/aggregation.</li>
 *   <li>Pre-registering metrics improves performance and ensures they are always visible.</li>
 * </ul>
 * <p>
 * See: <a href="https://micrometer.io/docs/concepts">Micrometer Concepts</a>
 */
@Component
public class CustomMetrics {

    // Metric name constants (used to identify metrics in monitoring systems)
    private static final String USER_CREATED_COUNTER = "user_created_events_total";
    private static final String USER_CREATE_TIMER = "user_create_duration_millis";

    // Counter for tracking the number of user creation events
    private final Counter userCreatedCounter;
    // Timer for measuring the duration of user creation operations
    private final Timer userCreateTimer;

    /**
     * Constructs the CustomMetrics bean and registers metrics with the MeterRegistry.
     *
     * @param registry The Micrometer MeterRegistry used to register and publish metrics.
     */
    public CustomMetrics(MeterRegistry registry) {
        // Pre-register a counter for user creation events, with a static tag for service name
        this.userCreatedCounter = Counter.builder(USER_CREATED_COUNTER)
                .description("Total number of user creation events")
                .tag("service", "user-service") // Example static tag for filtering
                .register(registry);

        // Pre-register a timer for user creation latency, with histogram for latency percentiles
        this.userCreateTimer = Timer.builder(USER_CREATE_TIMER)
                .description("Time taken to create a user in milliseconds")
                .tag("service", "user-service")
                .publishPercentileHistogram() // Enables latency distribution metrics
                .register(registry);
    }

    /**
     * Increments the user creation counter by 1.
     * <p>
     * Call this method each time a new user is successfully created.
     */
    public void countUserCreatedEvent() {
        userCreatedCounter.increment(); // Increments the counter metric
    }

    /**
     * Records the time taken to create a user in the timer metric.
     * <p>
     * This method should be called with the duration (in milliseconds) of the user creation operation.
     *
     * @param millis Duration in milliseconds
     */
    public void recordUserLatency(long millis) {
        userCreateTimer.record(millis, TimeUnit.MILLISECONDS); // Records the duration in the timer
    }
}
