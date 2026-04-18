package com.aios.backend.controller;

import com.aios.backend.model.IssueEntity;
import com.aios.backend.model.ActionEntity;
import com.aios.backend.model.MetricEntity;
import com.aios.backend.repository.IssueRepository;
import com.aios.backend.repository.ActionRepository;
import com.aios.backend.repository.MetricRepository;
import com.aios.shared.enums.ActionStatus;
import com.aios.shared.enums.Severity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard statistics controller for UI widgets.
 * 
 * <p>
 * Provides aggregated statistics for the dashboard:
 * <ul>
 * <li>Issues resolved today</li>
 * <li>System health score (0-100)</li>
 * <li>Time saved estimate</li>
 * <li>Remediation success rate</li>
 * <li>Activity summary</li>
 * </ul>
 * 
 * @author AIOS Team
 * @since 1.0
 */
@RestController
@RequestMapping("/api/dashboard/stats")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class DashboardStatsController {

    private final IssueRepository issueRepository;
    private final ActionRepository actionRepository;
    private final MetricRepository metricRepository;

    /**
     * Get count of issues resolved today.
     */
    @GetMapping("/issues-resolved-today")
    public ResponseEntity<Long> getIssuesResolvedToday() {
        Instant startOfDay = LocalDate.now()
                .atStartOfDay()
                .atZone(ZoneId.systemDefault())
                .toInstant();

        long count = issueRepository.countByResolvedTrueAndResolvedAtAfter(startOfDay);
        return ResponseEntity.ok(count);
    }

    /**
     * Get total issues resolved all time.
     */
    @GetMapping("/total-resolved")
    public ResponseEntity<Long> getTotalResolved() {
        long count = issueRepository.countByResolvedTrue();
        return ResponseEntity.ok(count);
    }

    /**
     * Get current active issues count.
     */
    @GetMapping("/active-issues")
    public ResponseEntity<Long> getActiveIssuesCount() {
        long count = issueRepository.countByResolvedFalse();
        return ResponseEntity.ok(count);
    }

    /**
     * Calculate system health score (0-100).
     * 
     * <p>
     * Score is calculated based on:
     * <ul>
     * <li>Active issues (weighted by severity) - 40% weight</li>
     * <li>Current CPU/memory usage - 30% weight</li>
     * <li>Recent remediation success rate - 30% weight</li>
     * </ul>
     */
    @GetMapping("/system-health-score")
    public ResponseEntity<Integer> getHealthScore() {
        int score = calculateHealthScore();
        return ResponseEntity.ok(score);
    }

    /**
     * Calculate health score breakdown with details.
     */
    @GetMapping("/health-breakdown")
    public ResponseEntity<Map<String, Object>> getHealthBreakdown() {
        Map<String, Object> breakdown = new HashMap<>();

        // Issue score (40% weight)
        int issueScore = calculateIssueScore();
        breakdown.put("issueScore", issueScore);
        breakdown.put("issueWeight", 0.4);

        // Resource score (30% weight)
        int resourceScore = calculateResourceScore();
        breakdown.put("resourceScore", resourceScore);
        breakdown.put("resourceWeight", 0.3);

        // Remediation score (30% weight)
        int remediationScore = calculateRemediationScore();
        breakdown.put("remediationScore", remediationScore);
        breakdown.put("remediationWeight", 0.3);

        // Overall score
        int overallScore = (int) (issueScore * 0.4 + resourceScore * 0.3 + remediationScore * 0.3);
        breakdown.put("overallScore", overallScore);

        // Status label
        String status;
        if (overallScore >= 90)
            status = "Excellent";
        else if (overallScore >= 75)
            status = "Good";
        else if (overallScore >= 50)
            status = "Fair";
        else if (overallScore >= 25)
            status = "Poor";
        else
            status = "Critical";
        breakdown.put("status", status);

        return ResponseEntity.ok(breakdown);
    }

    /**
     * Estimate time saved by automated remediation.
     * 
     * <p>
     * Assumes each auto-remediated issue saves approximately 30 minutes
     * of manual troubleshooting time.
     */
    @GetMapping("/time-saved")
    public ResponseEntity<Map<String, Object>> getTimeSaved() {
        long resolvedCount = issueRepository.countByResolvedTrue();
        long successfulActions = actionRepository.countByStatus(ActionStatus.SUCCESS);

        // Assume 30 minutes saved per resolved issue
        long minutesSaved = resolvedCount * 30;

        Map<String, Object> result = new HashMap<>();
        result.put("totalMinutes", minutesSaved);
        result.put("hours", minutesSaved / 60);
        result.put("humanReadable", formatDuration(Duration.ofMinutes(minutesSaved)));
        result.put("issuesResolved", resolvedCount);
        result.put("actionsExecuted", successfulActions);

        return ResponseEntity.ok(result);
    }

    /**
     * Get remediation success rate.
     */
    @GetMapping("/remediation-rate")
    public ResponseEntity<Map<String, Object>> getRemediationRate() {
        long total = actionRepository.count();
        long successful = actionRepository.countByStatus(ActionStatus.SUCCESS);
        long failed = actionRepository.countByStatus(ActionStatus.FAILED);
        long blocked = actionRepository.countByStatus(ActionStatus.BLOCKED_BY_POLICY);

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("successful", successful);
        result.put("failed", failed);
        result.put("blocked", blocked);
        result.put("successRate", total > 0 ? (double) successful / total * 100 : 100.0);

        return ResponseEntity.ok(result);
    }

    /**
     * Get activity summary for the last 24 hours.
     */
    @GetMapping("/activity-summary")
    public ResponseEntity<Map<String, Object>> getActivitySummary() {
        Instant last24h = Instant.now().minus(Duration.ofHours(24));

        long issuesDetected = issueRepository.countByDetectedAtAfter(last24h);
        long issuesResolved = issueRepository.countByResolvedTrueAndResolvedAtAfter(last24h);
        long actionsExecuted = actionRepository.countByExecutedAtAfter(last24h);

        Map<String, Object> result = new HashMap<>();
        result.put("issuesDetected", issuesDetected);
        result.put("issuesResolved", issuesResolved);
        result.put("actionsExecuted", actionsExecuted);
        result.put("period", "24h");

        return ResponseEntity.ok(result);
    }

    /**
     * Get issues by severity distribution.
     */
    @GetMapping("/issues-by-severity")
    public ResponseEntity<Map<String, Long>> getIssuesBySeverity() {
        Map<String, Long> distribution = new HashMap<>();

        for (Severity severity : Severity.values()) {
            long count = issueRepository.countBySeverityAndResolvedFalse(severity);
            distribution.put(severity.name(), count);
        }

        return ResponseEntity.ok(distribution);
    }

    // ============================================
    // Private Helper Methods
    // ============================================

    private int calculateHealthScore() {
        int issueScore = calculateIssueScore();
        int resourceScore = calculateResourceScore();
        int remediationScore = calculateRemediationScore();

        return (int) (issueScore * 0.4 + resourceScore * 0.3 + remediationScore * 0.3);
    }

    private int calculateIssueScore() {
        // Start with perfect score
        int score = 100;

        // Deduct points for active issues by severity
        List<IssueEntity> activeIssues = issueRepository.findByResolvedFalse();

        for (IssueEntity issue : activeIssues) {
            switch (issue.getSeverity()) {
                case CRITICAL -> score -= 25;
                case HIGH -> score -= 15;
                case MEDIUM -> score -= 8;
                case LOW -> score -= 3;
            }
        }

        return Math.max(0, score);
    }

    private int calculateResourceScore() {
        // Get latest metrics
        List<MetricEntity> recentMetrics = metricRepository
                .findByTimestampAfterOrderByTimestampAsc(Instant.now().minus(Duration.ofMinutes(5)));

        if (recentMetrics.isEmpty()) {
            return 100; // No data, assume healthy
        }

        MetricEntity latest = recentMetrics.get(recentMetrics.size() - 1);

        int score = 100;

        // CPU penalty
        if (latest.getCpuUsage() > 90)
            score -= 30;
        else if (latest.getCpuUsage() > 80)
            score -= 20;
        else if (latest.getCpuUsage() > 70)
            score -= 10;

        // Memory penalty
        double memoryPercent = (double) latest.getMemoryUsed() / latest.getMemoryTotal() * 100;
        if (memoryPercent > 95)
            score -= 30;
        else if (memoryPercent > 85)
            score -= 20;
        else if (memoryPercent > 75)
            score -= 10;

        return Math.max(0, score);
    }

    private int calculateRemediationScore() {
        Instant last24h = Instant.now().minus(Duration.ofHours(24));

        long successful = actionRepository.countByStatusAndExecutedAtAfter(
                ActionStatus.SUCCESS, last24h);
        long failed = actionRepository.countByStatusAndExecutedAtAfter(
                ActionStatus.FAILED, last24h);

        long total = successful + failed;

        if (total == 0) {
            return 100; // No actions needed, perfect score
        }

        return (int) ((double) successful / total * 100);
    }

    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();

        if (hours > 24) {
            long days = hours / 24;
            hours = hours % 24;
            return String.format("%d days, %d hours", days, hours);
        } else if (hours > 0) {
            return String.format("%d hours, %d minutes", hours, minutes);
        } else {
            return String.format("%d minutes", minutes);
        }
    }
}
