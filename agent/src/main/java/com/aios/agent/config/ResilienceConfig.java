package com.aios.agent.config;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

/**
 * Resilience4j configuration for backend communication retry logic.
 * 
 * <p>Provides retry policies with:
 * <ul>
 *   <li>Exponential backoff</li>
 *   <li>Configurable max attempts</li>
 *   <li>Selective exception handling</li>
 *   <li>Event listeners for monitoring</li>
 * </ul>
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Configuration
@Slf4j
public class ResilienceConfig {

    /**
     * Retry configuration for backend metric sync.
     * Less aggressive - metrics can tolerate some delay.
     */
    @Bean
    public Retry metricSyncRetry() {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)
            .retryOnException(e -> e instanceof WebClientResponseException 
                || e instanceof java.net.ConnectException
                || e instanceof java.util.concurrent.TimeoutException)
            .intervalFunction(io.github.resilience4j.core.IntervalFunction
                .ofExponentialBackoff(Duration.ofSeconds(2), 2.0))
            .failAfterMaxAttempts(true)
            .build();

        Retry retry = Retry.of("metricSync", config);
        
        // Event listeners for logging
        retry.getEventPublisher()
            .onRetry(event -> log.warn("Metric sync retry attempt {} due to: {}", 
                event.getNumberOfRetryAttempts(), 
                event.getLastThrowable().getMessage()))
            .onError(event -> log.error("Metric sync failed after {} attempts", 
                event.getNumberOfRetryAttempts()))
            .onSuccess(event -> {
                if (event.getNumberOfRetryAttempts() > 0) {
                    log.info("Metric sync succeeded after {} retries", 
                        event.getNumberOfRetryAttempts());
                }
            });

        return retry;
    }

    /**
     * Retry configuration for backend issue reporting.
     * More aggressive - issues are high priority.
     */
    @Bean
    public Retry issueSyncRetry() {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(5)
            .retryOnException(e -> e instanceof WebClientResponseException 
                || e instanceof java.net.ConnectException
                || e instanceof java.util.concurrent.TimeoutException)
            .intervalFunction(io.github.resilience4j.core.IntervalFunction
                .ofExponentialBackoff(Duration.ofSeconds(1), 1.5))
            .failAfterMaxAttempts(true)
            .build();

        Retry retry = Retry.of("issueSync", config);
        
        retry.getEventPublisher()
            .onRetry(event -> log.warn("Issue sync retry attempt {} due to: {}", 
                event.getNumberOfRetryAttempts(), 
                event.getLastThrowable().getMessage()))
            .onError(event -> log.error("Issue sync failed after {} attempts", 
                event.getNumberOfRetryAttempts()))
            .onSuccess(event -> {
                if (event.getNumberOfRetryAttempts() > 0) {
                    log.info("Issue sync succeeded after {} retries", 
                        event.getNumberOfRetryAttempts());
                }
            });

        return retry;
    }

    /**
     * Retry configuration for backend action logging.
     * Most aggressive - actions must be logged for audit trail.
     */
    @Bean
    public Retry actionSyncRetry() {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(5)
            .retryOnException(e -> e instanceof WebClientResponseException 
                || e instanceof java.net.ConnectException
                || e instanceof java.util.concurrent.TimeoutException)
            .intervalFunction(io.github.resilience4j.core.IntervalFunction
                .ofExponentialBackoff(Duration.ofMillis(500), 1.5))
            .failAfterMaxAttempts(true)
            .build();

        Retry retry = Retry.of("actionSync", config);
        
        retry.getEventPublisher()
            .onRetry(event -> log.warn("Action sync retry attempt {} due to: {}", 
                event.getNumberOfRetryAttempts(), 
                event.getLastThrowable().getMessage()))
            .onError(event -> log.error("Action sync failed after {} attempts - action may be lost!", 
                event.getNumberOfRetryAttempts()))
            .onSuccess(event -> {
                if (event.getNumberOfRetryAttempts() > 0) {
                    log.info("Action sync succeeded after {} retries", 
                        event.getNumberOfRetryAttempts());
                }
            });

        return retry;
    }

    /**
     * Retry configuration for backend health checks.
     * Minimal retries - health checks are frequent and lightweight.
     */
    @Bean
    public Retry healthCheckRetry() {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(2)
            .waitDuration(Duration.ofSeconds(1))
            .retryOnException(e -> e instanceof WebClientResponseException 
                || e instanceof java.net.ConnectException
                || e instanceof java.util.concurrent.TimeoutException)
            .failAfterMaxAttempts(false) // Don't fail completely
            .build();

        Retry retry = Retry.of("healthCheck", config);
        
        retry.getEventPublisher()
            .onRetry(event -> log.debug("Health check retry attempt {}", 
                event.getNumberOfRetryAttempts()));

        return retry;
    }

    /**
     * Retry registry for creating custom retries at runtime.
     */
    @Bean
    public RetryRegistry retryRegistry() {
        return RetryRegistry.ofDefaults();
    }
}
