package com.aios.agent.client;

import com.aios.shared.dto.ActionResult;

import java.util.Map;

/**
 * Client for communicating with the Agent service from the Backend.
 */
public interface AgentClient {

    /**
     * Execute an action on the agent service.
     *
     * @param actionType the type of action to execute
     * @param parameters action parameters (e.g., pid, priority, etc.)
     * @return result of the action execution
     */
    ActionResult executeAction(String actionType, Map<String, Object> parameters);

    /**
     * Get information about a process from the agent service.
     *
     * @param pid the process ID
     * @return process information map
     */
    Map<String, Object> getProcessInfo(int pid);

    /**
     * Check if the agent service is healthy and responsive.
     *
     * @return true if agent is healthy, false otherwise
     */
    boolean isHealthy();
}
