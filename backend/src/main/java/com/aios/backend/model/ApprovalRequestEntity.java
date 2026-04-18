package com.aios.backend.model;

import com.aios.shared.dto.ExecutionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity for tracking approval workflow.
 */
@Entity
@Table(name = "approval_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", nullable = false)
    private Long executionId;

    @Column(name = "issue_id", nullable = false)
    private Long issueId;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(name = "requested_by")
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejected_by")
    private String rejectedBy;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ExecutionStatus status;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @PrePersist
    protected void onCreate() {
        if (requestedAt == null) {
            requestedAt = Instant.now();
        }
        if (status == null) {
            status = ExecutionStatus.PENDING;
        }
    }
}
