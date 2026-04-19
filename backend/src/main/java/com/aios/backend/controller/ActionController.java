package com.aios.backend.controller;

import com.aios.backend.model.ActionEntity;
import com.aios.backend.service.ActionService;
import com.aios.backend.service.SettingsService;
import com.aios.shared.dto.RemediationActionLog;
import com.aios.shared.enums.ActionStatus;
import com.aios.shared.enums.ActionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for remediation action endpoints.
 * 
 * <p>
 * Provides APIs for:
 * <ul>
 * <li>Logging action executions from the agent</li>
 * <li>Retrieving action history</li>
 * <li>Querying actions by type, status, or process</li>
 * <li>Getting action statistics and success rates</li>
 * </ul>
 * 
 * @author AIOS Team
 * @since 1.0
 */
import com.aios.backend.dto.ManualExecuteRequest;
import com.aios.backend.service.ActionExecutorService;
import java.util.Map;

@RestController
@RequestMapping("/api/actions")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class ActionController {

    private final ActionService actionService;
    private final ActionExecutorService actionExecutorService;
    private final SettingsService settingsService;

    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> executeManualAction(@RequestBody ManualExecuteRequest request) {
        log.info("Manual execution requested: {}", request.getActionType());
        // A minimal wrapper for manual UI executions without an issue context
        // NOTE: we pass -1 for issueId since this is manual. The executor only requires
        // PID
        try {
            // Manual action endpoint should respect explicit operator intent.
            boolean effectiveDryRun = request.isDryRun();

            Map<String, Object> result = actionExecutorService.executePID(
                    request.getActionType(),
                    request.getTargetPid(),
                    "Unknown",
                    effectiveDryRun,
                    request.getTargetPriority());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Log a remediation action execution from the agent.
     * 
     * @param actionLog the action execution log
     * @return saved action entity with generated ID
     */
    @PostMapping
    public ResponseEntity<ActionEntity> logAction(@RequestBody RemediationActionLog actionLog) {
        log.info("Received action log: type={}, status={}, pid={}, dryRun={}",
                actionLog.getActionType(), actionLog.getStatus(), actionLog.getTargetPid(), actionLog.isDryRun());
        ActionEntity saved = actionService.logAction(actionLog);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Get action history for a specified time period.
     * Default: last 24 hours.
     * 
     * @param hours number of hours to look back (default: 24)
     * @return list of actions executed in the time period
     */
    @GetMapping("/history")
    public ResponseEntity<List<ActionEntity>> getHistory(
            @RequestParam(defaultValue = "24") int hours) {
        log.debug("Fetching action history for last {} hours", hours);
        List<ActionEntity> actions = actionService.getRecentActions(hours);
        return ResponseEntity.ok(actions);
    }

    /**
     * Get all actions (complete history).
     * 
     * @return list of all action records
     */
    @GetMapping
    public ResponseEntity<List<ActionEntity>> getAllActions() {
        log.debug("Fetching all actions");
        List<ActionEntity> actions = actionService.getAllActions();
        return ResponseEntity.ok(actions);
    }

    /**
     * Get action by ID.
     * 
     * @param id the action ID
     * @return action entity or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<ActionEntity> getActionById(@PathVariable Long id) {
        log.debug("Fetching action with id={}", id);
        return actionService.getActionById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get actions by type.
     * 
     * @param type the action type to filter by
     * @return list of actions of the specified type
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<List<ActionEntity>> getActionsByType(@PathVariable ActionType type) {
        log.debug("Fetching actions of type={}", type);
        List<ActionEntity> actions = actionService.getActionsByType(type);
        return ResponseEntity.ok(actions);
    }

    /**
     * Get actions by status.
     * 
     * @param status the action status to filter by
     * @return list of actions with the specified status
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<ActionEntity>> getActionsByStatus(@PathVariable ActionStatus status) {
        log.debug("Fetching actions with status={}", status);
        List<ActionEntity> actions = actionService.getActionsByStatus(status);
        return ResponseEntity.ok(actions);
    }

    /**
     * Get actions for a specific process.
     * 
     * @param pid process ID
     * @return list of actions targeting the process
     */
    @GetMapping("/process/{pid}")
    public ResponseEntity<List<ActionEntity>> getActionsByProcess(@PathVariable Integer pid) {
        log.debug("Fetching actions for process pid={}", pid);
        List<ActionEntity> actions = actionService.getActionsByProcess(pid);
        return ResponseEntity.ok(actions);
    }

    /**
     * Get actions related to a specific issue.
     * 
     * @param issueId the issue ID
     * @return list of actions executed for the issue
     */
    @GetMapping("/issue/{issueId}")
    public ResponseEntity<List<ActionEntity>> getActionsByIssue(@PathVariable Long issueId) {
        log.debug("Fetching actions for issue id={}", issueId);
        List<ActionEntity> actions = actionService.getActionsByIssue(issueId);
        return ResponseEntity.ok(actions);
    }

    /**
     * Get non-dry-run (actual) actions.
     * 
     * @return list of real executed actions
     */
    @GetMapping("/real")
    public ResponseEntity<List<ActionEntity>> getRealActions() {
        log.debug("Fetching non-dry-run actions");
        List<ActionEntity> actions = actionService.getRealActions();
        return ResponseEntity.ok(actions);
    }

    /**
     * Get actions that require manual review (failed high-risk actions).
     * 
     * @return list of failed actions requiring attention
     */
    @GetMapping("/review-required")
    public ResponseEntity<List<ActionEntity>> getActionsRequiringReview() {
        log.debug("Fetching actions requiring manual review");
        List<ActionEntity> actions = actionService.getActionsRequiringReview();
        return ResponseEntity.ok(actions);
    }

    /**
     * Get success rate for a specific action type.
     * 
     * @param type  the action type
     * @param hours time period in hours (default: 24)
     * @return success rate as percentage (0-100)
     */
    @GetMapping("/success-rate/{type}")
    public ResponseEntity<Double> getSuccessRate(
            @PathVariable ActionType type,
            @RequestParam(defaultValue = "24") int hours) {
        log.debug("Calculating success rate for type={} over last {} hours", type, hours);
        Double successRate = actionService.getSuccessRate(type, hours);
        return ResponseEntity.ok(successRate);
    }

    /**
     * Get action statistics.
     * 
     * @param hours time period in hours (default: 24)
     * @return statistics object with counts and rates
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getActionStatistics(
            @RequestParam(defaultValue = "24") Integer hours) {
        log.debug("Fetching action statistics for last {} hours", hours);
        Map<String, Object> stats = actionService.getStatistics(hours);
        return ResponseEntity.ok(stats);
    }

    /**
     * Delete old dry-run actions (cleanup endpoint).
     * 
     * @param olderThanDays delete dry-run actions older than this many days
     * @return number of deleted records
     */
    @DeleteMapping("/cleanup")
    public ResponseEntity<Integer> cleanupDryRunActions(
            @RequestParam(defaultValue = "7") int olderThanDays) {
        log.info("Cleaning up dry-run actions older than {} days", olderThanDays);
        int deleted = actionService.deleteOldDryRunActions(olderThanDays);
        return ResponseEntity.ok(deleted);
    }
}
