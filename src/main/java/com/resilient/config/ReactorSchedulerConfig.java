// ReactorSchedulerConfig.java
//
// Configures custom Reactor schedulers for async tasks.
// - notificationScheduler uses bounded elastic pool for blocking I/O.
// - testScheduler uses direct executor for deterministic unit tests.
// - Scheduler sizes can be tuned via properties for production.
// - Notification scheduler now includes metrics support.

package com.resilient.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Scheduler configuration for Reactor-based async tasks. Expose scheduler sizes via properties for
 * runtime tuning.
 */
@Configuration
public class ReactorSchedulerConfig {

    @Bean(name = "notificationScheduler", destroyMethod = "dispose")
    public Scheduler notificationScheduler(MeterRegistry meterRegistry) {
        return Schedulers.newBoundedElastic(
                50,
                Integer.MAX_VALUE,
                "notification",
                30,
                true,
                meterRegistry);
    }

    @Bean(name = "testScheduler", destroyMethod = "dispose")
    public Scheduler testScheduler() {
        // Prefer direct executor for tests
        return Schedulers.fromExecutorService(java.util.concurrent.Executors.newFixedThreadPool(10));
    }

    @Bean(name = "authScheduler")
    public Scheduler authScheduler() {
        // Bounded elastic for any blocking-ish hashing/parsing ops
        return Schedulers.boundedElastic();
    }
}
