package com.aios.agent.detector;

import com.aios.shared.dto.DiagnosticIssue;
import com.aios.shared.dto.MetricSnapshot;
import com.aios.shared.dto.ProcessInfo;
import com.aios.shared.enums.IssueType;
import com.aios.shared.enums.Severity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detector for identifying memory leak patterns in processes.
 * 
 * Uses linear regression analysis to detect steady memory growth over time.
 * Tracks memory usage history per process and calculates:
 * - Growth rate (bytes/second)
 * - Trend confidence (R² coefficient)
 * - Pattern stability
 * 
 * Detection threshold: confidence > 0.6 with positive growth trend
 */
@Component
@Slf4j
public class MemoryLeakDetector implements IssueDetector {

    // Store memory history per process (PID -> historical data)
    private final Map<Integer, Deque<MemoryDataPoint>> processMemoryHistory = new ConcurrentHashMap<>();

    // Maximum history size per process (10 minutes at 30s intervals = 20 samples)
    private static final int MAX_HISTORY_SIZE = 20;

    // Minimum samples required for reliable detection
    private static final int MIN_SAMPLES_FOR_DETECTION = 6;

    // Fallback high-memory thresholds for immediate pressure detection
    private static final long HIGH_MEMORY_USAGE_BYTES = 256L * 1024 * 1024; // 256 MB
    private static final long CRITICAL_MEMORY_USAGE_BYTES = 512L * 1024 * 1024; // 512 MB
    private static final int MIN_SAMPLES_FOR_HIGH_MEMORY = 3;

    // Memory growth rate threshold (bytes per second) - 1 MB/min = ~17KB/s
    private static final double SIGNIFICANT_GROWTH_RATE = 17_000.0;

    // Minimum R² for confident trend detection
    private static final double MIN_R_SQUARED = 0.7;

    // Assumed sampling interval for trend analysis in seconds.
    // Using a fixed interval avoids slope inflation when detections run in tight
    // loops.
    private static final double ASSUMED_SAMPLE_INTERVAL_SECONDS = 30.0;

    @Override
    public List<DiagnosticIssue> detect(List<MetricSnapshot> metrics, List<ProcessInfo> processes) {
        List<DiagnosticIssue> issues = new ArrayList<>();

        // Update memory history for all processes
        for (ProcessInfo process : processes) {
            updateMemoryHistory(process);
        }

        // Analyze each process for memory leak patterns
        for (ProcessInfo process : processes) {
            try {
                DetectionResult result = analyzeProcess(process, metrics);
                if (result != null && result.meetsThreshold(getConfidenceThreshold())) {
                    issues.add(toIssue(result));
                }
            } catch (Exception e) {
                log.error("Error analyzing process {} (PID: {})", process.getName(), process.getPid(), e);
            }
        }

        // Cleanup old history for processes that no longer exist
        cleanupOldHistory(processes);

        log.debug("MemoryLeakDetector: Analyzed {} processes, found {} issues",
                processes.size(), issues.size());

        return issues;
    }

    /**
     * Update memory history for a process
     */
    private void updateMemoryHistory(ProcessInfo process) {
        int pid = process.getPid();
        long memoryBytes = process.getMemoryBytes();
        Instant timestamp = Instant.now();

        Deque<MemoryDataPoint> history = processMemoryHistory.computeIfAbsent(
                pid, k -> new ArrayDeque<>(MAX_HISTORY_SIZE));

        // Add new data point
        history.addLast(new MemoryDataPoint(timestamp, memoryBytes));

        // Remove oldest if exceeding max size
        while (history.size() > MAX_HISTORY_SIZE) {
            history.removeFirst();
        }
    }

    /**
     * Analyze a single process for memory leak patterns
     */
    private DetectionResult analyzeProcess(ProcessInfo process, List<MetricSnapshot> metrics) {
        int pid = process.getPid();
        Deque<MemoryDataPoint> history = processMemoryHistory.get(pid);

        DetectionResult highMemoryFallback = analyzeHighMemoryUsage(process, history);

        // Need enough history for reliable detection
        if (history == null || history.size() < MIN_SAMPLES_FOR_DETECTION) {
            return highMemoryFallback;
        }

        // Perform linear regression analysis
        LinearRegressionResult regression = performLinearRegression(history);

        // Calculate growth rate (bytes per second)
        double growthRate = regression.slope;
        double rSquared = regression.rSquared;

        // Must have positive growth and good fit
        if (growthRate <= 0 || rSquared < MIN_R_SQUARED) {
            return highMemoryFallback;
        }

        // Calculate confidence based on R² and growth rate
        double confidence = calculateConfidence(growthRate, rSquared, history);

        // Only report if confidence exceeds threshold
        if (confidence < getConfidenceThreshold()) {
            return highMemoryFallback;
        }

        // Determine severity based on growth rate and current memory
        Severity severity = determineSeverity(growthRate, process.getMemoryBytes());

        // Calculate projected memory in 1 hour
        long currentMemory = process.getMemoryBytes();
        long projectedMemory = (long) (currentMemory + (growthRate * 3600));

        // Build detection result with evidence
        return DetectionResult.builder()
                .type(IssueType.MEMORY_LEAK)
                .severity(severity)
                .confidence(confidence)
                .affectedPid(process.getPid())
                .processName(process.getName())
                .description(buildDescription(process, growthRate, confidence, projectedMemory))
                .build()
                .addEvidence("growthRate", Math.round(growthRate)) // bytes/second
                .addEvidence("growthRateMBPerMin", String.format("%.2f MB/min", growthRate * 60 / 1024 / 1024))
                .addEvidence("currentMemory", currentMemory)
                .addEvidence("currentMemoryMB", currentMemory / 1024 / 1024)
                .addEvidence("projectedMemory1h", projectedMemory)
                .addEvidence("projectedMemoryMB1h", projectedMemory / 1024 / 1024)
                .addEvidence("rSquared", String.format("%.3f", rSquared))
                .addEvidence("trend", "increasing")
                .addEvidence("samples", history.size())
                .addEvidence("duration", formatDuration(history));
    }

    /**
     * Fallback detector for high memory pressure even without leak trend.
     */
    private DetectionResult analyzeHighMemoryUsage(ProcessInfo process, Deque<MemoryDataPoint> history) {
        if (history == null || history.size() < MIN_SAMPLES_FOR_HIGH_MEMORY) {
            return null;
        }

        long memoryBytes = process.getMemoryBytes();
        if (memoryBytes < HIGH_MEMORY_USAGE_BYTES) {
            return null;
        }

        boolean critical = memoryBytes >= CRITICAL_MEMORY_USAGE_BYTES;
        Severity severity = critical ? Severity.CRITICAL : Severity.HIGH;
        double confidence = critical ? 0.9 : 0.8;
        long memoryMB = memoryBytes / 1024 / 1024;

        return DetectionResult.builder()
                .type(IssueType.RESOURCE_HOG)
                .severity(severity)
                .confidence(confidence)
                .affectedPid(process.getPid())
                .processName(process.getName())
                .description(String.format(
                        "High memory usage detected in %s (PID: %d): %d MB currently in use.",
                        process.getName(), process.getPid(), memoryMB))
                .build()
                .addEvidence("currentMemory", memoryBytes)
                .addEvidence("currentMemoryMB", memoryMB)
                .addEvidence("thresholdMB", HIGH_MEMORY_USAGE_BYTES / 1024 / 1024)
                .addEvidence("classification", "HIGH_MEMORY_USAGE");
    }

    /**
     * Perform linear regression on memory data points
     */
    private LinearRegressionResult performLinearRegression(Deque<MemoryDataPoint> history) {
        if (history.isEmpty()) {
            return new LinearRegressionResult(0, 0, 0);
        }

        List<MemoryDataPoint> points = new ArrayList<>(history);
        int n = points.size();

        // Calculate sums for regression
        double sumX = 0; // time in seconds
        double sumY = 0; // memory in bytes
        double sumXY = 0;
        double sumX2 = 0;
        double sumY2 = 0;

        for (int i = 0; i < n; i++) {
            MemoryDataPoint point = points.get(i);
            double x = i * ASSUMED_SAMPLE_INTERVAL_SECONDS;
            double y = point.memoryBytes;

            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
            sumY2 += y * y;
        }

        // Calculate slope (growth rate in bytes/second)
        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);

        // Calculate intercept
        double intercept = (sumY - slope * sumX) / n;

        // Calculate R² (coefficient of determination)
        double meanY = sumY / n;
        double ssTotal = 0;
        double ssResidual = 0;

        for (int i = 0; i < n; i++) {
            MemoryDataPoint point = points.get(i);
            double x = i * ASSUMED_SAMPLE_INTERVAL_SECONDS;
            double y = point.memoryBytes;
            double predicted = slope * x + intercept;

            ssTotal += Math.pow(y - meanY, 2);
            ssResidual += Math.pow(y - predicted, 2);
        }

        double rSquared = ssTotal > 0 ? 1 - (ssResidual / ssTotal) : 0;

        return new LinearRegressionResult(slope, intercept, rSquared);
    }

    /**
     * Calculate confidence score based on regression results
     */
    private double calculateConfidence(double growthRate, double rSquared, Deque<MemoryDataPoint> history) {
        // Base confidence from R² (how well data fits linear trend)
        double baseConfidence = rSquared;

        // Boost confidence if growth rate is significant
        double growthFactor = Math.min(growthRate / SIGNIFICANT_GROWTH_RATE, 1.0);

        // Boost confidence with more samples
        double sampleFactor = Math.min(history.size() / (double) MAX_HISTORY_SIZE, 1.0);

        // Combined confidence (weighted average)
        double confidence = (baseConfidence * 0.6) + (growthFactor * 0.3) + (sampleFactor * 0.1);

        // Cap at 0.95 (never 100% certain)
        return Math.min(confidence, 0.95);
    }

    /**
     * Determine severity based on growth rate and current memory
     */
    private Severity determineSeverity(double growthRate, long currentMemory) {
        // Growth rate in MB per minute
        double mbPerMin = growthRate * 60 / 1024 / 1024;

        // Current memory in GB
        double currentGB = currentMemory / 1024.0 / 1024.0 / 1024.0;

        // CRITICAL: Very fast leak (>10 MB/min) or already using >4GB
        if (mbPerMin > 10 || currentGB > 4) {
            return Severity.CRITICAL;
        }

        // HIGH: Fast leak (5-10 MB/min) or high memory (2-4GB)
        if (mbPerMin > 5 || currentGB > 2) {
            return Severity.HIGH;
        }

        // MEDIUM: Moderate leak (1-5 MB/min) or moderate memory (1-2GB)
        if (mbPerMin > 1 || currentGB > 1) {
            return Severity.MEDIUM;
        }

        // LOW: Slow leak (<1 MB/min)
        return Severity.LOW;
    }

    /**
     * Build human-readable description
     */
    private String buildDescription(ProcessInfo process, double growthRate, double confidence, long projectedMemory) {
        double mbPerMin = growthRate * 60 / 1024 / 1024;
        long currentMB = process.getMemoryBytes() / 1024 / 1024;
        long projectedMB = projectedMemory / 1024 / 1024;
        long leakMB = projectedMB - currentMB;

        return String.format(
                "Memory leak detected in %s (PID: %d). Memory is growing at %.2f MB/min " +
                        "(current: %d MB, projected in 1h: %d MB, increase: +%d MB). " +
                        "Detection confidence: %.1f%%.",
                process.getName(), process.getPid(), mbPerMin,
                currentMB, projectedMB, leakMB, confidence * 100);
    }

    /**
     * Convert DetectionResult to DiagnosticIssue
     */
    private DiagnosticIssue toIssue(DetectionResult result) {
        return DiagnosticIssue.builder()
                .type(result.getType())
                .severity(result.getSeverity())
                .confidence(result.getConfidence())
                .affectedPid(result.getAffectedPid())
                .processName(result.getProcessName())
                .details(result.getDescription() != null ? result.getDescription() : result.generateDescription())
                .detectedAt(Instant.now())
                .build();
    }

    /**
     * Cleanup history for processes that no longer exist
     */
    private void cleanupOldHistory(List<ProcessInfo> activeProcesses) {
        Set<Integer> activePids = new HashSet<>();
        for (ProcessInfo process : activeProcesses) {
            activePids.add(process.getPid());
        }

        // Remove history for PIDs not in active list
        processMemoryHistory.keySet().removeIf(pid -> !activePids.contains(pid));
    }

    /**
     * Format duration of monitoring
     */
    private String formatDuration(Deque<MemoryDataPoint> history) {
        if (history.size() < 2) {
            return "0 seconds";
        }

        Instant start = history.getFirst().timestamp;
        Instant end = history.getLast().timestamp;
        long seconds = Duration.between(start, end).getSeconds();

        if (seconds < 60) {
            return seconds + " seconds";
        } else {
            return (seconds / 60) + " minutes";
        }
    }

    /**
     * Data point representing memory usage at a specific time
     */
    private static class MemoryDataPoint {
        final Instant timestamp;
        final long memoryBytes;

        MemoryDataPoint(Instant timestamp, long memoryBytes) {
            this.timestamp = timestamp;
            this.memoryBytes = memoryBytes;
        }
    }

    /**
     * Result of linear regression analysis
     */
    private static class LinearRegressionResult {
        final double slope; // bytes per second
        final double intercept; // bytes at time=0
        final double rSquared; // coefficient of determination (0-1)

        LinearRegressionResult(double slope, double intercept, double rSquared) {
            this.slope = slope;
            this.intercept = intercept;
            this.rSquared = rSquared;
        }
    }
}
