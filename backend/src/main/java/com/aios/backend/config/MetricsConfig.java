package com.aios.backend.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Micrometer metrics configuration.
 * 
 * <p>
 * Configures custom metrics, common tags, and enables
 * automatic method timing with @Timed annotation.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Configuration
@EnableAspectJAutoProxy
@Slf4j
public class MetricsConfig {

    /**
     * Customize the meter registry with common tags.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags(Tags.of(
                        Tag.of("application", "aios-backend"),
                        Tag.of("environment", getEnvironment())));
    }

    /**
     * Enable @Timed annotation support for automatic method timing.
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    /**
     * Get current environment from system properties.
     */
    private String getEnvironment() {
        String env = System.getProperty("spring.profiles.active");
        return env != null ? env : "default";
    }
}
