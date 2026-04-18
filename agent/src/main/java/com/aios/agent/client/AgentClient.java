package com.aios.agent.client;

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
     * @param parameters action parameters (e.g., pid, procesName)
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
}
