package com.aios.backend.model;

import com.aios.shared.enums.IssueType;
import com.aios.shared.enums.IssueStatus;
import com.aios.shared.enums.Severity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.Duration;

/**
 * JPA Entity for storing diagnostic issues detected by the agent.
 * 
 * <p>
 * Represents problems such as memory leaks, thread explosions,
 * hung processes, I/O bottlenecks, and resource hogs.
 * 
 * <p>
 * Table: issues
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Entity
@Table(name = "issues", indexes = {
        @Index(name = "idx_issue_key", columnList = "issue_key"),
        @Index(name = "idx_issue_type", columnList = "type"),
        @Index(name = "idx_issue_status", columnList = "status"),
        @Index(name = "idx_issue_severity", columnList = "severity"),
        @Index(name = "idx_issue_resolved", columnList = "resolved"),
        @Index(name = "idx_issue_detected_at", columnList = "detected_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Stable fingerprint key used for deduplication of active issues.
     */
    @Column(name = "issue_key", nullable = false, length = 300)
    private String issueKey;

    /**
     * Type of issue (MEMORY_LEAK, THREAD_EXPLOSION, etc.).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private IssueType type;

    /**
     * Severity level (LOW, MEDIUM, HIGH, CRITICAL).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity;

    /**
     * Confidence score of the detection (0.0 - 1.0).
     */
    @Column(nullable = false)
    private Double confidence;

    /**
     * Process ID affected by this issue.
     */
    @Column(name = "affected_pid", nullable = false)
    private Integer affectedPid;

    /**
     * Name of the affected process.
     */
    @Column(name = "process_name", nullable = false, length = 255)
    private String processName;

    /**
     * Detailed description and evidence of the issue.
     */
    @Column(columnDefinition = "TEXT")
    private String details;

    /**
     * Aggregated evidence across detections for this same issue key.
     */
    @Column(columnDefinition = "TEXT")
    private String evidence;

    /**
     * When the issue was first detected.
     */
    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    /**
     * Most recent time this issue was re-observed by detectors.
     */
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    /**
     * Last state mutation/update timestamp.
     */
    @Column(name = "last_updated_at", nullable = false)
    private Instant lastUpdatedAt;

    /**
     * Last time a persistent-issue escalation alert was emitted.
     */
    @Column(name = "last_persistence_alert_at")
    private Instant lastPersistenceAlertAt;

    /**
     * Number of times this issue has been observed while unresolved.
     */
    @Column(name = "occurrence_count", nullable = false)
    @Builder.Default
    private Integer occurrenceCount = 1;

    /**
     * Issue lifecycle state.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private IssueStatus status = IssueStatus.NEW;

    /**
     * Whether remediation has been executed for this issue in current cycle.
     */
    @Column(name = "remediation_taken", nullable = false)
    @Builder.Default
    private Boolean remediationTaken = false;

    /**
     * Last time remediation was executed for this issue.
     */
    @Column(name = "last_remediation_at")
    private Instant lastRemediationAt;

    /**
     * When the issue was resolved (null if still active).
     */
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /**
     * Whether the issue has been resolved.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean resolved = false;

    /**
     * Check if this is a high priority issue requiring immediate attention.
     * 
     * @return true if severity is HIGH or CRITICAL and confidence > 0.7
     */
    @Transient
    public boolean isHighPriority() {
        return (severity == Severity.HIGH || severity == Severity.CRITICAL)
                && confidence > 0.7;
    }

    /**
     * Check if this issue is eligible for auto-remediation.
     * 
     * @param confidenceThreshold minimum confidence required
     * @return true if confidence meets threshold and not resolved
     */
    @Transient
    public boolean isEligibleForAutoRemediation(double confidenceThreshold) {
        return !resolved && confidence >= confidenceThreshold;
    }

    /**
     * Mark this issue as resolved.
     */
    public void markResolved() {
        this.resolved = true;
        this.status = IssueStatus.RESOLVED;
        this.resolvedAt = Instant.now();
        this.lastUpdatedAt = this.resolvedAt;
    }

    /**
     * Mark this issue as ignored.
     */
    public void markIgnored() {
        this.resolved = true;
        this.status = IssueStatus.IGNORED;
        this.resolvedAt = Instant.now();
        this.lastUpdatedAt = this.resolvedAt;
    }

    /**
     * A higher stability score means issue persistence across cycles.
     */
    @Transient
    public Integer getStabilityScore() {
        Instant now = Instant.now();
        Duration age = detectedAt == null ? Duration.ZERO : Duration.between(detectedAt, now);
        double ageFactor = Math.min(1.0, age.toMinutes() / 30.0);
        double countFactor = Math.min(1.0, (occurrenceCount == null ? 0 : occurrenceCount) / 10.0);
        double confidenceFactor = confidence == null ? 0.0 : Math.max(0.0, Math.min(1.0, confidence));

        double score = (ageFactor * 0.5) + (countFactor * 0.3) + (confidenceFactor * 0.2);
        return (int) Math.round(score * 100);
    }
}
