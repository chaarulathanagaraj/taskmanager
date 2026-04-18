package com.aios.agent.detector;

import com.aios.shared.enums.IssueType;
import com.aios.shared.enums.Severity;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Result of an issue detection analysis.
 * 
 * Contains all information needed to create a DiagnosticIssue,
 * plus additional evidence and metadata used during analysis.
 * 
 * This is an intermediate representation used by detectors
 * before converting to the final DiagnosticIssue DTO.
 */
@Data
@Builder
public class DetectionResult {

    /**
     * Type of issue detected (MEMORY_LEAK, THREAD_EXPLOSION, etc.)
     */
    private IssueType type;

    /**
     * Severity level (LOW, MEDIUM, HIGH, CRITICAL)
     */
    private Severity severity;

    /**
     * Confidence score (0.0-1.0) representing how certain the detector is
     * that this is a real issue vs. normal behavior.
     * 
     * Typical ranges:
     * - 0.6-0.7: Possible issue, needs monitoring
     * - 0.7-0.85: Probable issue, consider action
     * - 0.85-1.0: Definite issue, take action
     */
    private double confidence;

    /**
     * Process ID affected by this issue
     */
    private int affectedPid;

    /**
     * Name of the affected process
     */
    private String processName;

    /**
     * Detailed evidence supporting the detection.
     * 
     * Common keys:
     * - "growthRate": Rate of resource growth (MB/min, threads/min, etc.)
     * - "currentValue": Current metric value
     * - "threshold": Threshold that was exceeded
     * - "trend": "increasing", "decreasing", "stable"
     * - "duration": How long the issue has been observed
     * - "samples": Number of data points analyzed
     * - "pattern": Description of detected pattern
     */
    @Builder.Default
    private Map<String, Object> evidence = new HashMap<>();

    /**
     * Human-readable description of the issue and evidence
     */
    private String description;

    /**
     * Add evidence to the detection result
     * @param key Evidence key
     * @param value Evidence value
     * @return this (for fluent API)
     */
    public DetectionResult addEvidence(String key, Object value) {
        this.evidence.put(key, value);
        return this;
    }

    /**
     * Get evidence value by key
     * @param key Evidence key
     * @return Evidence value or null if not found
     */
    public Object getEvidence(String key) {
        return evidence.get(key);
    }

    /**
     * Get evidence value as double
     * @param key Evidence key
     * @param defaultValue Default value if not found or not a number
     * @return Evidence value as double
     */
    public double getEvidenceAsDouble(String key, double defaultValue) {
        Object value = evidence.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    /**
     * Get evidence value as string
     * @param key Evidence key
     * @param defaultValue Default value if not found
     * @return Evidence value as string
     */
    public String getEvidenceAsString(String key, String defaultValue) {
        Object value = evidence.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * Check if this result has sufficient confidence to report
     * @param threshold Minimum confidence threshold
     * @return true if confidence >= threshold
     */
    public boolean meetsThreshold(double threshold) {
        return confidence >= threshold;
    }

    /**
     * Generate a formatted description from evidence
     * @return Description string with key evidence points
     */
    public String generateDescription() {
        if (description != null && !description.isEmpty()) {
            return description;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Detected %s in process %s (PID: %d). ",
            type, processName, affectedPid));
        sb.append(String.format("Confidence: %.1f%%. ", confidence * 100));

        // Add key evidence
        evidence.forEach((key, value) -> {
            sb.append(String.format("%s: %s. ", key, value));
        });

        return sb.toString();
    }
}
