package com.aios.backend.controller;

import com.aios.backend.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for dashboard data aggregation.
 * 
 * <p>Provides a consolidated endpoint that aggregates data from
 * multiple services for efficient dashboard rendering.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * Get complete dashboard data.
     * 
     * <p>Returns aggregated data including:
     * <ul>
     *   <li>Current system metrics (CPU, memory, disk, network)</li>
     *   <li>Top processes by CPU and memory</li>
     *   <li>Active issues with counts by severity</li>
     *   <li>Recent actions with success rates</li>
     *   <li>System health status</li>
     * </ul>
     * 
     * <p>This endpoint is optimized for single-call dashboard loading,
     * reducing multiple API calls from the frontend.
     * 
     * @return dashboard data object
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getDashboard() {
        log.debug("Fetching complete dashboard data");
        Map<String, Object> data = dashboardService.getDashboardData();
        return ResponseEntity.ok(data);
    }

    /**
     * Get dashboard overview (lightweight version).
     * 
     * <p>Returns only summary metrics without detailed lists.
     * Useful for widgets or auto-refresh components.
     * 
     * @return dashboard overview object
     */
    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getDashboardOverview() {
        log.debug("Fetching dashboard overview");
        Map<String, Object> overview = dashboardService.getDashboardOverview();
        return ResponseEntity.ok(overview);
    }

    /**
     * Get system health status.
     * 
     * <p>Calculates overall system health based on:
     * <ul>
     *   <li>Active critical/high issues</li>
     *   <li>CPU and memory usage levels</li>
     *   <li>Recent action failure rates</li>
     * </ul>
     * 
     * @return health status (HEALTHY, WARNING, CRITICAL)
     */
    @GetMapping("/health")
    public ResponseEntity<String> getSystemHealth() {
        log.debug("Fetching system health status");
        String health = dashboardService.getSystemHealth();
        return ResponseEntity.ok(health);
    }

    /**
     * Get trending data for charts.
     * 
     * @param hours number of hours to analyze (default: 24)
     * @return trending data with time series
     */
    @GetMapping("/trends")
    public ResponseEntity<Map<String, Object>> getTrends(
            @RequestParam(defaultValue = "24") int hours) {
        log.debug("Fetching trending data for last {} hours", hours);
        Map<String, Object> trends = dashboardService.getTrendingData(hours);
        return ResponseEntity.ok(trends);
    }
}
