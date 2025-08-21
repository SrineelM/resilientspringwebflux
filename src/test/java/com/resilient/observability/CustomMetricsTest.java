package com.resilient.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustomMetricsTest {
    private MeterRegistry registry;
    private CustomMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new CustomMetrics(registry);
    }

    @Test
    void countUserCreatedEvent_happyPath() {
        // when
        metrics.countUserCreatedEvent();
        metrics.countUserCreatedEvent();

        // then
        assertThat(registry.get("user_created_events_total").counter().count()).isEqualTo(2.0);
    }

    @Test
    void recordUserCreateDuration_happyPath() {
        // when
        metrics.recordUserLatency(100L);
        metrics.recordUserLatency(200L);

        // then
        assertThat(registry.get("user_create_duration_millis").timer().count()).isEqualTo(2);
        assertThat(registry.get("user_create_duration_millis").timer().totalTime(TimeUnit.MILLISECONDS))
                .isEqualTo(300.0);
    }
}
