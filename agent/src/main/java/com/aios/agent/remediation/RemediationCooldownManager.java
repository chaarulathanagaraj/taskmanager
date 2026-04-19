package com.aios.agent.remediation;

import com.aios.shared.enums.ActionType;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages remediation action cooldowns to prevent rapid-fire repeated actions.
 * 
 * <p>
 * Tracks the last execution time of each action per process and enforces
 * cooldown periods to prevent resource exhaustion and rapid-fire retries.
 * 
 * <p>
 * Uses customized messages from RemediationMessageFormatter to provide
 * detailed, process-specific feedback when actions are blocked due to cooldown.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RemediationCooldownManager {

    /**
     * Track action execution history: key = "{processName}:{actionType}", value =
     * execution timestamp
     */
    private final Map<String, ActionExecutionRecord> actionLastExecution = new ConcurrentHashMap<>();

    /**
     * Cooldown duration in milliseconds (default 10 minutes).
     */
    private static final long DEFAULT_COOLDOWN_MS = 10 * 60 * 1000; // 10 minutes

    /**
     * Process-specific cooldown overrides (if needed).
     */
    private final Map<String, Long> processCooldownOverrides = new ConcurrentHashMap<>();

    /**
     * Check if a remediation action is in cooldown.
     * 
     * @param processName Name of the process
     * @param pid         Process ID
     * @param actionType  The action type to check
     * @return ActionResult if in cooldown, null otherwise
     */
    public ActionResult checkCooldown(String processName, int pid, ActionType actionType) {
        String key = buildKey(processName, actionType);
        ActionExecutionRecord lastExecution = actionLastExecution.get(key);

        if (lastExecution == null) {
            return null; // No previous execution, no cooldown
        }

        long cooldownMs = getCooldownDuration(processName);
        long elapsedMs = System.currentTimeMillis() - lastExecution.getLastExecutionTime();

        if (elapsedMs < cooldownMs) {
            // Still in cooldown
            int cooldownMinutes = (int) ((cooldownMs / 1000) / 60);
            return ActionResult.cooldownLocked(processName, pid, actionType, cooldownMinutes)
                    .addDetail("elapsedMs", elapsedMs)
                    .addDetail("cooldownMs", cooldownMs);
        }

        return null; // Cooldown expired, action can proceed
    }

    /**
     * Record an action execution for cooldown tracking.
     * 
     * @param processName Name of the process
     * @param pid         Process ID
     * @param actionType  The action type that was executed
     * @param success     Whether the action was successful
     */
    public void recordExecution(String processName, int pid, ActionType actionType, boolean success) {
        String key = buildKey(processName, actionType);
        ActionExecutionRecord record = ActionExecutionRecord.builder()
                .processName(processName)
                .targetPid(pid)
                .actionName(actionType.name())
                .lastExecutionTime(System.currentTimeMillis())
                .executionCount(1)
                .build();

        actionLastExecution.put(key, record);
        log.debug("Recorded execution: process={}, action={}, success={}", processName, actionType, success);
    }

    /**
     * Get the cooldown duration for a specific process.
     * Can be overridden per-process if needed.
     * 
     * @param processName Name of the process
     * @return Cooldown duration in milliseconds
     */
    public long getCooldownDuration(String processName) {
        return processCooldownOverrides.getOrDefault(processName, DEFAULT_COOLDOWN_MS);
    }

    /**
     * Set a custom cooldown duration for a specific process.
     * 
     * @param processName Name of the process
     * @param cooldownMs  Cooldown duration in milliseconds
     */
    public void setProcessCooldown(String processName, long cooldownMs) {
        processCooldownOverrides.put(processName, cooldownMs);
        log.info("Set custom cooldown for {}: {} minutes", processName, cooldownMs / (60 * 1000));
    }

    /**
     * Reset cooldown for a specific process/action combination.
     * 
     * @param processName Name of the process
     * @param actionType  The action type to reset
     */
    public void resetCooldown(String processName, ActionType actionType) {
        String key = buildKey(processName, actionType);
        actionLastExecution.remove(key);
        log.info("Reset cooldown for {}/{}", processName, actionType);
    }

    /**
     * Clear all cooldowns.
     */
    public void clearAllCooldowns() {
        actionLastExecution.clear();
        log.info("Cleared all remediation cooldowns");
    }

    /**
     * Get execution history for a process.
     * 
     * @param processName Name of the process
     * @return Map of action types to last execution records
     */
    public Map<ActionType, ActionExecutionRecord> getProcessExecutionHistory(String processName) {
        Map<ActionType, ActionExecutionRecord> history = new HashMap<>();

        actionLastExecution.forEach((key, record) -> {
            if (key.startsWith(processName + ":")) {
                try {
                    String actionName = key.substring((processName + ":").length());
                    history.put(ActionType.valueOf(actionName), record);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid action type in cooldown history: {}", key);
                }
            }
        });

        return history;
    }

    // ==================== Private Helper Methods ====================

    /**
     * Build a unique key for process + action combination.
     * 
     * @param processName Name of the process
     * @param actionType  The action type
     * @return Composite key
     */
    private String buildKey(String processName, ActionType actionType) {
        return processName + ":" + actionType.name();
    }

    /**
     * Internal record for tracking action execution.
     */
    @Data
    public static class ActionExecutionRecord {
        private String processName;
        private int targetPid;
        private String actionName;
        private long lastExecutionTime;
        private int executionCount;

        public static ActionExecutionRecord.ActionExecutionRecordBuilder builder() {
            return new ActionExecutionRecord.ActionExecutionRecordBuilder();
        }

        public static class ActionExecutionRecordBuilder {
            private String processName;
            private int targetPid;
            private String actionName;
            private long lastExecutionTime;
            private int executionCount;

            public ActionExecutionRecordBuilder processName(String processName) {
                this.processName = processName;
                return this;
            }

            public ActionExecutionRecordBuilder targetPid(int targetPid) {
                this.targetPid = targetPid;
                return this;
            }

            public ActionExecutionRecordBuilder actionName(String actionName) {
                this.actionName = actionName;
                return this;
            }

            public ActionExecutionRecordBuilder lastExecutionTime(long lastExecutionTime) {
                this.lastExecutionTime = lastExecutionTime;
                return this;
            }

            public ActionExecutionRecordBuilder executionCount(int executionCount) {
                this.executionCount = executionCount;
                return this;
            }

            public ActionExecutionRecord build() {
                ActionExecutionRecord record = new ActionExecutionRecord();
                record.processName = this.processName;
                record.targetPid = this.targetPid;
                record.actionName = this.actionName;
                record.lastExecutionTime = this.lastExecutionTime;
                record.executionCount = this.executionCount;
                return record;
            }
        }
    }
}
