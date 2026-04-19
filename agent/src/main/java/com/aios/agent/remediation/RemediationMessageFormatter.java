package com.aios.agent.remediation;

import com.aios.shared.enums.ActionType;
import lombok.extern.slf4j.Slf4j;

/**
 * Formats remediation failure and status messages with contextual details.
 * Provides customized, process-specific messages for different failure
 * scenarios.
 * 
 * <p>
 * This formatter ensures that each process receives a unique, descriptive
 * message
 * that includes the process name, PID, action type, and relevant context.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Slf4j
public final class RemediationMessageFormatter {

    private RemediationMessageFormatter() {
    }

    // ==================== Cooldown & Lock Messages ====================

    /**
     * Format a cooldown lock message for a remediation action.
     * Used when the same action was recently executed and is temporarily blocked.
     * 
     * @param processName     Name of the process
     * @param pid             Process ID
     * @param actionType      The action that was attempted
     * @param cooldownMinutes Duration of the cooldown in minutes
     * @return Customized cooldown message
     */
    public static String cooldownLocked(String processName, int pid, ActionType actionType, int cooldownMinutes) {
        return String.format(
                "Remediation skipped for %s (PID: %d) because '%s' was already executed recently. " +
                        "Retry is blocked until the %d-minute cooldown expires.",
                processName, pid, formatActionName(actionType), cooldownMinutes);
    }

    /**
     * Format a cooldown message for a specific process with execution details.
     * 
     * @param processName         Name of the process
     * @param pid                 Process ID
     * @param actionType          The action that was attempted
     * @param cooldownMinutes     Duration of the cooldown in minutes
     * @param lastExecutionTimeMs When the action was last executed (ms ago)
     * @return Customized cooldown message with timing
     */
    public static String cooldownLockedWithTiming(String processName, int pid, ActionType actionType,
            int cooldownMinutes, long lastExecutionTimeMs) {
        long remainingMs = (cooldownMinutes * 60 * 1000L) - lastExecutionTimeMs;
        long remainingSeconds = Math.max(0, remainingMs / 1000);
        long remainingMinutes = remainingSeconds / 60;

        return String.format(
                "%s (PID: %d) is temporarily locked because the same remediation action '%s' was applied recently. " +
                        "Try again after %d minutes (%d seconds remaining).",
                processName, pid, formatActionName(actionType), cooldownMinutes, remainingSeconds);
    }

    /**
     * Format a duplicate action message for safety.
     * 
     * @param processName Name of the process
     * @param pid         Process ID
     * @param actionType  The action that was attempted
     * @return Duplicate action message
     */
    public static String duplicateActionPrevented(String processName, int pid, ActionType actionType) {
        return String.format(
                "Duplicate remediation prevented for %s (PID: %d). Action '%s' is already running or was recently executed.",
                processName, pid, formatActionName(actionType));
    }

    // ==================== Policy Violation Messages ====================

    /**
     * Format a protected process violation message.
     * 
     * @param processName Name of the protected process
     * @param pid         Process ID
     * @param actionType  The action that was attempted
     * @return Protected process message
     */
    public static String protectedProcessBlocked(String processName, int pid, ActionType actionType) {
        return String.format(
                "Cannot execute '%s' on %s (PID: %d) - this is a protected system process. " +
                        "Action blocked for system stability.",
                formatActionName(actionType), processName, pid);
    }

    /**
     * Format a confidence threshold not met message.
     * 
     * @param processName        Name of the process
     * @param pid                Process ID
     * @param issueType          Type of issue detected
     * @param currentConfidence  Current confidence percentage
     * @param requiredConfidence Required confidence threshold
     * @return Confidence threshold message
     */
    public static String confidenceThresholdNotMet(String processName, int pid, String issueType,
            double currentConfidence, double requiredConfidence) {
        return String.format(
                "%s (PID: %d) - %s issue detected with %.1f%% confidence. " +
                        "Confidence %.1f%% below required threshold of %.1f%% - requires manual approval.",
                processName, pid, issueType, currentConfidence * 100, currentConfidence * 100,
                requiredConfidence * 100);
    }

    /**
     * Format a policy violation message.
     * 
     * @param processName Name of the process
     * @param pid         Process ID
     * @param actionType  The action that was attempted
     * @param policyName  Name of the policy that was violated
     * @param reason      Reason for the violation
     * @return Policy violation message
     */
    public static String policyViolation(String processName, int pid, ActionType actionType,
            String policyName, String reason) {
        return String.format(
                "Action '%s' on %s (PID: %d) blocked by policy '%s': %s",
                formatActionName(actionType), processName, pid, policyName, reason);
    }

    // ==================== Failure Messages ====================

    /**
     * Format a process not found message.
     * 
     * @param processName Name of the process
     * @param pid         Process ID
     * @return Process not found message
     */
    public static String processNotFound(String processName, int pid) {
        return String.format(
                "Cannot remediate %s (PID: %d) - process not found or already terminated.",
                processName, pid);
    }

    /**
     * Format an insufficient permissions message.
     * 
     * @param processName Name of the process
     * @param pid         Process ID
     * @param actionType  The action that failed
     * @return Insufficient permissions message
     */
    public static String insufficientPermissions(String processName, int pid, ActionType actionType) {
        return String.format(
                "Cannot execute '%s' on %s (PID: %d) - insufficient permissions. " +
                        "Administrator privileges required.",
                formatActionName(actionType), processName, pid);
    }

    /**
     * Format an action timeout message.
     * 
     * @param processName Name of the process
     * @param pid         Process ID
     * @param actionType  The action that timed out
     * @param timeoutMs   Timeout duration in milliseconds
     * @return Timeout message
     */
    public static String actionTimeout(String processName, int pid, ActionType actionType, long timeoutMs) {
        return String.format(
                "Remediation timeout for %s (PID: %d) executing '%s' - exceeded %d second(s).",
                processName, pid, formatActionName(actionType), timeoutMs / 1000);
    }

    // ==================== Success Messages ====================

    /**
     * Format a successful remediation message.
     * 
     * @param processName Name of the process
     * @param pid         Process ID
     * @param actionType  The action that was executed
     * @param details     Additional details (e.g., "priority changed to
     *                    BELOW_NORMAL")
     * @return Success message
     */
    public static String remediationSuccessful(String processName, int pid, ActionType actionType, String details) {
        String message = String.format(
                "Successfully executed '%s' on %s (PID: %d).",
                formatActionName(actionType), processName, pid);
        if (details != null && !details.isEmpty()) {
            message += " " + details;
        }
        return message;
    }

    /**
     * Format a dry-run message.
     * 
     * @param processName Name of the process
     * @param pid         Process ID
     * @param actionType  The action that would be executed
     * @return Dry-run message
     */
    public static String dryRunMessage(String processName, int pid, ActionType actionType) {
        return String.format(
                "[DRY RUN] Would execute '%s' on %s (PID: %d) - no actual changes made.",
                formatActionName(actionType), processName, pid);
    }

    // ==================== Resource-Specific Messages ====================

    /**
     * Format a memory leak remediation message.
     * 
     * @param processName         Name of the process
     * @param pid                 Process ID
     * @param actionType          The action being taken
     * @param memoryLeakRateMbMin Memory leak rate in MB/minute
     * @return Memory leak remediation message
     */
    public static String memoryLeakRemediation(String processName, int pid, ActionType actionType,
            double memoryLeakRateMbMin) {
        return String.format(
                "Remediating memory leak in %s (PID: %d) - leaking at %.2f MB/min. " +
                        "Executing '%s'.",
                processName, pid, memoryLeakRateMbMin, formatActionName(actionType));
    }

    /**
     * Format a resource hog remediation message.
     * 
     * @param processName    Name of the process
     * @param pid            Process ID
     * @param actionType     The action being taken
     * @param currentUsageMb Current resource usage in MB
     * @return Resource hog remediation message
     */
    public static String resourceHogRemediation(String processName, int pid, ActionType actionType,
            long currentUsageMb) {
        return String.format(
                "Remediating resource hogging in %s (PID: %d) - currently using %d MB. " +
                        "Executing '%s'.",
                processName, pid, currentUsageMb, formatActionName(actionType));
    }

    // ==================== Helper Methods ====================

    /**
     * Format an action type name for display.
     * Converts enum names to readable format.
     * 
     * @param actionType The action type
     * @return Formatted action name (e.g., "Kill Process", "Reduce Priority")
     */
    private static String formatActionName(ActionType actionType) {
        if (actionType == null) {
            return "Unknown";
        }

        String enumName = actionType.name();
        StringBuilder result = new StringBuilder();

        for (String word : enumName.split("_")) {
            if (!word.isEmpty()) {
                result.append(word.charAt(0))
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }

        return result.toString().trim();
    }

    /**
     * Format a duration in human-readable format.
     * 
     * @param milliseconds Duration in milliseconds
     * @return Formatted duration (e.g., "2m 30s", "45s")
     */
    public static String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;

        if (minutes > 0) {
            return String.format("%dm %ds", minutes, remainingSeconds);
        }
        return String.format("%ds", seconds);
    }

    /**
     * Create a detailed remediation status summary.
     * 
     * @param processName Name of the process
     * @param pid         Process ID
     * @param issueType   Type of issue
     * @param actionType  Action being taken
     * @param status      Current status
     * @return Detailed status message
     */
    public static String remediationStatusSummary(String processName, int pid, String issueType,
            ActionType actionType, String status) {
        return String.format(
                "[%s] %s (PID: %d) | Issue: %s | Action: %s",
                status.toUpperCase(), processName, pid, issueType, formatActionName(actionType));
    }
}
