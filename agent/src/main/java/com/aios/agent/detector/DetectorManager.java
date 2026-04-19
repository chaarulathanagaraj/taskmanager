package com.aios.agent.detector;

import com.aios.agent.client.BackendClient;
import com.aios.agent.collector.ProcessInfoCollector;
import com.aios.agent.collector.SystemMetricsCollector;
import com.aios.agent.config.AgentConfiguration;
import com.aios.shared.dto.DiagnosticIssue;
import com.aios.shared.dto.MetricSnapshot;
import com.aios.shared.dto.ProcessInfo;
import com.aios.shared.enums.IssueType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Central manager for coordinating all issue detectors.
 * 
 * Responsibilities:
 * - Schedule periodic detection runs (every 10 seconds)
 * - Collect system metrics and process information
 * - Execute all registered detectors
 * - Deduplicate detected issues
 * - Maintain active issues tracking
 * - Sync issues to backend server
 * - Provide query methods for detected issues
 * 
 * Thread-safe using ConcurrentHashMap for active issues.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DetectorManager {

    private final List<IssueDetector> detectors;
    private final SystemMetricsCollector metricsCollector;
    private final ProcessInfoCollector processCollector;
    private final AgentConfiguration config;
    private final BackendClient backendClient;

    // Track active issues by unique key (PID + IssueType)
    private final Map<String, DiagnosticIssue> activeIssues = new ConcurrentHashMap<>();

    // Track last detection run time
    private Instant lastDetectionRun = Instant.now();

    // Statistics
    private long totalDetectionRuns = 0;
    private long totalIssuesDetected = 0;

    /**
     * Main detection loop - runs every 10 seconds.
     */
    @Scheduled(fixedDelay = 10000, initialDelay = 5000)
    public void runDetection() {
        if (!config.isDetectionEnabled()) {
            log.debug("Detection is disabled in configuration");
            return;
        }

        long startTime = System.currentTimeMillis();
        log.debug("Starting detection run #{}", totalDetectionRuns + 1);

        try {
            // Step 1: Collect system data
            List<MetricSnapshot> recentMetrics = metricsCollector.getRecentMetrics(
                    config.getRetentionMinutes());

            List<ProcessInfo> topProcesses = processCollector.getTopProcesses(
                    config.getMonitoredProcessLimit());

            log.debug("Collected {} metrics and {} processes for analysis",
                    recentMetrics.size(), topProcesses.size());

            // Step 2: Run all detectors
            List<DiagnosticIssue> newIssues = runAllDetectors(recentMetrics, topProcesses);

            // Step 3: Deduplicate issues
            List<DiagnosticIssue> uniqueIssues = deduplicateIssues(newIssues);

            // Step 4: Update active issues
            updateActiveIssues(uniqueIssues);

            // Step 5: Clean up stale issues
            cleanupStaleIssues();

            // Update statistics
            totalDetectionRuns++;
            totalIssuesDetected += uniqueIssues.size();
            lastDetectionRun = Instant.now();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Detection complete: {} detectors, {} issues found, {} active issues, took {}ms",
                    detectors.size(), uniqueIssues.size(), activeIssues.size(), duration);

        } catch (Exception e) {
            log.error("Error during detection run", e);
        }
    }

    /**
     * Execute all registered detectors
     */
    private List<DiagnosticIssue> runAllDetectors(List<MetricSnapshot> metrics,
            List<ProcessInfo> processes) {
        List<DiagnosticIssue> allIssues = new ArrayList<>();

        for (IssueDetector detector : detectors) {
            if (!detector.isEnabled()) {
                log.debug("Detector {} is disabled, skipping", detector.getName());
                continue;
            }

            try {
                long detectorStart = System.currentTimeMillis();
                List<DiagnosticIssue> issues = detector.detect(metrics, processes);
                long detectorDuration = System.currentTimeMillis() - detectorStart;

                if (!issues.isEmpty()) {
                    log.debug("Detector {} found {} issue(s) in {}ms",
                            detector.getName(), issues.size(), detectorDuration);
                    allIssues.addAll(issues);
                }
            } catch (Exception e) {
                log.error("Error running detector: {}", detector.getName(), e);
            }
        }

        return allIssues;
    }

    /**
     * Deduplicate issues by PID + IssueType (keep highest confidence)
     */
    private List<DiagnosticIssue> deduplicateIssues(List<DiagnosticIssue> issues) {
        if (issues.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, DiagnosticIssue> deduped = new HashMap<>();

        for (DiagnosticIssue issue : issues) {
            String key = makeIssueKey(issue.getAffectedPid(), issue.getType());

            // Keep the issue with higher confidence
            DiagnosticIssue existing = deduped.get(key);
            if (existing == null || issue.getConfidence() > existing.getConfidence()) {
                deduped.put(key, issue);
            }
        }

        return new ArrayList<>(deduped.values());
    }

    /**
     * Update active issues map with new detections
     */
    private void updateActiveIssues(List<DiagnosticIssue> newIssues) {
        for (DiagnosticIssue issue : newIssues) {
            String key = makeIssueKey(issue.getAffectedPid(), issue.getType());

            DiagnosticIssue existing = activeIssues.get(key);

            if (existing == null) {
                // New issue detected
                activeIssues.put(key, issue);
                log.warn("NEW ISSUE: {} - {} (PID: {}, Confidence: {:.0f}%)",
                        issue.getType(), issue.getProcessName(),
                        issue.getAffectedPid(), Math.round(issue.getConfidence() * 100));

                // Queue issue for backend sync
                backendClient.queueIssue(issue);
            } else {
                // Update existing issue if confidence changed significantly
                double confidenceDelta = Math.abs(issue.getConfidence() - existing.getConfidence());
                if (confidenceDelta > 0.1) {
                    activeIssues.put(key, issue);
                    log.debug("UPDATED ISSUE: {} - {} (Confidence: {:.0f}% -> {:.0f}%)",
                            issue.getType(), issue.getProcessName(),
                            Math.round(existing.getConfidence() * 100),
                            Math.round(issue.getConfidence() * 100));

                    // Queue updated issue for backend sync
                    backendClient.queueIssue(issue);
                }
            }
        }
    }

    /**
     * Remove issues that haven't been re-detected for over 5 minutes
     */
    private void cleanupStaleIssues() {
        Instant staleThreshold = Instant.now().minus(Duration.ofMinutes(5));

        List<String> staleKeys = activeIssues.entrySet().stream()
                .filter(entry -> entry.getValue().getDetectedAt().isBefore(staleThreshold))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (!staleKeys.isEmpty()) {
            for (String key : staleKeys) {
                DiagnosticIssue removed = activeIssues.remove(key);
                log.info("RESOLVED: {} - {} (no longer detected)",
                        removed.getType(), removed.getProcessName());
            }
        }
    }

    /**
     * Create unique key for issue tracking (PID + IssueType)
     */
    private String makeIssueKey(int pid, IssueType type) {
        return pid + ":" + type.name();
    }

    // ========== Public Query Methods ==========

    /**
     * Get all currently active issues
     */
    public List<DiagnosticIssue> getActiveIssues() {
        return new ArrayList<>(activeIssues.values());
    }

    /**
     * Get active issues for a specific process
     */
    public List<DiagnosticIssue> getIssuesForProcess(int pid) {
        return activeIssues.values().stream()
                .filter(issue -> issue.getAffectedPid() == pid)
                .collect(Collectors.toList());
    }

    /**
     * Get active issues by type
     */
    public List<DiagnosticIssue> getIssuesByType(IssueType type) {
        return activeIssues.values().stream()
                .filter(issue -> issue.getType() == type)
                .collect(Collectors.toList());
    }

    /**
     * Get active issues by minimum confidence threshold
     */
    public List<DiagnosticIssue> getIssuesByConfidence(double minConfidence) {
        return activeIssues.values().stream()
                .filter(issue -> issue.getConfidence() >= minConfidence)
                .sorted(Comparator.comparingDouble(DiagnosticIssue::getConfidence).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Check if a specific issue is currently active
     */
    public boolean hasActiveIssue(int pid, IssueType type) {
        String key = makeIssueKey(pid, type);
        return activeIssues.containsKey(key);
    }

    /**
     * Get specific issue details
     */
    public Optional<DiagnosticIssue> getIssue(int pid, IssueType type) {
        String key = makeIssueKey(pid, type);
        return Optional.ofNullable(activeIssues.get(key));
    }

    /**
     * Clear all active issues (for testing)
     */
    public void clearAllIssues() {
        int count = activeIssues.size();
        activeIssues.clear();
        log.info("Cleared {} active issues", count);
    }

    /**
     * Get list of all registered detector names
     */
    public List<String> getDetectorNames() {
        return detectors.stream()
                .map(IssueDetector::getName)
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Get detection statistics
     */
    public DetectionStatistics getStatistics() {
        return DetectionStatistics.builder()
                .totalDetectionRuns(totalDetectionRuns)
                .totalIssuesDetected(totalIssuesDetected)
                .activeIssuesCount(activeIssues.size())
                .lastDetectionRun(lastDetectionRun)
                .detectorCount(detectors.size())
                .enabledDetectorCount((int) detectors.stream().filter(IssueDetector::isEnabled).count())
                .build();
    }

    /**
     * Statistics about detection operations
     */
    @lombok.Data
    @lombok.Builder
    public static class DetectionStatistics {
        private long totalDetectionRuns;
        private long totalIssuesDetected;
        private int activeIssuesCount;
        private Instant lastDetectionRun;
        private int detectorCount;
        private int enabledDetectorCount;
    }
}
