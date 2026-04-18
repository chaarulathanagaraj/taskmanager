package com.aios.backend.service;

import com.aios.shared.enums.ActionType;
import com.aios.shared.enums.IssueType;
import com.aios.shared.enums.Severity;
import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for recording performance metrics using Micrometer.
 * 
 * <p>
 * Provides methods to record various application metrics including
 * detection latency, remediation success/failure, issue counts,
 * and system health indicators.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Service
@Slf4j
public class PerformanceMetrics {

    private final MeterRegistry registry;

    // Timers
    private Timer detectionLatencyTimer;
    private Timer remediationLatencyTimer;
    private Timer aiDiagnosisLatencyTimer;
    private Timer metricProcessingTimer;

    // Counters
    private Counter remediationSuccessCounter;
    private Counter remediationFailureCounter;
    private Counter issueDetectedCounter;
    private Counter issueResolvedCounter;
    private Counter aiDiagnosisRequestCounter;
    private Counter policyViolationCounter;

    // Gauges - using AtomicLong for gauge values
    private final AtomicLong activeIssueCount = new AtomicLong(0);
    private final AtomicLong pendingActionsCount = new AtomicLong(0);
    private final AtomicLong connectedClientsCount = new AtomicLong(0);

    // Distribution summaries
    private DistributionSummary confidenceDistribution;

    // Per-type counters
    private final Map<String, Counter> issuesByTypeCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> actionsByTypeCounters = new ConcurrentHashMap<>();

    public PerformanceMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    public void initializeMetrics() {
        log.info("Initializing performance metrics");

        // Detection metrics
        detectionLatencyTimer = Timer.builder("aios.detection.latency")
                .description("Time taken to detect issues")
                .tags("component", "detector")
                .register(registry);

        // Remediation metrics
        remediationLatencyTimer = Timer.builder("aios.remediation.latency")
                .description("Time taken to execute remediation actions")
                .tags("component", "remediation")
                .register(registry);

        remediationSuccessCounter = Counter.builder("aios.remediation.total")
                .description("Total remediation actions")
                .tags("result", "success")
                .register(registry);

        remediationFailureCounter = Counter.builder("aios.remediation.total")
                .description("Total remediation actions")
                .tags("result", "failure")
                .register(registry);

        // AI Diagnosis metrics
        aiDiagnosisLatencyTimer = Timer.builder("aios.ai.diagnosis.latency")
                .description("Time taken for AI diagnosis")
                .tags("component", "ai")
                .register(registry);

        aiDiagnosisRequestCounter = Counter.builder("aios.ai.diagnosis.requests")
                .description("Total AI diagnosis requests")
                .register(registry);

        // Issue metrics
        issueDetectedCounter = Counter.builder("aios.issues.total")
                .description("Total issues detected")
                .tags("event", "detected")
                .register(registry);

        issueResolvedCounter = Counter.builder("aios.issues.total")
                .description("Total issues resolved")
                .tags("event", "resolved")
                .register(registry);

        // Metric processing
        metricProcessingTimer = Timer.builder("aios.metrics.processing.latency")
                .description("Time to process incoming metrics")
                .register(registry);

        // Policy violations
        policyViolationCounter = Counter.builder("aios.policy.violations")
                .description("Total policy violations detected")
                .register(registry);

        // Confidence distribution
        confidenceDistribution = DistributionSummary.builder("aios.detection.confidence")
                .description("Distribution of detection confidence scores")
                .baseUnit("percent")
                .scale(100)
                .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
                .register(registry);

        // Gauges
        Gauge.builder("aios.issues.active", activeIssueCount, AtomicLong::get)
                .description("Current number of active issues")
                .register(registry);

        Gauge.builder("aios.actions.pending", pendingActionsCount, AtomicLong::get)
                .description("Current number of pending actions")
                .register(registry);

        Gauge.builder("aios.websocket.clients", connectedClientsCount, AtomicLong::get)
                .description("Number of connected WebSocket clients")
                .register(registry);

        log.info("Performance metrics initialized");
    }

    // ==================== Detection Metrics ====================

    /**
     * Record detection latency.
     */
    public void recordDetectionLatency(Duration duration) {
        detectionLatencyTimer.record(duration);
    }

    /**
     * Record detection latency with issue type.
     */
    public void recordDetectionLatency(Duration duration, IssueType issueType) {
        Timer.builder("aios.detection.latency.by_type")
                .tag("issue_type", issueType.name())
                .register(registry)
                .record(duration);
        detectionLatencyTimer.record(duration);
    }

    /**
     * Record issue detection.
     */
    public void recordIssueDetected(IssueType type, Severity severity) {
        issueDetectedCounter.increment();

        // Per-type counter
        String key = "detected_" + type.name();
        issuesByTypeCounters.computeIfAbsent(key, k -> Counter.builder("aios.issues.by_type")
                .tag("type", type.name())
                .tag("severity", severity.name())
                .register(registry)).increment();
    }

    /**
     * Record issue resolved.
     */
    public void recordIssueResolved() {
        issueResolvedCounter.increment();
    }

    /**
     * Record detection confidence score.
     */
    public void recordConfidence(double confidence) {
        confidenceDistribution.record(confidence);
    }

    // ==================== Remediation Metrics ====================

    /**
     * Record remediation success.
     */
    public void recordRemediationSuccess() {
        remediationSuccessCounter.increment();
    }

    /**
     * Record remediation success with action type.
     */
    public void recordRemediationSuccess(ActionType actionType, Duration duration) {
        remediationSuccessCounter.increment();
        remediationLatencyTimer.record(duration);

        // Per-action counter
        String key = "success_" + actionType.name();
        actionsByTypeCounters.computeIfAbsent(key, k -> Counter.builder("aios.remediation.by_type")
                .tag("action_type", actionType.name())
                .tag("result", "success")
                .register(registry)).increment();
    }

    /**
     * Record remediation failure.
     */
    public void recordRemediationFailure() {
        remediationFailureCounter.increment();
    }

    /**
     * Record remediation failure with action type.
     */
    public void recordRemediationFailure(ActionType actionType, String error) {
        remediationFailureCounter.increment();

        String key = "failure_" + actionType.name();
        actionsByTypeCounters.computeIfAbsent(key, k -> Counter.builder("aios.remediation.by_type")
                .tag("action_type", actionType.name())
                .tag("result", "failure")
                .register(registry)).increment();
    }

    /**
     * Record remediation latency.
     */
    public void recordRemediationLatency(Duration duration) {
        remediationLatencyTimer.record(duration);
    }

    // ==================== AI Diagnosis Metrics ====================

    /**
     * Record AI diagnosis request.
     */
    public void recordAiDiagnosisRequest() {
        aiDiagnosisRequestCounter.increment();
    }

    /**
     * Record AI diagnosis latency.
     */
    public void recordAiDiagnosisLatency(Duration duration) {
        aiDiagnosisLatencyTimer.record(duration);
    }

    /**
     * Record AI diagnosis completion.
     */
    public void recordAiDiagnosis(Duration duration, boolean success) {
        aiDiagnosisLatencyTimer.record(duration);

        Counter.builder("aios.ai.diagnosis.completed")
                .tag("success", String.valueOf(success))
                .register(registry)
                .increment();
    }

    // ==================== Processing Metrics ====================

    /**
     * Record metric processing time.
     */
    public void recordMetricProcessing(Duration duration) {
        metricProcessingTimer.record(duration);
    }

    /**
     * Record policy violation.
     */
    public void recordPolicyViolation(String policyName, String severity) {
        policyViolationCounter.increment();

        Counter.builder("aios.policy.violations.by_policy")
                .tag("policy", policyName)
                .tag("severity", severity)
                .register(registry)
                .increment();
    }

    // ==================== Gauge Updates ====================

    /**
     * Set active issue count.
     */
    public void setActiveIssueCount(long count) {
        activeIssueCount.set(count);
    }

    /**
     * Increment active issue count.
     */
    public void incrementActiveIssues() {
        activeIssueCount.incrementAndGet();
    }

    /**
     * Decrement active issue count.
     */
    public void decrementActiveIssues() {
        activeIssueCount.decrementAndGet();
    }

    /**
     * Set pending actions count.
     */
    public void setPendingActionsCount(long count) {
        pendingActionsCount.set(count);
    }

    /**
     * Set connected clients count.
     */
    public void setConnectedClientsCount(long count) {
        connectedClientsCount.set(count);
    }

    // ==================== Summary Methods ====================

    /**
     * Get current metrics summary.
     */
    public MetricsSummary getSummary() {
        return MetricsSummary.builder()
                .totalDetections(issueDetectedCounter.count())
                .totalResolutions(issueResolvedCounter.count())
                .totalRemediationsSuccess(remediationSuccessCounter.count())
                .totalRemediationsFailure(remediationFailureCounter.count())
                .activeIssues(activeIssueCount.get())
                .aiDiagnosisRequests(aiDiagnosisRequestCounter.count())
                .policyViolations(policyViolationCounter.count())
                .avgDetectionLatencyMs(detectionLatencyTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS))
                .avgRemediationLatencyMs(remediationLatencyTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS))
                .build();
    }

    /**
     * Summary DTO for metrics.
     */
    @lombok.Data
    @lombok.Builder
    public static class MetricsSummary {
        private double totalDetections;
        private double totalResolutions;
        private double totalRemediationsSuccess;
        private double totalRemediationsFailure;
        private long activeIssues;
        private double aiDiagnosisRequests;
        private double policyViolations;
        private double avgDetectionLatencyMs;
        private double avgRemediationLatencyMs;
    }
}
