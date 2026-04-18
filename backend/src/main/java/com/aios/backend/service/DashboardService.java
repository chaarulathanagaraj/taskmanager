package com.aios.backend.service;

import com.aios.backend.model.ActionEntity;
import com.aios.backend.model.IssueEntity;
import com.aios.backend.model.MetricEntity;
import com.aios.shared.enums.ActionStatus;
import com.aios.shared.enums.Severity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for aggregating dashboard data.
 * 
 * <p>
 * Provides high-level views combining metrics, issues, and actions
 * to support dashboard displays. Reduces the number of API calls required
 * by the frontend by bundling related data together.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final MetricService metricService;
    private final IssueService issueService;
    private final ActionService actionService;
    private final WebSocketBroadcaster broadcaster;

    /**
     * Get complete dashboard data.
     * Aggregates recent metrics, active issues, recent actions, and statistics.
     * 
     * @return map containing all dashboard data
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDashboardData() {
        log.debug("Building complete dashboard data");

        // Recent metrics (last 10 minutes)
        List<MetricEntity> recentMetrics = metricService.getRecentMetrics(10);

        // Get latest metric for current values
        List<MetricEntity> latestMetrics = metricService.getLatestMetrics(1);
        MetricEntity latestMetric = !latestMetrics.isEmpty() ? latestMetrics.get(0) : null;

        // Active issues
        List<IssueEntity> activeIssues = issueService.getActiveIssues();
        List<IssueEntity> highPriorityIssues = issueService.getHighPriorityIssues();

        // Recent actions (last 24 hours)
        List<ActionEntity> recentActions = actionService.getRecentActions(24);

        // Metric statistics (last hour)
        Map<String, Object> metricStats = metricService.getStatistics(
                Instant.now().minus(1, ChronoUnit.HOURS),
                Instant.now());

        // Action statistics (last 24 hours)
        Map<String, Object> actionStats = actionService.getStatistics(24);

        // Get top processes
        List<Map<String, Object>> topProcesses = getTopProcesses(20);

        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("currentTime", Instant.now());
        dashboard.put("recentMetrics", recentMetrics);
        dashboard.put("activeIssues", activeIssues);
        dashboard.put("highPriorityIssues", highPriorityIssues);
        dashboard.put("recentActions", recentActions);
        dashboard.put("metricStatistics", metricStats);
        dashboard.put("actionStatistics", actionStats);
        dashboard.put("systemHealth", calculateSystemHealth(activeIssues));

        // Add current metric values from latest metric or collect in real-time using
        // OSHI
        if (latestMetric != null) {
            dashboard.put("cpuUsage", latestMetric.getCpuUsage());
            dashboard.put("memoryPercent",
                    (latestMetric.getMemoryUsed() * 100.0) / latestMetric.getMemoryTotal());
            dashboard.put("diskIO",
                    (latestMetric.getDiskRead() + latestMetric.getDiskWrite()) / (1024.0 * 1024.0)); // MB/s
            dashboard.put("networkIO",
                    (latestMetric.getNetworkSent() + latestMetric.getNetworkReceived()) / (1024.0 * 1024.0)); // MB/s
        } else {
            // No stored metrics - collect real-time data using OSHI
            log.debug("No stored metrics found, collecting real-time system metrics using OSHI");
            Map<String, Object> realtimeMetrics = collectRealtimeMetrics();
            dashboard.put("cpuUsage", realtimeMetrics.get("cpuUsage"));
            dashboard.put("memoryPercent", realtimeMetrics.get("memoryPercent"));
            dashboard.put("diskIO", realtimeMetrics.get("diskIO"));
            dashboard.put("networkIO", realtimeMetrics.get("networkIO"));
        }

        dashboard.put("topProcesses", topProcesses);

        return dashboard;
    }

    /**
     * Get lightweight dashboard overview.
     * Provides summary statistics without full data lists.
     * 
     * @return map containing overview statistics
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDashboardOverview() {
        log.debug("Building dashboard overview");

        long activeIssueCount = issueService.getActiveIssueCount();
        long criticalIssueCount = issueService.getIssueCountBySeverity(Severity.CRITICAL);
        long highIssueCount = issueService.getIssueCountBySeverity(Severity.HIGH);

        List<ActionEntity> recentActions = actionService.getRecentActions(1);
        long recentActionCount = recentActions.size();
        long recentSuccessfulActions = recentActions.stream()
                .filter(ActionEntity::isSuccessful)
                .count();

        List<MetricEntity> latestMetrics = metricService.getLatestMetrics(1);
        MetricEntity latestMetric = !latestMetrics.isEmpty() ? latestMetrics.get(0) : null;

        Map<String, Object> overview = new HashMap<>();
        overview.put("currentTime", Instant.now());
        overview.put("activeIssueCount", activeIssueCount);
        overview.put("criticalIssueCount", criticalIssueCount);
        overview.put("highIssueCount", highIssueCount);
        overview.put("recentActionCount", recentActionCount);
        overview.put("recentSuccessfulActionCount", recentSuccessfulActions);
        overview.put("latestMetric", latestMetric);
        overview.put("systemHealth", getSystemHealth());

        return overview;
    }

    /**
     * Get system health status.
     * 
     * @return health status (HEALTHY, WARNING, or CRITICAL)
     */
    @Transactional(readOnly = true)
    public String getSystemHealth() {
        log.debug("Calculating system health");

        List<IssueEntity> activeIssues = issueService.getActiveIssues();
        String health = calculateSystemHealth(activeIssues);

        // Broadcast health status change
        broadcaster.broadcastHealthStatus(health);

        return health;
    }

    /**
     * Get trending data for charts.
     * 
     * @param hours number of hours to look back
     * @return map containing time-series data
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTrendingData(int hours) {
        log.debug("Building trending data for {} hours", hours);

        Instant start = Instant.now().minus(hours, ChronoUnit.HOURS);
        Instant end = Instant.now();

        // Metrics over time
        List<MetricEntity> metrics = metricService.getMetricsInRange(start, end);

        // Actions over time
        List<ActionEntity> actions = actionService.getRecentActions(hours);

        // Calculate trends
        Map<String, Object> trends = new HashMap<>();
        trends.put("timeRange", Map.of("start", start, "end", end, "hours", hours));
        trends.put("metrics", metrics);
        trends.put("actions", actions);
        trends.put("metricCount", metrics.size());
        trends.put("actionCount", actions.size());

        return trends;
    }

    /**
     * Calculate overall system health based on active issues.
     * 
     * @param activeIssues list of active issues
     * @return HEALTHY, WARNING, or CRITICAL
     */
    private String calculateSystemHealth(List<IssueEntity> activeIssues) {
        if (activeIssues.isEmpty()) {
            return "HEALTHY";
        }

        long criticalCount = activeIssues.stream()
                .filter(i -> i.getSeverity() == Severity.CRITICAL)
                .count();

        long highCount = activeIssues.stream()
                .filter(i -> i.getSeverity() == Severity.HIGH)
                .count();

        if (criticalCount > 0) {
            return "CRITICAL";
        } else if (highCount > 0 || activeIssues.size() > 5) {
            return "WARNING";
        } else {
            return "HEALTHY";
        }
    }

    /**
     * Get top processes sorted by CPU usage.
     * Uses OSHI to collect real-time process information.
     * 
     * @param limit maximum number of processes to return
     * @return list of process information maps
     */
    private List<Map<String, Object>> getTopProcesses(int limit) {
        try {
            SystemInfo si = new SystemInfo();
            OperatingSystem os = si.getOperatingSystem();
            List<OSProcess> processes = os.getProcesses();

            return processes.stream()
                    .filter(proc -> proc.getName() != null && !proc.getName().isEmpty())
                    .sorted(Comparator.comparingDouble(OSProcess::getProcessCpuLoadCumulative).reversed())
                    .limit(limit)
                    .map(proc -> {
                        Map<String, Object> info = new HashMap<>();
                        info.put("pid", proc.getProcessID());
                        info.put("name", proc.getName());
                        info.put("cpuPercent", Math.round(proc.getProcessCpuLoadCumulative() * 100 * 100.0) / 100.0);
                        info.put("memoryBytes", proc.getResidentSetSize());
                        info.put("threadCount", proc.getThreadCount());
                        info.put("handleCount", proc.getOpenFiles());
                        info.put("ioReadBytes", proc.getBytesRead());
                        info.put("ioWriteBytes", proc.getBytesWritten());
                        return info;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get top processes: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Collect real-time system metrics using OSHI.
     * Used when no stored metrics are available.
     * 
     * @return map containing current system metrics
     */
    private Map<String, Object> collectRealtimeMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        try {
            SystemInfo si = new SystemInfo();
            var hal = si.getHardware();
            var processor = hal.getProcessor();
            var memory = hal.getMemory();

            // CPU usage (system-wide) - use ticks-based calculation for accuracy
            long[] prevTicks = processor.getSystemCpuLoadTicks();
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignore) {
            }
            double cpuUsage = processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100.0;
            // Fallback if CPU load is 0 or negative
            if (cpuUsage <= 0) {
                cpuUsage = processor.getSystemCpuLoad(500) * 100.0;
            }
            metrics.put("cpuUsage", Math.round(cpuUsage * 100.0) / 100.0);

            // Memory usage
            long totalMemory = memory.getTotal();
            long availableMemory = memory.getAvailable();
            long usedMemory = totalMemory - availableMemory;
            double memoryPercent = (usedMemory * 100.0) / totalMemory;
            metrics.put("memoryPercent", Math.round(memoryPercent * 100.0) / 100.0);
            metrics.put("memoryUsed", usedMemory);
            metrics.put("memoryTotal", totalMemory);

            // Disk I/O (simplified - just get current values)
            var diskStores = hal.getDiskStores();
            long totalReadBytes = diskStores.stream().mapToLong(d -> d.getReadBytes()).sum();
            long totalWriteBytes = diskStores.stream().mapToLong(d -> d.getWriteBytes()).sum();
            metrics.put("diskIO", (totalReadBytes + totalWriteBytes) / (1024.0 * 1024.0)); // MB

            // Network I/O (simplified - just get current values)
            var networks = hal.getNetworkIFs();
            long totalSent = networks.stream().mapToLong(n -> n.getBytesSent()).sum();
            long totalReceived = networks.stream().mapToLong(n -> n.getBytesRecv()).sum();
            metrics.put("networkIO", (totalSent + totalReceived) / (1024.0 * 1024.0)); // MB

            log.debug("Collected real-time metrics: CPU={}%, Memory={}%",
                    metrics.get("cpuUsage"), metrics.get("memoryPercent"));

        } catch (Exception e) {
            log.error("Failed to collect real-time metrics: {}", e.getMessage(), e);
            // Return default values on error
            metrics.put("cpuUsage", 0.0);
            metrics.put("memoryPercent", 0.0);
            metrics.put("diskIO", 0.0);
            metrics.put("networkIO", 0.0);
        }

        return metrics;
    }
}
