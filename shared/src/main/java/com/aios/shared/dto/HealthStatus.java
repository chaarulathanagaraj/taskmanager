package com.aios.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Health status response DTO.
 * 
 * <p>
 * Represents the overall health status of the AIOS system,
 * including individual component health checks.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthStatus {

    /**
     * Overall system status: UP, DOWN, or DEGRADED.
     */
    private String status;

    /**
     * Timestamp when health check was performed.
     */
    private Instant timestamp;

    /**
     * Application name.
     */
    private String application;

    /**
     * Application version.
     */
    private String version;

    /**
     * System uptime in milliseconds.
     */
    private long uptimeMs;

    /**
     * Individual component health checks.
     */
    private Map<String, ComponentHealth> checks;

    /**
     * System resource information.
     */
    private SystemResources resources;

    /**
     * Represents health status of an individual component.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentHealth {
        /**
         * Component status: UP, DOWN, or UNKNOWN.
         */
        private String status;

        /**
         * Response time in milliseconds.
         */
        private Long responseTimeMs;

        /**
         * Error message if component is down.
         */
        private String error;

        /**
         * Additional details about the component.
         */
        private Map<String, Object> details;

        public static ComponentHealth up() {
            return ComponentHealth.builder().status("UP").build();
        }

        public static ComponentHealth up(long responseTimeMs) {
            return ComponentHealth.builder()
                    .status("UP")
                    .responseTimeMs(responseTimeMs)
                    .build();
        }

        public static ComponentHealth down(String error) {
            return ComponentHealth.builder()
                    .status("DOWN")
                    .error(error)
                    .build();
        }

        public static ComponentHealth unknown() {
            return ComponentHealth.builder().status("UNKNOWN").build();
        }
    }

    /**
     * System resource information.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemResources {
        /**
         * Total JVM heap memory in bytes.
         */
        private long heapMemoryTotal;

        /**
         * Used JVM heap memory in bytes.
         */
        private long heapMemoryUsed;

        /**
         * Free JVM heap memory in bytes.
         */
        private long heapMemoryFree;

        /**
         * Number of available processors.
         */
        private int availableProcessors;

        /**
         * System CPU usage percentage (0-100).
         */
        private Double cpuUsage;
    }
}
