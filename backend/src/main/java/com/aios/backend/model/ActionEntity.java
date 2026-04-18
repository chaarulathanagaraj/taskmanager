package com.aios.backend.model;

import com.aios.shared.enums.ActionStatus;
import com.aios.shared.enums.ActionType;
import com.aios.shared.enums.SafetyLevel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA Entity for storing remediation action execution records.
 * 
 * <p>Tracks all remediation actions executed by the system,
 * including kills, priority reductions, and working set trims.
 * 
 * <p>Table: actions
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Entity
@Table(name = "actions", indexes = {
    @Index(name = "idx_action_type", columnList = "action_type"),
    @Index(name = "idx_action_status", columnList = "status"),
    @Index(name = "idx_action_executed_at", columnList = "executed_at"),
    @Index(name = "idx_action_target_pid", columnList = "target_pid")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Type of action executed (KILL_PROCESS, REDUCE_PRIORITY, etc.).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 50)
    private ActionType actionType;

    /**
     * Process ID that was targeted by the action.
     */
    @Column(name = "target_pid", nullable = false)
    private Integer targetPid;

    /**
     * Name of the target process.
     */
    @Column(name = "target_name", nullable = false, length = 255)
    private String targetName;

    /**
     * Safety level of the action (LOW, MEDIUM, HIGH, CRITICAL).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "safety_level", nullable = false, length = 20)
    private SafetyLevel safetyLevel;

    /**
     * Execution status (SUCCESS, FAILED, PENDING, SKIPPED).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActionStatus status;

    /**
     * Result message or error details.
     */
    @Column(columnDefinition = "TEXT")
    private String result;

    /**
     * Whether this was a dry-run (simulation mode).
     */
    @Column(name = "dry_run", nullable = false)
    @Builder.Default
    private Boolean dryRun = true;

    /**
     * When the action was executed.
     */
    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    /**
     * The issue that triggered this action (optional).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id")
    private IssueEntity issue;

    /**
     * Who or what initiated the action (e.g., "RemediationEngine", "ManualUser").
     */
    @Column(name = "initiated_by", length = 100)
    private String initiatedBy;

    /**
     * Check if this action was successful.
     * 
     * @return true if status is SUCCESS
     */
    @Transient
    public boolean isSuccessful() {
        return status == ActionStatus.SUCCESS;
    }

    /**
     * Check if this action requires manual review.
     * 
     * @return true if status is FAILED and safety level is HIGH or CRITICAL
     */
    @Transient
    public boolean requiresManualReview() {
        return status == ActionStatus.FAILED 
            && (safetyLevel == SafetyLevel.HIGH || safetyLevel == SafetyLevel.CRITICAL);
    }

    /**
     * Get human-readable action summary.
     * 
     * @return formatted summary string
     */
    @Transient
    public String getSummary() {
        return String.format("%s on %s (PID: %d) - %s%s",
            actionType,
            targetName,
            targetPid,
            status,
            dryRun ? " [DRY RUN]" : ""
        );
    }
}
