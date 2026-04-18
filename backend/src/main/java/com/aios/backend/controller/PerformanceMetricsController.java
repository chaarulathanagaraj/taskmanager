package com.aios.backend.controller;

import com.aios.backend.service.PerformanceMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Meter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST controller for application performance metrics.
 * 
 * <p>
 * Provides endpoints for viewing AIOS-specific performance metrics
 * in addition to the standard Spring Actuator metrics endpoints.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Performance Metrics", description = "Application performance metrics endpoints")
public class PerformanceMetricsController {

    private final PerformanceMetrics performanceMetrics;
    private final MeterRegistry meterRegistry;

    /**
     * Get AIOS metrics summary.
     */
    @GetMapping("/summary")
    @Operation(summary = "Get metrics summary", description = "Get summary of AIOS-specific performance metrics")
    public ResponseEntity<PerformanceMetrics.MetricsSummary> getSummary() {
        return ResponseEntity.ok(performanceMetrics.getSummary());
    }

    /**
     * Get all AIOS-specific meters.
     */
    @GetMapping("/aios")
    @Operation(summary = "Get AIOS metrics", description = "Get all AIOS-specific metrics")
    public ResponseEntity<Map<String, Object>> getAiosMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();

        // Get all meters that start with "aios."
        List<Map<String, Object>> aiosMeters = meterRegistry.getMeters().stream()
                .filter(meter -> meter.getId().getName().startsWith("aios."))
                .map(this::meterToMap)
                .collect(Collectors.toList());

        metrics.put("count", aiosMeters.size());
        metrics.put("metrics", aiosMeters);

        return ResponseEntity.ok(metrics);
    }

    /**
     * Get detection metrics.
     */
    @GetMapping("/detection")
    @Operation(summary = "Get detection metrics", description = "Get issue detection performance metrics")
    public ResponseEntity<Map<String, Object>> getDetectionMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();

        meterRegistry.getMeters().stream()
                .filter(m -> m.getId().getName().contains("detection"))
                .forEach(meter -> addMeterValues(metrics, meter));

        return ResponseEntity.ok(metrics);
    }

    /**
     * Get remediation metrics.
     */
    @GetMapping("/remediation")
    @Operation(summary = "Get remediation metrics", description = "Get remediation action performance metrics")
    public ResponseEntity<Map<String, Object>> getRemediationMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();

        meterRegistry.getMeters().stream()
                .filter(m -> m.getId().getName().contains("remediation"))
                .forEach(meter -> addMeterValues(metrics, meter));

        return ResponseEntity.ok(metrics);
    }

    /**
     * Get AI diagnosis metrics.
     */
    @GetMapping("/ai")
    @Operation(summary = "Get AI metrics", description = "Get AI diagnosis performance metrics")
    public ResponseEntity<Map<String, Object>> getAiMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();

        meterRegistry.getMeters().stream()
                .filter(m -> m.getId().getName().contains("ai."))
                .forEach(meter -> addMeterValues(metrics, meter));

        return ResponseEntity.ok(metrics);
    }

    /**
     * Convert a meter to a map representation.
     */
    private Map<String, Object> meterToMap(Meter meter) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", meter.getId().getName());
        map.put("type", meter.getId().getType().name());

        // Add tags
        Map<String, String> tags = new LinkedHashMap<>();
        meter.getId().getTags().forEach(tag -> tags.put(tag.getKey(), tag.getValue()));
        if (!tags.isEmpty()) {
            map.put("tags", tags);
        }

        // Add measurements
        List<Map<String, Object>> measurements = new ArrayList<>();
        meter.measure().forEach(measurement -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("statistic", measurement.getStatistic().name());
            m.put("value", measurement.getValue());
            measurements.add(m);
        });
        map.put("measurements", measurements);

        return map;
    }

    /**
     * Add meter values to a metrics map.
     */
    private void addMeterValues(Map<String, Object> metrics, Meter meter) {
        String name = meter.getId().getName();
        String tags = meter.getId().getTags().stream()
                .map(t -> t.getKey() + "=" + t.getValue())
                .collect(Collectors.joining(","));

        String key = tags.isEmpty() ? name : name + "{" + tags + "}";

        Map<String, Double> values = new LinkedHashMap<>();
        meter.measure().forEach(
                measurement -> values.put(measurement.getStatistic().name().toLowerCase(), measurement.getValue()));

        metrics.put(key, values);
    }
}
