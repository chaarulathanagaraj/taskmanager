package com.aios.backend.service;

import com.aios.backend.model.ActionEntity;
import com.aios.backend.model.IssueEntity;
import com.aios.backend.repository.ActionRepository;
import com.aios.backend.repository.IssueRepository;
import com.aios.shared.dto.RemediationActionLog;
import com.aios.shared.enums.ActionStatus;
import com.aios.shared.enums.ActionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing remediation action logs.
 * 
 * <p>
 * Handles logging, retrieval, and analysis of remediation actions
 * executed by the agent. Provides methods for querying action history,
 * calculating success rates, and identifying actions requiring review.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActionService {

    private final ActionRepository actionRepository;
    private final IssueRepository issueRepository;
    private final WebSocketBroadcaster broadcaster;
    private final PerformanceMetrics performanceMetrics;

    /**
     * Log a remediation action executed by the agent.
     * Broadcasts the action to WebSocket clients for real-time tracking.
     * 
     * @param actionLog remediation action log DTO from agent
     * @return created action entity
     */
    @Transactional
    public ActionEntity logAction(RemediationActionLog actionLog) {
        log.info("Logging action: type={}, status={}, pid={}, dryRun={}",
                actionLog.getActionType(), actionLog.getStatus(),
                actionLog.getTargetPid(), actionLog.isDryRun());

        ActionEntity entity = toEntity(actionLog);

        // Link to issue if provided
        if (actionLog.getIssueId() != null) {
            Optional<IssueEntity> issue = issueRepository.findById(actionLog.getIssueId());
            issue.ifPresent(entity::setIssue);
        }

        ActionEntity saved = actionRepository.save(entity);

        // Record metrics
        if (saved.getStatus() == ActionStatus.SUCCESS) {
            performanceMetrics.recordRemediationSuccess();
        } else if (saved.getStatus() == ActionStatus.FAILED) {
            performanceMetrics.recordRemediationFailure();
        }

        // Broadcast to WebSocket clients
        broadcaster.broadcastAction(saved);

        // Broadcast alert if action requires manual review
        if (saved.requiresManualReview()) {
            broadcaster.broadcastAlert(
                    String.format("High-risk action %s FAILED on PID %d - manual review required",
                            saved.getActionType(), saved.getTargetPid()),
                    "CRITICAL");
        }

        return saved;
    }

    /**
     * Get action history from the last N hours.
     * 
     * @param hours number of hours to look back
     * @return list of actions within the time window
     */
    @Transactional(readOnly = true)
    public List<ActionEntity> getRecentActions(int hours) {
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
        log.debug("Fetching actions since {}", since);
        return actionRepository.findByExecutedAtAfterOrderByExecutedAtDesc(since);
    }

    /**
     * Get all actions.
     * 
     * @return list of all actions
     */
    @Transactional(readOnly = true)
    public List<ActionEntity> getAllActions() {
        log.debug("Fetching all actions");
        return actionRepository.findAll();
    }

    /**
     * Get action by ID.
     * 
     * @param id action ID
     * @return optional action entity
     */
    @Transactional(readOnly = true)
    public Optional<ActionEntity> getActionById(Long id) {
        return actionRepository.findById(id);
    }

    /**
     * Get actions by type.
     * 
     * @param type action type
     * @return list of matching actions
     */
    @Transactional(readOnly = true)
    public List<ActionEntity> getActionsByType(ActionType type) {
        log.debug("Fetching actions by type: {}", type);
        return actionRepository.findByActionTypeOrderByExecutedAtDesc(type);
    }

    /**
     * Get actions by status.
     * 
     * @param status action status
     * @return list of matching actions
     */
    @Transactional(readOnly = true)
    public List<ActionEntity> getActionsByStatus(ActionStatus status) {
        log.debug("Fetching actions by status: {}", status);
        return actionRepository.findByStatusOrderByExecutedAtDesc(status);
    }

    /**
     * Get actions for a specific process.
     * 
     * @param pid process ID
     * @return list of matching actions
     */
    @Transactional(readOnly = true)
    public List<ActionEntity> getActionsByProcess(Integer pid) {
        log.debug("Fetching actions for PID: {}", pid);
        return actionRepository.findByTargetPidOrderByExecutedAtDesc(pid);
    }

    /**
     * Get actions for a specific issue.
     * 
     * @param issueId issue ID
     * @return list of actions taken for the issue
     */
    @Transactional(readOnly = true)
    public List<ActionEntity> getActionsByIssue(Long issueId) {
        log.debug("Fetching actions for issue ID: {}", issueId);
        return actionRepository.findByIssueId(issueId);
    }

    /**
     * Get real (non-dry-run) actions only.
     * 
     * @return list of real actions
     */
    @Transactional(readOnly = true)
    public List<ActionEntity> getRealActions() {
        log.debug("Fetching real (non-dry-run) actions");
        return actionRepository.findByDryRunFalseOrderByExecutedAtDesc();
    }

    /**
     * Get actions requiring manual review (failed high-risk actions).
     * 
     * @return list of actions needing review
     */
    @Transactional(readOnly = true)
    public List<ActionEntity> getActionsRequiringReview() {
        log.debug("Fetching actions requiring manual review");
        return actionRepository.findActionsRequiringManualReview();
    }

    /**
     * Calculate success rate for a specific action type.
     * 
     * @param type  action type
     * @param hours number of hours to look back
     * @return success rate as percentage (0.0-100.0)
     */
    @Transactional(readOnly = true)
    public double getSuccessRate(ActionType type, int hours) {
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
        log.debug("Calculating success rate for {} since {}", type, since);

        Double rate = actionRepository.getSuccessRateByActionType(type, since);
        return rate != null ? rate : 0.0;
    }

    /**
     * Get aggregated action statistics.
     * 
     * @param hours number of hours to look back (null for all time)
     * @return map of statistic names to values
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStatistics(Integer hours) {
        Instant since = hours != null
                ? Instant.now().minus(hours, ChronoUnit.HOURS)
                : Instant.EPOCH;

        log.debug("Calculating action statistics since {}", since);

        Instant end = Instant.now();
        List<ActionEntity> actions = hours != null
                ? actionRepository.findByExecutedAtAfterOrderByExecutedAtDesc(since)
                : actionRepository.findAll();

        long totalActions = actions.size();
        long successfulActions = actions.stream()
                .filter(ActionEntity::isSuccessful)
                .count();
        long failedActions = actions.stream()
                .filter(a -> a.getStatus() == ActionStatus.FAILED)
                .count();
        long realActions = actionRepository.countByDryRunFalseAndExecutedAtBetween(since, end);

        double overallSuccessRate = totalActions > 0
                ? (successfulActions * 100.0 / totalActions)
                : 0.0;

        return Map.of(
                "totalActions", totalActions,
                "successfulActions", successfulActions,
                "failedActions", failedActions,
                "realActions", realActions,
                "overallSuccessRate", overallSuccessRate,
                "timeWindow", hours != null ? hours + " hours" : "all time");
    }

    /**
     * Delete old dry-run actions.
     * 
     * @param days number of days to keep
     * @return number of deleted records
     */
    @Transactional
    public int deleteOldDryRunActions(int days) {
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        log.info("Deleting dry-run actions older than {} days (cutoff: {})", days, cutoff);

        long deleted = actionRepository.deleteByDryRunTrueAndExecutedAtBefore(cutoff);
        log.info("Deleted {} old dry-run action records", deleted);

        return (int) deleted;
    }

    /**
     * Convert RemediationActionLog DTO to ActionEntity.
     */
    private ActionEntity toEntity(RemediationActionLog dto) {
        return ActionEntity.builder()
                .actionType(dto.getActionType())
                .targetPid(dto.getTargetPid())
                .targetName(dto.getTargetName())
                .safetyLevel(dto.getSafetyLevel())
                .status(dto.getStatus())
                .result(dto.getResult())
                .dryRun(dto.isDryRun())
                .executedAt(Instant.now())
                .initiatedBy(dto.getInitiatedBy())
                .build();
    }
}
