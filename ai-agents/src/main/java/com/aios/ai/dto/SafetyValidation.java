package com.aios.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Safety validation result from SafetyValidatorAgent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SafetyValidation {

    /**
     * Whether the remediation plan is considered safe
     */
    private boolean safe;

    /**
     * Whether approval is required before execution
     */
    private boolean approvalRequired;

    /**
     * List of warnings about potential risks
     */
    private List<String> warnings;

    /**
     * List of safety violations found
     */
    private List<SafetyViolation> violations;

    /**
     * Recommended modifications to the plan
     */
    private List<String> recommendations;

    /**
     * Overall safety score (0.0 - 1.0)
     */
    private double safetyScore;

    /**
     * Explanation of the safety assessment
     */
    private String explanation;

    /**
     * Represents a safety violation.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SafetyViolation {
        private String rule;
        private String description;
        private String severity;
        private boolean blocking;
    }
}
