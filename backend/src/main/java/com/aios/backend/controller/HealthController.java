package com.aios.backend.controller;

import com.aios.shared.dto.HealthStatus;
import com.aios.shared.dto.HealthStatus.ComponentHealth;
import com.aios.shared.dto.HealthStatus.SystemResources;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health check endpoint for AIOS system monitoring.
 * 
 * <p>
 * Provides detailed health status including database connectivity,
 * external service availability, and system resource information.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Health", description = "System health check endpoints")
public class HealthController {

    private final DataSource dataSource;

    @Value("${spring.application.name:aios-backend}")
    private String applicationName;

    @Value("${aios.agent.url:http://localhost:8081}")
    private String agentUrl;

    @Value("${aios.mcp.url:http://localhost:8082}")
    private String mcpUrl;

    private static final Instant START_TIME = Instant.now();

    /**
     * Get comprehensive health status.
     */
    @GetMapping
    @Operation(summary = "Get health status", description = "Get comprehensive system health status including all component checks")
    public ResponseEntity<HealthStatus> getHealth() {
        log.debug("Performing health check");

        Map<String, ComponentHealth> checks = new LinkedHashMap<>();

        // Check database
        checks.put("database", checkDatabase());

        // Check external services
        checks.put("agent", checkService(agentUrl, "agent"));
        checks.put("mcp-server", checkService(mcpUrl, "mcp-server"));

        // Determine overall status
        String overallStatus = determineOverallStatus(checks);

        HealthStatus status = HealthStatus.builder()
                .status(overallStatus)
                .timestamp(Instant.now())
                .application(applicationName)
                .version("1.0.0-SNAPSHOT")
                .uptimeMs(Duration.between(START_TIME, Instant.now()).toMillis())
                .checks(checks)
                .resources(getSystemResources())
                .build();

        log.debug("Health check completed: {}", overallStatus);
        return ResponseEntity.ok(status);
    }

    /**
     * Simple liveness check.
     */
    @GetMapping("/live")
    @Operation(summary = "Liveness check", description = "Simple check to verify the service is running")
    public ResponseEntity<Map<String, String>> liveness() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "timestamp", Instant.now().toString()));
    }

    /**
     * Readiness check (includes database).
     */
    @GetMapping("/ready")
    @Operation(summary = "Readiness check", description = "Check if the service is ready to accept traffic")
    public ResponseEntity<Map<String, Object>> readiness() {
        ComponentHealth dbHealth = checkDatabase();
        boolean ready = "UP".equals(dbHealth.getStatus());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", ready ? "READY" : "NOT_READY");
        response.put("timestamp", Instant.now().toString());
        response.put("database", dbHealth.getStatus());

        if (ready) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(503).body(response);
        }
    }

    /**
     * Check database connectivity.
     */
    private ComponentHealth checkDatabase() {
        long start = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection()) {
            // Execute simple query to verify connection
            conn.createStatement().execute("SELECT 1");
            long responseTime = System.currentTimeMillis() - start;

            return ComponentHealth.builder()
                    .status("UP")
                    .responseTimeMs(responseTime)
                    .details(Map.of(
                            "database", conn.getMetaData().getDatabaseProductName(),
                            "version", conn.getMetaData().getDatabaseProductVersion()))
                    .build();
        } catch (Exception e) {
            log.warn("Database health check failed: {}", e.getMessage());
            return ComponentHealth.down(e.getMessage());
        }
    }

    /**
     * Check external service connectivity.
     */
    private ComponentHealth checkService(String baseUrl, String serviceName) {
        long start = System.currentTimeMillis();
        try {
            WebClient client = WebClient.builder()
                    .baseUrl(baseUrl)
                    .build();

            // Try to reach the service's health endpoint
            String response = client.get()
                    .uri("/actuator/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            long responseTime = System.currentTimeMillis() - start;

            return ComponentHealth.builder()
                    .status("UP")
                    .responseTimeMs(responseTime)
                    .details(Map.of("url", baseUrl))
                    .build();
        } catch (Exception e) {
            log.debug("{} health check failed: {}", serviceName, e.getMessage());
            return ComponentHealth.builder()
                    .status("DOWN")
                    .error(e.getMessage())
                    .details(Map.of("url", baseUrl))
                    .build();
        }
    }

    /**
     * Get system resource information.
     */
    private SystemResources getSystemResources() {
        Runtime runtime = Runtime.getRuntime();
        var osBean = ManagementFactory.getOperatingSystemMXBean();

        Double cpuUsage = null;
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            cpuUsage = sunOsBean.getCpuLoad() * 100;
            if (cpuUsage < 0)
                cpuUsage = null; // CPU load not available yet
        }

        return SystemResources.builder()
                .heapMemoryTotal(runtime.totalMemory())
                .heapMemoryUsed(runtime.totalMemory() - runtime.freeMemory())
                .heapMemoryFree(runtime.freeMemory())
                .availableProcessors(runtime.availableProcessors())
                .cpuUsage(cpuUsage)
                .build();
    }

    /**
     * Determine overall system status based on component checks.
     */
    private String determineOverallStatus(Map<String, ComponentHealth> checks) {
        boolean allUp = true;
        boolean anyDown = false;

        for (ComponentHealth health : checks.values()) {
            if (!"UP".equals(health.getStatus())) {
                allUp = false;
            }
            if ("DOWN".equals(health.getStatus())) {
                anyDown = true;
            }
        }

        // Database being down is critical
        if ("DOWN".equals(checks.get("database").getStatus())) {
            return "DOWN";
        }

        if (allUp) {
            return "UP";
        } else if (anyDown) {
            return "DEGRADED";
        } else {
            return "UP";
        }
    }
}
