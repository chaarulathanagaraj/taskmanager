package com.aios.shared.dto;

import com.aios.shared.enums.ActionStatus;
import com.aios.shared.enums.ActionType;
import com.aios.shared.enums.SafetyLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for logging remediation action execution to the backend.
 * 
 * <p>
 * Sent by the agent to the backend to record what actions were executed,
 * their results, and any associated metadata. Used for audit trail and
 * history tracking.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemediationActionLog {

    /**
     * Type of action executed.
     */
    private ActionType actionType;

    /**
     * Process ID that was targeted.
     */
    private int targetPid;

    /**
     * Name of the target process.
     */
    private String targetName;

    /**
     * Safety level of the action.
     */
    private SafetyLevel safetyLevel;

    /**
     * Execution status (SUCCESS, FAILED, etc.).
     */
    private ActionStatus status;

    /**
     * Result message or error details.
     */
    private String result;

    /**
     * Whether this was a dry-run execution.
     */
    private boolean dryRun;

    /**
     * Who or what initiated the action.
     */
    private String initiatedBy;

    /**
     * Optional ID of the issue that triggered this action.
     */
    private Long issueId;

    /**
     * When the action was executed.
     */
    @Builder.Default
    private Instant executedAt = Instant.now();

    /**
     * Duration of the action in milliseconds.
     */
    private Long durationMs;

    /**
     * AI confidence score that led to this action (if AI-triggered).
     */
    private Double aiConfidence;

    /**
     * Additional metadata about the action.
     */
    private String metadata;
}
