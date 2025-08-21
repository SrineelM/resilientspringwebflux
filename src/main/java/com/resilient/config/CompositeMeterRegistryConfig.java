// CompositeMeterRegistryConfig.java
//
// Sets up a composite MeterRegistry for Prometheus and other metrics backends.
// - Only registers if Prometheus profile is active and no MeterRegistry bean exists.
// - For business metrics, prefer exposing MeterBinder beans.

package com.resilient.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configures a composite MeterRegistry for Prometheus and other backends. Expose MeterBinder beans
 * for business metrics. Use @Profile("prometheus") to avoid duplicate registries.
 */
@Configuration
public class CompositeMeterRegistryConfig {

    @Bean
    @Profile("prometheus")
    @ConditionalOnMissingBean(MeterRegistry.class)
    public MeterRegistry compositeRegistry() {
        return new CompositeMeterRegistry();
    }
}
