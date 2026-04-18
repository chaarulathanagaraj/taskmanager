package com.aios.backend.service;

import com.aios.backend.dto.IssueResolutionSummary;
import com.aios.backend.event.IssueCreatedEvent;
import com.aios.backend.model.IssueEntity;
import com.aios.backend.repository.IssueRepository;
import com.aios.shared.dto.DiagnosticIssue;
import com.aios.shared.enums.IssueType;
import com.aios.shared.enums.IssueStatus;
import com.aios.shared.enums.Severity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Service for managing diagnostic issues.
 * 
 * <p>
 * Handles creation, retrieval, resolution, and querying of issues
 * detected by the agent. Provides methods for filtering by various
 * criteria and identifying issues eligible for auto-remediation.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IssueService {

    private static final Duration DUPLICATE_REFRESH_WINDOW = Duration.ofSeconds(10);
    private static final Duration DUPLICATE_COOLDOWN_WINDOW = Duration.ofMinutes(5);
    private static final Duration PERSISTENCE_ESCALATION_WINDOW = Duration.ofMinutes(10);

    private final IssueRepository issueRepository;
    private final WebSocketBroadcaster broadcaster;
    private final ApplicationEventPublisher eventPublisher;
    private final PerformanceMetrics performanceMetrics;

    /**
     * Create a new issue from agent detection.
     * Broadcasts the issue to WebSocket clients for real-time alerts.
     * Auto-triggers AI diagnosis for low-confidence detections.
     * Prevents duplicate active issues by upserting the same unresolved issue.
     * Refreshes issue state on a 10-second cadence and escalates if issue persists
     * for 10+ minutes.
     * 
     * @param issue diagnostic issue DTO from agent
     * @return created issue entity or existing duplicate
     */
    @Transactional
    public IssueEntity createIssue(DiagnosticIssue issue) {
        log.info("Creating new issue: type={}, severity={}, pid={}, confidence={}",
                issue.getType(), issue.getSeverity(), issue.getAffectedPid(), issue.getConfidence());

        Instant now = Instant.now();
        String fingerprint = buildIssueKey(issue);
        Optional<IssueEntity> existingOpt = issueRepository
                .findFirstByIssueKeyAndResolvedFalseOrderByDetectedAtDesc(fingerprint);

        if (existingOpt.isEmpty()) {
            existingOpt = issueRepository.findFirstByTypeAndAffectedPidAndResolvedFalseOrderByDetectedAtDesc(
                    issue.getType(),
                    issue.getAffectedPid());
        }

        if (existingOpt.isPresent()) {
            IssueEntity existing = existingOpt.get();

            if (existing.getIssueKey() == null || existing.getIssueKey().isBlank()) {
                existing.setIssueKey(fingerprint);
            }

            collapseDuplicateActiveRows(existing, now);

            boolean withinCooldown = existing.getLastUpdatedAt() != null
                    && Duration.between(existing.getLastUpdatedAt(), now).compareTo(DUPLICATE_COOLDOWN_WINDOW) < 0;

            boolean shouldRefresh = existing.getLastSeenAt() == null
                    || Duration.between(existing.getLastSeenAt(), now).compareTo(DUPLICATE_REFRESH_WINDOW) >= 0;
            boolean changed = applyIssueRefresh(existing, issue, now, shouldRefresh, withinCooldown);

            if (shouldEscalatePersistence(existing, now)) {
                existing.setLastPersistenceAlertAt(now);
                if (existing.getSeverity() != Severity.CRITICAL) {
                    existing.setSeverity(escalateSeverity(existing.getSeverity()));
                }
                changed = true;

                if (!withinCooldown) {
                    broadcaster.broadcastAlert(
                            String.format(
                                    "Persistent issue: %s in %s (PID: %d) has lasted over %d minutes",
                                    existing.getType(),
                                    existing.getProcessName(),
                                    existing.getAffectedPid(),
                                    PERSISTENCE_ESCALATION_WINDOW.toMinutes()),
                            existing.getSeverity().name());
                }
            }

            if (changed) {
                issueRepository.save(existing);
            }

            return existing;
        }

        IssueEntity entity = toEntity(issue, now, fingerprint);
        IssueEntity saved = issueRepository.save(entity);

        // Record metrics
        performanceMetrics.recordIssueDetected(saved.getType(), saved.getSeverity());
        performanceMetrics.recordConfidence(saved.getConfidence());
        performanceMetrics.incrementActiveIssues();

        // Broadcast to WebSocket clients
        broadcaster.broadcastNewIssue(saved);

        // Broadcast alert if high priority
        if (saved.isHighPriority()) {
            broadcaster.broadcastAlert(
                    String.format("High priority %s issue detected in process %s (PID: %d)",
                            saved.getType(), saved.getProcessName(), saved.getAffectedPid()),
                    saved.getSeverity().name());
        }

        // Publish event for async processing (auto-diagnosis for low-confidence issues)
        eventPublisher.publishEvent(new IssueCreatedEvent(this, saved));

        return saved;
    }

    /**
     * Get all issues (active and resolved).
     * 
     * @return list of all issues
     */
    @Transactional(readOnly = true)
    public List<IssueEntity> getAllIssues() {
        log.debug("Fetching all issues");
        return issueRepository.findAll();
    }

    /**
     * Get all active (unresolved) issues.
     * 
     * @return list of active issues ordered by severity and time
     */
    @Transactional(readOnly = true)
    public List<IssueEntity> getActiveIssues() {
        log.debug("Fetching active issues");
        return issueRepository.findByResolvedFalseOrderBySeverityDescDetectedAtDesc();
    }

    /**
     * Get high-priority active issues.
     * 
     * @return list of high-priority issues
     */
    @Transactional(readOnly = true)
    public List<IssueEntity> getHighPriorityIssues() {
        log.debug("Fetching high-priority issues");
        return issueRepository.findHighPriorityActiveIssues();
    }

    /**
     * Get issue by ID.
     * 
     * @param id issue ID
     * @return optional issue entity
     */
    @Transactional(readOnly = true)
    public Optional<IssueEntity> getIssueById(Long id) {
        return issueRepository.findById(id);
    }

    /**
     * Get active issues by type.
     * 
     * @param type issue type
     * @return list of matching issues
     */
    @Transactional(readOnly = true)
    public List<IssueEntity> getIssuesByType(IssueType type) {
        log.debug("Fetching issues by type: {}", type);
        return issueRepository.findByTypeAndResolvedFalseOrderByDetectedAtDesc(type);
    }

    /**
     * Get active issues by severity.
     * 
     * @param severity severity level
     * @return list of matching issues
     */
    @Transactional(readOnly = true)
    public List<IssueEntity> getIssuesBySeverity(Severity severity) {
        log.debug("Fetching issues by severity: {}", severity);
        return issueRepository.findBySeverityAndResolvedFalseOrderByDetectedAtDesc(severity);
    }

    /**
     * Get active issues affecting a specific process.
     * 
     * @param pid process ID
     * @return list of matching issues
     */
    @Transactional(readOnly = true)
    public List<IssueEntity> getIssuesByProcess(Integer pid) {
        log.debug("Fetching issues for PID: {}", pid);
        return issueRepository.findByAffectedPidAndResolvedFalseOrderByDetectedAtDesc(pid);
    }

    /**
     * Get issues with confidence above threshold (for auto-remediation).
     * 
     * @param minConfidence minimum confidence threshold (0.0-1.0)
     * @return list of high-confidence unresolved issues
     */
    @Transactional(readOnly = true)
    public List<IssueEntity> getEligibleForRemediation(double minConfidence) {
        log.debug("Fetching issues eligible for remediation (minConfidence={})", minConfidence);
        return issueRepository.findEligibleForAutoRemediation(minConfidence);
    }

    /**
     * Mark an issue as resolved.
     * 
     * @param id issue ID
     * @return true if resolved successfully, false if not found
     */
    @Transactional
    public IssueResolutionSummary resolveIssue(Long id) {
        Optional<IssueEntity> issueOpt = issueRepository.findById(id);

        if (issueOpt.isEmpty()) {
            log.warn("Cannot resolve issue - not found: id={}", id);
            return null;
        }

        IssueEntity issue = issueOpt.get();

        if (Boolean.TRUE.equals(issue.getResolved())) {
            log.warn("Issue already resolved: id={}", id);
            return buildResolutionSummary(issue, "MANUAL", "Issue was already resolved", List.of(
                    "Loaded the issue record",
                    "Confirmed it was already resolved",
                    "Returned the existing resolution state"));
        }

        issue.markResolved();
        issueRepository.save(issue);

        log.info("Resolved issue: id={}, type={}, pid={}",
                id, issue.getType(), issue.getAffectedPid());

        // Broadcast resolution to clients
        IssueResolutionSummary summary = buildResolutionSummary(issue, "MANUAL", "Issue resolved successfully", List.of(
                "Loaded the issue record",
                "Set resolved=true",
                "Updated status to RESOLVED",
                "Set resolvedAt and lastUpdatedAt",
                "Saved the resolved issue",
                "Broadcast the resolution event"));
        broadcaster.broadcastIssueResolved(summary);

        return summary;
    }

    /**
     * Mark an issue as ignored.
     *
     * @param id issue ID
     * @return true if ignored, false if not found
     */
    @Transactional
    public boolean ignoreIssue(Long id) {
        Optional<IssueEntity> issueOpt = issueRepository.findById(id);
        if (issueOpt.isEmpty()) {
            log.warn("Cannot ignore issue - not found: id={}", id);
            return false;
        }

        IssueEntity issue = issueOpt.get();
        if (Boolean.TRUE.equals(issue.getResolved())) {
            return true;
        }

        issue.markIgnored();
        issueRepository.save(issue);

        broadcaster.broadcastIssueResolved(buildResolutionSummary(issue, "IGNORED",
                "Issue ignored and removed from active queue", List.of(
                        "Loaded the issue record",
                        "Marked the issue as ignored",
                        "Updated resolved timestamps",
                        "Saved the ignored issue",
                        "Broadcast the resolution event")));
        return true;
    }

    /**
     * Get count of active issues.
     * 
     * @return number of unresolved issues
     */
    @Transactional(readOnly = true)
    public long getActiveIssueCount() {
        return issueRepository.countByResolvedFalse();
    }

    /**
     * Get count of active issues by severity.
     * 
     * @param severity severity level
     * @return number of matching issues
     */
    @Transactional(readOnly = true)
    public long getIssueCountBySeverity(Severity severity) {
        return issueRepository.countBySeverityAndResolvedFalse(severity);
    }

    /**
     * Get count of active issues by type.
     * 
     * @param type issue type
     * @return number of matching issues
     */
    @Transactional(readOnly = true)
    public long getIssueCountByType(IssueType type) {
        return issueRepository.countByTypeAndResolvedFalse(type);
    }

    /**
     * Delete resolved issues older than specified days.
     * 
     * @param days number of days to keep
     * @return number of deleted records
     */
    @Transactional
    public int deleteOldResolvedIssues(int days) {
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        log.info("Deleting resolved issues older than {} days (cutoff: {})", days, cutoff);

        long deleted = issueRepository.deleteByResolvedTrueAndResolvedAtBefore(cutoff);
        log.info("Deleted {} old resolved issues", deleted);

        return (int) deleted;
    }

    /**
     * Clean up duplicate issues keeping only the most recent one per type/PID.
     * This helps when duplicates have accumulated due to rapid repeated detections.
     * 
     * @param daysToCheck number of days back to check for duplicates (default 7)
     * @return number of duplicate issues deleted
     */
    @Transactional
    public int cleanupDuplicateIssues(int daysToCheck) {
        Instant cutoff = Instant.now().minus(daysToCheck, ChronoUnit.DAYS);
        log.info("Cleaning up duplicate issues from last {} days", daysToCheck);

        List<Long> duplicateIds = issueRepository.findDuplicateIssueIds(cutoff);

        if (duplicateIds.isEmpty()) {
            log.info("No duplicate issues found");
            return 0;
        }

        log.info("Found {} duplicate issues to delete", duplicateIds.size());

        // Delete duplicates in batches to avoid memory issues
        int deleted = 0;
        for (Long id : duplicateIds) {
            issueRepository.deleteById(id);
            deleted++;
        }

        log.info("Deleted {} duplicate issues", deleted);
        broadcaster.broadcastAlert(
                String.format("Cleaned up %d duplicate issues", deleted),
                "INFO");

        return deleted;
    }

    /**
     * Convert DiagnosticIssue DTO to IssueEntity.
     */
    private IssueEntity toEntity(DiagnosticIssue dto, Instant now, String fingerprint) {
        return IssueEntity.builder()
                .issueKey(fingerprint)
                .type(dto.getType())
                .severity(dto.getSeverity())
                .confidence(dto.getConfidence())
                .affectedPid(dto.getAffectedPid())
                .processName(dto.getProcessName())
                .details(dto.getDetails())
                .evidence(buildInitialEvidence(dto, now))
                .detectedAt(now)
                .lastSeenAt(now)
                .lastUpdatedAt(now)
                .occurrenceCount(1)
                .status(IssueStatus.NEW)
                .resolved(false)
                .build();
    }

    private boolean applyIssueRefresh(IssueEntity existing, DiagnosticIssue incoming, Instant now,
            boolean shouldRefresh,
            boolean withinCooldown) {
        boolean changed = false;

        if (incoming.getConfidence() > existing.getConfidence()) {
            existing.setConfidence(incoming.getConfidence());
            changed = true;
        }

        if (incoming.getSeverity().ordinal() > existing.getSeverity().ordinal()) {
            existing.setSeverity(incoming.getSeverity());
            changed = true;
        }

        if (incoming.getDetails() != null && !incoming.getDetails().isBlank()
                && !incoming.getDetails().equals(existing.getDetails())) {
            existing.setDetails(incoming.getDetails());
            changed = true;
        }

        String mergedEvidence = mergeEvidence(existing.getEvidence(), incoming.getDetails(), now);
        if (!mergedEvidence.equals(existing.getEvidence())) {
            existing.setEvidence(mergedEvidence);
            changed = true;
        }

        if (incoming.getProcessName() != null && !incoming.getProcessName().isBlank()
                && !incoming.getProcessName().equals(existing.getProcessName())) {
            existing.setProcessName(incoming.getProcessName());
            changed = true;
        }

        if (shouldRefresh) {
            existing.setLastSeenAt(now);
            existing.setOccurrenceCount(
                    (existing.getOccurrenceCount() == null ? 0 : existing.getOccurrenceCount()) + 1);
            changed = true;
        }

        if (existing.getStatus() == IssueStatus.NEW) {
            existing.setStatus(IssueStatus.ACTIVE);
            changed = true;
        }

        if (changed) {
            existing.setLastUpdatedAt(now);
        } else if (!withinCooldown && shouldRefresh) {
            existing.setLastUpdatedAt(now);
            changed = true;
        }

        return changed;
    }

    private boolean shouldEscalatePersistence(IssueEntity issue, Instant now) {
        if (issue.getDetectedAt() == null) {
            return false;
        }

        Duration persisted = Duration.between(issue.getDetectedAt(), now);
        if (persisted.compareTo(PERSISTENCE_ESCALATION_WINDOW) < 0) {
            return false;
        }

        if (issue.getLastPersistenceAlertAt() == null) {
            return true;
        }

        Duration sinceLastAlert = Duration.between(issue.getLastPersistenceAlertAt(), now);
        return sinceLastAlert.compareTo(PERSISTENCE_ESCALATION_WINDOW) >= 0;
    }

    private Severity escalateSeverity(Severity severity) {
        if (severity == null) {
            return Severity.LOW;
        }

        return switch (severity) {
            case LOW -> Severity.MEDIUM;
            case MEDIUM -> Severity.HIGH;
            case HIGH, CRITICAL -> Severity.CRITICAL;
        };
    }

    private void collapseDuplicateActiveRows(IssueEntity keeper, Instant now) {
        List<IssueEntity> activeForKey = issueRepository.findByTypeAndAffectedPidAndResolvedFalseOrderByDetectedAtDesc(
                keeper.getType(),
                keeper.getAffectedPid());

        if (activeForKey.size() <= 1) {
            return;
        }

        for (IssueEntity issue : activeForKey) {
            if (issue.getId().equals(keeper.getId())) {
                continue;
            }

            issue.setResolved(true);
            issue.setStatus(IssueStatus.RESOLVED);
            issue.setResolvedAt(now);
            issue.setLastUpdatedAt(now);
            issueRepository.save(issue);
        }

        log.info("Collapsed {} duplicate active issue(s) for type={}, pid={}, keeper_id={}",
                activeForKey.size() - 1,
                keeper.getType(),
                keeper.getAffectedPid(),
                keeper.getId());
    }

    private String buildIssueKey(DiagnosticIssue issue) {
        String type = issue.getType() == null ? "unknown" : issue.getType().name().toLowerCase(Locale.ROOT);
        String process = issue.getProcessName() == null
                ? "unknown"
                : issue.getProcessName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return type + "_" + process + "_" + issue.getAffectedPid();
    }

    private String buildInitialEvidence(DiagnosticIssue issue, Instant now) {
        if (issue.getDetails() == null || issue.getDetails().isBlank()) {
            return "";
        }
        return "[" + now + "] " + issue.getDetails().trim();
    }

    private String mergeEvidence(String existingEvidence, String incomingDetails, Instant now) {
        if (incomingDetails == null || incomingDetails.isBlank()) {
            return existingEvidence == null ? "" : existingEvidence;
        }

        String current = existingEvidence == null ? "" : existingEvidence;
        String trimmedIncoming = incomingDetails.trim();

        if (!current.isBlank() && current.contains(trimmedIncoming)) {
            return current;
        }

        List<String> lines = new ArrayList<>();
        if (!current.isBlank()) {
            lines.addAll(List.of(current.split("\\n")));
        }
        lines.add("[" + now + "] " + trimmedIncoming);

        int maxLines = 12;
        if (lines.size() > maxLines) {
            lines = lines.subList(lines.size() - maxLines, lines.size());
        }

        return String.join("\n", lines);
    }

    private IssueResolutionSummary buildResolutionSummary(IssueEntity issue, String source, String message,
            List<String> actionsTaken) {
        return IssueResolutionSummary.builder()
                .issueId(issue.getId())
                .processName(issue.getProcessName())
                .affectedPid(issue.getAffectedPid())
                .issueType(issue.getType())
                .severity(issue.getSeverity())
                .status(issue.getStatus())
                .resolved(issue.getResolved())
                .remediationTaken(issue.getRemediationTaken())
                .source(source)
                .message(message)
                .resolvedAt(issue.getResolvedAt())
                .actionsTaken(actionsTaken)
                .build();
    }
}
