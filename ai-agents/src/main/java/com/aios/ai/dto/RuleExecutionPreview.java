package com.aios.ai.dto;

import com.aios.shared.enums.ActionType;
import com.aios.shared.enums.SafetyLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Preview of what a rule execution will do.
 * Shown to user before any automation runs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleExecutionPreview {

    /**
     * Primary action that will be executed
     */
    private ActionType primaryAction;

    /**
     * Human-readable action description
     */
    private String actionDescription;

    /**
     * Target process information
     */
    private TargetInfo target;

    /**
     * Overall risk level
     */
    private SafetyLevel riskLevel;

    /**
     * Confidence in the diagnosis (0.0 - 1.0)
     */
    private double confidence;

    /**
     * Detailed execution steps
     */
    private List<ExecutionStep> steps;

    /**
     * Estimated execution time in seconds
     */
    private int estimatedSeconds;

    /**
     * Warnings about potential side effects
     */
    private List<Warning> warnings;

    /**
     * Expected outcome description
     */
    private String expectedOutcome;

    /**
     * Fallback action if primary fails
     */
    private ActionType fallbackAction;

    /**
     * Fallback action description
     */
    private String fallbackDescription;

    /**
     * Whether manual approval is required
     */
    private boolean approvalRequired;

    /**
     * Whether this action can be rolled back
     */
    private boolean canRollback;

    /**
     * Issue ID this preview is for
     */
    private Long issueId;

    /**
     * Process information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TargetInfo {
        private int pid;
        private String processName;
        private String description;
        private long memoryUsageMB;
        private int threadCount;
        private boolean isCriticalProcess;
    }

    /**
     * Single execution step
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionStep {
        private int order;
        private SafetyLevel safetyLevel;
        private String description;
        private String details;
        private boolean optional;
        private int estimatedSeconds;
    }

    /**
     * Warning about potential issues
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Warning {
        private String severity; // INFO, WARNING, CRITICAL
        private String message;
        private String mitigation;
    }
}
