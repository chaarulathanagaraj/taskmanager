package com.aios.backend.model;

import com.aios.shared.dto.ExecutionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity for tracking rule action executions.
 */
@Entity
@Table(name = "rule_executions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_id", nullable = false)
    private Long issueId;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(name = "action_params", columnDefinition = "TEXT")
    private String actionParams;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ExecutionStatus status;

    @Column(name = "dry_run")
    private boolean dryRun;

    @Column(name = "success")
    private Boolean success;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "execution_details", columnDefinition = "TEXT")
    private String executionDetails; // JSON

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "executed_by")
    private String executedBy;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "rollback_info", columnDefinition = "TEXT")
    private String rollbackInfo; // JSON

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (status == null) {
            status = ExecutionStatus.PENDING;
        }
    }
}
