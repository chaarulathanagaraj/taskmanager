package com.aios.backend.controller;

import com.aios.backend.model.MetricEntity;
import com.aios.backend.service.MetricService;
import com.aios.shared.dto.MetricSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for system metrics endpoints.
 * 
 * <p>
 * Provides APIs for:
 * <ul>
 * <li>Receiving metrics from the agent</li>
 * <li>Querying recent metrics for dashboard</li>
 * <li>Retrieving metrics statistics</li>
 * </ul>
 * 
 * @author AIOS Team
 * @since 1.0
 */
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class MetricController {

    private final MetricService metricService;

    /**
     * Save metrics batch from agent.
     * Agent sends batches every 30 seconds.
     * 
     * @param metrics list of metric snapshots to save
     * @return empty response with 200 OK status
     */
    @PostMapping
    public ResponseEntity<Void> saveMetrics(@RequestBody List<MetricSnapshot> metrics) {
        log.debug("Received {} metric snapshots from agent", metrics.size());
        metricService.saveMetrics(metrics);
        return ResponseEntity.ok().build();
    }

    /**
     * Get recent metrics for dashboard display.
     * Default: last 10 minutes of data.
     * If no stored metrics exist, generates a single current snapshot using OSHI.
     * 
     * @param minutes number of minutes to retrieve (default: 10)
     * @return list of recent metrics
     */
    @GetMapping("/recent")
    public ResponseEntity<List<MetricSnapshot>> getRecentMetrics(
            @RequestParam(defaultValue = "10") int minutes) {
        log.debug("Fetching metrics for last {} minutes", minutes);
        List<MetricEntity> entities = metricService.getRecentMetrics(minutes);
        
        if (entities.isEmpty()) {
            // No stored metrics - return current snapshot using OSHI
            log.debug("No stored metrics found, generating current snapshot using OSHI");
            MetricSnapshot currentSnapshot = metricService.collectCurrentMetric();
            return ResponseEntity.ok(List.of(currentSnapshot));
        }
        
        List<MetricSnapshot> metrics = entities.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(metrics);
    }

    /**
     * Get metrics within a specific time range.
     * 
     * @param startTime start timestamp (ISO-8601 format)
     * @param endTime   end timestamp (ISO-8601 format)
     * @return list of metrics in the range
     */
    @GetMapping("/range")
    public ResponseEntity<List<MetricSnapshot>> getMetricsInRange(
            @RequestParam String startTime,
            @RequestParam String endTime) {
        log.debug("Fetching metrics from {} to {}", startTime, endTime);
        Instant start = Instant.parse(startTime);
        Instant end = Instant.parse(endTime);
        List<MetricEntity> entities = metricService.getMetricsInRange(start, end);
        List<MetricSnapshot> metrics = entities.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(metrics);
    }

    /**
     * Get the latest N metrics.
     * 
     * @param limit number of records to return (default: 100)
     * @return list of most recent metrics
     */
    @GetMapping("/latest")
    public ResponseEntity<List<MetricSnapshot>> getLatestMetrics(
            @RequestParam(defaultValue = "100") int limit) {
        log.debug("Fetching latest {} metrics", limit);
        List<MetricEntity> entities = metricService.getLatestMetrics(limit);
        List<MetricSnapshot> metrics = entities.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(metrics);
    }

    /**
     * Get statistics about stored metrics.
     * 
     * @return statistics object
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getMetricStatistics() {
        log.debug("Fetching metric statistics");
        Map<String, Object> stats = metricService.getStatistics(null, null);
        return ResponseEntity.ok(stats);
    }

    /**
     * Delete old metrics (cleanup endpoint).
     * Requires admin role in production.
     * 
     * @param olderThanDays delete metrics older than this many days
     * @return number of deleted records
     */
    @DeleteMapping("/cleanup")
    public ResponseEntity<Integer> cleanupOldMetrics(
            @RequestParam(defaultValue = "7") int olderThanDays) {
        log.info("Cleaning up metrics older than {} days", olderThanDays);
        int deleted = metricService.deleteOldMetrics(olderThanDays);
        return ResponseEntity.ok(deleted);
    }

    /**
     * Convert MetricEntity to MetricSnapshot DTO.
     */
    private MetricSnapshot toDto(MetricEntity entity) {
        return MetricSnapshot.builder()
                .timestamp(entity.getTimestamp())
                .cpuUsage(entity.getCpuUsage())
                .memoryUsed(entity.getMemoryUsed())
                .memoryTotal(entity.getMemoryTotal())
                .diskRead(entity.getDiskRead())
                .diskWrite(entity.getDiskWrite())
                .networkSent(entity.getNetworkSent())
                .networkReceived(entity.getNetworkReceived())
                .build();
    }
}
