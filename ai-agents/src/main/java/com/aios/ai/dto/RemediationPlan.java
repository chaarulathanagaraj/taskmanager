package com.aios.ai.dto;

import com.aios.shared.enums.ActionType;
import com.aios.shared.enums.SafetyLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Remediation plan created by the RemediationPlannerAgent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemediationPlan {

    /**
     * Primary recommended action
     */
    private ActionType primaryAction;

    /**
     * Target process ID
     */
    private int targetPid;

    /**
     * Target process name
     */
    private String targetProcessName;

    /**
     * Step-by-step execution plan
     */
    private List<RemediationStep> steps;

    /**
     * Expected outcome description
     */
    private String expectedOutcome;

    /**
     * Estimated risk level
     */
    private SafetyLevel riskLevel;

    /**
     * Whether manual approval is required
     */
    private boolean approvalRequired;

    /**
     * Warnings about potential side effects
     */
    private List<String> warnings;

    /**
     * Fallback action if primary fails
     */
    private ActionType fallbackAction;

    /**
     * Estimated execution time in seconds
     */
    private int estimatedExecutionSeconds;

    /**
     * Represents a single step in the remediation plan.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RemediationStep {
        private int order;
        private String description;
        private ActionType action;
        private String parameters;
        private boolean optional;
    }
}
