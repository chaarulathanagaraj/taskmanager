package com.aios.agent.remediation;

import com.aios.shared.enums.SafetyLevel;

/**
 * Interface for all remediation actions that can be executed on processes.
 * 
 * Remediation actions are executed in response to detected issues to resolve
 * or mitigate problems automatically or with manual approval.
 * 
 * All implementations must:
 * - Support dry-run mode (simulation without actual execution)
 * - Perform safety checks (e.g., protected processes)
 * - Return detailed results with success/failure status
 * - Declare their safety level for risk assessment
 * 
 * Example implementations:
 * - KillProcessAction
 * - ReducePriorityAction
 * - TrimWorkingSetAction
 * - SuspendProcessAction
 * 
 * Usage:
 * <pre>
 * RemediationContext context = RemediationContext.builder()
 *     .targetPid(1234)
 *     .processName("chrome.exe")
 *     .dryRun(true)
 *     .build();
 * 
 * RemediationAction action = new KillProcessAction();
 * ActionResult result = action.execute(context);
 * 
 * if (result.isSuccess()) {
 *     log.info("Action executed: {}", result.getMessage());
 * }
 * </pre>
 */
public interface RemediationAction {

    /**
     * Execute the remediation action on the target process.
     * 
     * @param context Execution context containing target process, issue details,
     *                dry-run flag, and safety constraints
     * @return ActionResult containing success status, message, and execution details
     * @throws RemediationException if the action cannot be executed due to
     *                              safety violations or system errors
     */
    ActionResult execute(RemediationContext context) throws RemediationException;

    /**
     * Get the safety level of this action.
     * 
     * Safety levels indicate the risk of executing this action:
     * - LOW: Minimal risk (e.g., changing priority)
     * - MEDIUM: Moderate risk (e.g., trimming memory)
     * - HIGH: High risk (e.g., killing process)
     * - CRITICAL: Critical risk (e.g., system-level changes)
     * 
     * Higher safety levels may require additional approval or validation.
     * 
     * @return SafetyLevel enum indicating the risk level
     */
    SafetyLevel getSafetyLevel();

    /**
     * Check if this action should run in dry-run mode by default.
     * 
     * Some actions may always run in dry-run mode until explicitly
     * configured otherwise for maximum safety.
     * 
     * Default implementation returns false (respects context setting).
     * 
     * @return true if dry-run should be forced, false otherwise
     */
    default boolean isDryRun() {
        return false;
    }

    /**
     * Get a human-readable name for this action.
     * 
     * Default implementation returns the simple class name.
     * 
     * @return Action name
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * Get a description of what this action does.
     * 
     * @return Action description
     */
    default String getDescription() {
        return "No description available";
    }

    /**
     * Check if this action is enabled.
     * 
     * Actions can be disabled via configuration without removing them
     * from the Spring context.
     * 
     * Default implementation returns true (enabled).
     * 
     * @return true if the action is enabled
     */
    default boolean isEnabled() {
        return true;
    }
}
