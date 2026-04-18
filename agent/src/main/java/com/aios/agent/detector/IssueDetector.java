package com.aios.agent.detector;

import com.aios.shared.dto.DiagnosticIssue;
import com.aios.shared.dto.MetricSnapshot;
import com.aios.shared.dto.ProcessInfo;

import java.util.List;

/**
 * Interface for issue detection implementations.
 * 
 * Each detector analyzes system metrics and process information
 * to identify specific types of problems (memory leaks, thread explosions, etc.).
 * 
 * Implementations should:
 * - Analyze recent metrics and process data
 * - Calculate confidence scores (0.0-1.0)
 * - Return issues only when confidence exceeds threshold
 * - Provide detailed evidence in issue descriptions
 */
public interface IssueDetector {

    /**
     * Detect issues based on system metrics and process information.
     * 
     * @param metrics Recent system metrics history (typically last 1-60 minutes)
     * @param processes List of processes to analyze (typically top 50 by CPU/memory)
     * @return List of detected issues with confidence scores and evidence
     */
    List<DiagnosticIssue> detect(List<MetricSnapshot> metrics, List<ProcessInfo> processes);

    /**
     * Get the name of this detector (for logging and debugging).
     * @return Detector name (e.g., "MemoryLeakDetector")
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * Get the minimum confidence threshold for reporting issues.
     * Issues below this threshold are filtered out.
     * @return Confidence threshold (0.0-1.0), default 0.6
     */
    default double getConfidenceThreshold() {
        return 0.6;
    }

    /**
     * Check if this detector is enabled.
     * Can be overridden to support runtime enabling/disabling.
     * @return true if detector should run
     */
    default boolean isEnabled() {
        return true;
    }
}
