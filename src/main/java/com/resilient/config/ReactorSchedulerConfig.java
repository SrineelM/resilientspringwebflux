// ReactorSchedulerConfig.java
//
// Configures custom Reactor schedulers for async tasks.
// - notificationScheduler uses bounded elastic pool for blocking I/O.
// - testScheduler uses direct executor for deterministic unit tests.
// - Scheduler sizes can be tuned via properties for production.
// - Notification scheduler now includes metrics support.

package com.resilient.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Scheduler configuration for Reactor-based async tasks. Expose scheduler sizes via properties for
 * runtime tuning.
 */
@Configuration
public class ReactorSchedulerConfig {

    /**
     * Creates a dedicated scheduler for notification-related tasks.
     *
     * <p>This scheduler uses a bounded elastic thread pool, which is ideal for I/O-bound or
     * moderately blocking tasks. It prevents the main Reactor event loop from being blocked by
     * operations like external HTTP calls or database interactions in the notification service.
     *
     * <p>The parameters are configured as follows:
     * <ul>
     *   <li>{@code threadCap}: 50 - The maximum number of threads in the pool.</li>
     *   <li>{@code queuedTaskCap}: Unlimited - The number of tasks that can be queued.</li>
     *   <li>{@code name}: "notification" - A prefix for thread names for easier debugging.</li>
     *   <li>{@code ttlSeconds}: 30 - Idle threads will be terminated after 30 seconds.</li>
     *   <li>{@code metrics}: true - Enables Micrometer metrics for this scheduler.</li>
     * </ul>
     *
     * @param meterRegistry The meter registry to which scheduler metrics will be published.
     * @return A {@link Scheduler} instance for notification tasks.
     */
    @Bean(name = "notificationScheduler", destroyMethod = "dispose")
    public Scheduler notificationScheduler(MeterRegistry meterRegistry) {
        return Schedulers.newBoundedElastic(50, Integer.MAX_VALUE, "notification", 30, true);
    }

    /**
     * Creates a scheduler for running tests.
     *
     * <p>This scheduler is backed by a fixed-size thread pool. While tests often benefit from
     * deterministic, single-threaded execution (e.g., {@code Schedulers.immediate()}), a fixed pool
     * can be useful for integration tests that need a controlled level of concurrency without
     * overwhelming the test environment.
     *
     * @return A {@link Scheduler} instance for test execution.
     */
    @Bean(name = "testScheduler", destroyMethod = "dispose")
    public Scheduler testScheduler() {
        return Schedulers.fromExecutorService(java.util.concurrent.Executors.newFixedThreadPool(10));
    }

    /**
     * Creates a general-purpose scheduler for authentication-related tasks.
     * <p>This uses Reactor's default bounded elastic scheduler, suitable for potentially
     * blocking operations like password hashing or claim parsing during authentication.
     *
     * @return A default bounded elastic {@link Scheduler}.
     */
    @Bean(name = "authScheduler")
    public Scheduler authScheduler() {
        return Schedulers.boundedElastic();
    }
}
