package com.aios.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Complete diagnosis report combining all AI agent outputs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteDiagnosisReport {

    /**
     * Whether the diagnosis was successful
     */
    private boolean success;

    /**
     * Status message
     */
    private String message;

    /**
     * Analysis from specialized agent (LeakDetector, ThreadExpert, IOAnalyst)
     */
    private AiAnalysisResult analysis;

    /**
     * Remediation plan from RemediationPlannerAgent
     */
    private RemediationPlan remediationPlan;

    /**
     * Safety validation from SafetyValidatorAgent
     */
    private SafetyValidation safetyValidation;

    /**
     * Overall confidence in the diagnosis
     */
    private double confidence;

    /**
     * When the diagnosis was completed
     */
    private Instant timestamp;

    /**
     * Total processing time in milliseconds
     */
    private long processingTimeMs;

    /**
     * Issue ID that was diagnosed
     */
    private Long issueId;

    /**
     * Process ID that was analyzed
     */
    private int analyzedPid;

    /**
     * Process name that was analyzed
     */
    private String analyzedProcessName;
}
