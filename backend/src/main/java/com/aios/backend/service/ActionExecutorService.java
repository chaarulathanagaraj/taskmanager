package com.aios.backend.service;

import com.aios.shared.client.AgentClient;
import com.aios.shared.dto.ActionResult;
import com.aios.shared.enums.ActionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for executing different types of remediation actions.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ActionExecutorService {

    private final AgentClient agentClient;
    private final IssueService issueService;

    /**
     * Execute an action on an issue.
     */
    public Map<String, Object> execute(String actionType, Long issueId, boolean dryRun) {
        log.info("Executing action {} for issue {} (dryRun={})", actionType, issueId, dryRun);

        var issue = issueService.getIssueById(issueId)
                .orElseThrow(() -> new IllegalArgumentException("Issue not found: " + issueId));

        Map<String, Object> result = new HashMap<>();
        result.put("actionType", actionType);
        result.put("issueId", issueId);
        result.put("dryRun", dryRun);

        // Capture state before execution for potential rollback
        Map<String, Object> beforeState = captureState(actionType, issue.getAffectedPid());
        result.put("beforeState", beforeState);

        try {
            ActionResult actionResult;

            switch (actionType) {
                case "KILL_PROCESS" ->
                    actionResult = executeKillProcess(issue.getAffectedPid(), dryRun);

                case "REDUCE_PRIORITY" ->
                    actionResult = executeReducePriority(issue.getAffectedPid(), dryRun);

                case "TRIM_WORKING_SET" ->
                    actionResult = executeTrimWorkingSet(issue.getAffectedPid(), dryRun);

                case "RESTART_PROCESS" ->
                    actionResult = executeRestartProcess(issue.getAffectedPid(), issue.getProcessName(), dryRun);

                default -> throw new IllegalArgumentException("Unsupported action type: " + actionType);
            }

            result.put("success", actionResult.isSuccess());
            result.put("message", actionResult.getMessage());
            result.put("details", actionResult.getDetails());

            if (!actionResult.isSuccess()) {
                throw new RuntimeException("Action execution failed: " + actionResult.getMessage());
            }

            return result;

        } catch (Exception e) {
            log.error("Failed to execute action {}: {}", actionType, e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
            throw new RuntimeException("Action execution failed", e);
        }
    }

    /**
     * Rollback a previous action execution.
     */
    public Map<String, Object> rollback(String actionType, Long issueId, Map<String, Object> beforeState) {
        log.info("Rolling back action {} for issue {}", actionType, issueId);

        var issue = issueService.getIssueById(issueId)
                .orElseThrow(() -> new IllegalArgumentException("Issue not found: " + issueId));

        Map<String, Object> result = new HashMap<>();
        result.put("actionType", actionType);
        result.put("issueId", issueId);
        result.put("rollback", true);

        try {
            ActionResult rollbackResult;

            switch (actionType) {
                case "KILL_PROCESS" ->
                    rollbackResult = rollbackKillProcess(beforeState);

                case "REDUCE_PRIORITY" ->
                    rollbackResult = rollbackReducePriority(issue.getAffectedPid(), beforeState);

                case "TRIM_WORKING_SET" ->
                    rollbackResult = rollbackTrimWorkingSet(issue.getAffectedPid());

                case "RESTART_PROCESS" ->
                    rollbackResult = rollbackRestartProcess(beforeState);

                default -> throw new IllegalArgumentException("Unsupported action type for rollback: " + actionType);
            }

            result.put("success", rollbackResult.isSuccess());
            result.put("message", rollbackResult.getMessage());
            result.put("details", rollbackResult.getDetails());

            return result;

        } catch (Exception e) {
            log.error("Failed to rollback action {}: {}", actionType, e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
            throw new RuntimeException("Rollback failed", e);
        }
    }

    public Map<String, Object> executePID(String actionType, int pid, String processName, boolean dryRun) {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("actionType", actionType);
        result.put("targetPid", pid);
        result.put("dryRun", dryRun);
        Map<String, Object> beforeState = captureState(actionType, pid);
        result.put("beforeState", beforeState);
        try {
            com.aios.shared.dto.ActionResult actionResult;
            switch (actionType) {
                case "KILL_PROCESS" -> actionResult = executeKillProcess(pid, dryRun);
                case "REDUCE_PRIORITY" -> actionResult = executeReducePriority(pid, dryRun);
                case "TRIM_WORKING_SET" -> actionResult = executeTrimWorkingSet(pid, dryRun);
                case "RESTART_PROCESS" -> actionResult = executeRestartProcess(pid, processName, dryRun);
                default -> throw new IllegalArgumentException("Unsupported action type: " + actionType);
            }
            result.put("success", actionResult.isSuccess());
            result.put("message", actionResult.getMessage());
            result.put("details", actionResult.getDetails());
            return result;
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            throw new RuntimeException("Action execution failed", e);
        }
    }

    /**
     * Capture the current state before executing an action.
     */
    private Map<String, Object> captureState(String actionType, int pid) {
        Map<String, Object> state = new HashMap<>();
        state.put("pid", pid);
        state.put("actionType", actionType);
        state.put("timestamp", System.currentTimeMillis());

        try {
            // For REDUCE_PRIORITY, capture current priority
            if (ActionType.REDUCE_PRIORITY.equals(actionType)) {
                // Get current priority from agent
                var processInfo = agentClient.getProcessInfo(pid);
                if (processInfo != null) {
                    state.put("originalPriority", processInfo.get("priority"));
                }
            }

            // For KILL_PROCESS and RESTART_PROCESS, capture process details
            if (ActionType.KILL_PROCESS.equals(actionType) || ActionType.RESTART_PROCESS.equals(actionType)) {
                var processInfo = agentClient.getProcessInfo(pid);
                if (processInfo != null) {
                    state.put("processName", processInfo.get("name"));
                    state.put("commandLine", processInfo.get("commandLine"));
                    state.put("workingDirectory", processInfo.get("workingDirectory"));
                }
            }

        } catch (Exception e) {
            log.warn("Failed to capture state for {}: {}", actionType, e.getMessage());
        }

        return state;
    }

    private ActionResult rollbackKillProcess(Map<String, Object> beforeState) {
        // Cannot restore a killed process automatically
        String processName = (String) beforeState.get("processName");
        return ActionResult.failure("Cannot automatically restart killed process: " + processName +
                ". Manual intervention required.");
    }

    private ActionResult rollbackReducePriority(int pid, Map<String, Object> beforeState) {
        Object originalPriority = beforeState.get("originalPriority");
        if (originalPriority != null) {
            log.info("Restoring priority of PID {} to {}", pid, originalPriority);
            return agentClient.executeAction("SET_PRIORITY",
                    Map.of("pid", pid, "priority", originalPriority));
        }
        return ActionResult.failure("Original priority not available for rollback");
    }

    private ActionResult rollbackTrimWorkingSet(int pid) {
        // Trimming working set doesn't need rollback - OS will allocate memory as
        // needed
        return ActionResult.success("Working set trim does not require rollback");
    }

    private ActionResult rollbackRestartProcess(Map<String, Object> beforeState) {
        // Process was restarted, so it should already be running
        return ActionResult.success("Process restart does not require rollback (process should be running)");
    }

    private ActionResult executeKillProcess(int pid, boolean dryRun) {
        log.info("Executing KILL_PROCESS for PID {} (dryRun={})", pid, dryRun);

        if (dryRun) {
            return ActionResult.success("Dry run: Would kill process " + pid);
        }

        return agentClient.executeAction(ActionType.KILL_PROCESS.name(), Map.of("pid", pid));
    }

    private ActionResult executeReducePriority(int pid, boolean dryRun) {
        log.info("Executing REDUCE_PRIORITY for PID {} (dryRun={})", pid, dryRun);

        if (dryRun) {
            return ActionResult.success("Dry run: Would reduce priority of process " + pid);
        }

        return agentClient.executeAction(ActionType.REDUCE_PRIORITY.name(), Map.of("pid", pid));
    }

    private ActionResult executeTrimWorkingSet(int pid, boolean dryRun) {
        log.info("Executing TRIM_WORKING_SET for PID {} (dryRun={})", pid, dryRun);

        if (dryRun) {
            return ActionResult.success("Dry run: Would trim working set of process " + pid);
        }

        return agentClient.executeAction(ActionType.TRIM_WORKING_SET.name(), Map.of("pid", pid));
    }

    private ActionResult executeRestartProcess(int pid, String processName, boolean dryRun) {
        log.info("Executing RESTART_PROCESS for {} (PID={}, dryRun={})", processName, pid, dryRun);

        if (dryRun) {
            return ActionResult.success("Dry run: Would restart process " + processName + " (PID=" + pid + ")");
        }

        return agentClient.executeAction(ActionType.RESTART_PROCESS.name(),
                Map.of("pid", pid, "processName", processName));
    }
}
