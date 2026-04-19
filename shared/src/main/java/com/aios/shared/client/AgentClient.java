package com.aios.shared.client;

import com.aios.shared.dto.ActionResult;

import java.util.Map;

/**
 * Client interface for agent service operations.
 * This interface is implemented by backend services to call the agent.
 */
public interface AgentClient {

    /**
     * Execute a remediation action on the agent.
     *
     * @param actionType type of action to execute (e.g., "RESTART_PROCESS",
     *                   "KILL_PROCESS")
     * @param parameters action parameters (e.g., pid, processName)
     * @return result of the action execution
     */
    ActionResult executeAction(String actionType, Map<String, Object> parameters);

    /**
     * Get process information from agent.
     *
     * @param pid process ID
     * @return process information
     */
    Map<String, Object> getProcessInfo(int pid);

    /**
     * Diagnose why a remediation did not resolve an issue using Agent + MCP
     * context.
     *
     * @param actionType   action that was attempted
     * @param pid          affected process id
     * @param processName  affected process name
     * @param errorMessage latest execution or verification error message
     * @return structured diagnostics with category, explanation, and retry hints
     */
    default Map<String, Object> diagnoseResolutionFailure(String actionType, int pid, String processName,
            String errorMessage) {
        return Map.of(
                "failureCategory", "unknown_process_state",
                "explanation", "Resolution verification failed and no agent diagnostics were available.",
                "retryable", Boolean.TRUE,
                "actionType", actionType,
                "pid", pid,
                "processName", processName,
                "errorMessage", errorMessage == null ? "" : errorMessage,
                "source", "agent+mcp",
                "timestamp", java.time.Instant.now().toString());
    }
}
