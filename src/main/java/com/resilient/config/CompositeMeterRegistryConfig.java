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

    /**
     * Creates a {@link CompositeMeterRegistry} bean.
     *
     * <p>This bean is conditionally created only when:
     * <ul>
     *   <li>The "prometheus" profile is active. This is to ensure that it's only used
     *       in environments where Prometheus scraping is expected.</li>
     *   <li>No other {@link MeterRegistry} bean has been defined. This allows for easy
     *       overriding with a different registry implementation if needed.</li>
     * </ul>
     *
     * A {@code CompositeMeterRegistry} is useful for publishing metrics to multiple monitoring
     * systems simultaneously. Spring Boot will automatically add any other {@code MeterRegistry}
     * beans to this composite.
     *
     * @return a new {@link CompositeMeterRegistry} instance.
     */
    @Bean
    @Profile("prometheus")
    @ConditionalOnMissingBean(MeterRegistry.class)
    public MeterRegistry compositeRegistry() {
        return new CompositeMeterRegistry();
    }
}
