package com.aios.agent.remediation;

/**
 * Exception thrown when a remediation action cannot be executed.
 * 
 * This can occur due to:
 * - Safety violations (e.g., attempting to kill a protected process)
 * - Insufficient permissions
 * - Process not found
 * - System-level errors
 * - Invalid action parameters
 * 
 * Remediation actions should throw this exception for expected failures
 * that should be handled gracefully (e.g., logged and reported to user).
 * 
 * For unexpected errors, allow RuntimeException to propagate normally.
 */
public class RemediationException extends Exception {

    /**
     * Create a new remediation exception with a message.
     * 
     * @param message Detailed error message
     */
    public RemediationException(String message) {
        super(message);
    }

    /**
     * Create a new remediation exception with a message and cause.
     * 
     * @param message Detailed error message
     * @param cause The underlying cause of the failure
     */
    public RemediationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Create a new remediation exception with a cause.
     * 
     * @param cause The underlying cause of the failure
     */
    public RemediationException(Throwable cause) {
        super(cause);
    }

    /**
     * Create an exception for protected process violation.
     * 
     * @param processName Name of the protected process
     * @return RemediationException
     */
    public static RemediationException protectedProcess(String processName) {
        return new RemediationException(
            String.format("Cannot execute action on protected process: %s", processName)
        );
    }

    /**
     * Create an exception for process not found.
     * 
     * @param pid Process ID
     * @return RemediationException
     */
    public static RemediationException processNotFound(int pid) {
        return new RemediationException(
            String.format("Process not found: PID %d", pid)
        );
    }

    /**
     * Create an exception for insufficient permissions.
     * 
     * @param action Action name
     * @param pid Process ID
     * @return RemediationException
     */
    public static RemediationException insufficientPermissions(String action, int pid) {
        return new RemediationException(
            String.format("Insufficient permissions to %s on PID %d", action, pid)
        );
    }

    /**
     * Create an exception for low confidence score.
     * 
     * @param confidence Actual confidence
     * @param threshold Required threshold
     * @return RemediationException
     */
    public static RemediationException lowConfidence(double confidence, double threshold) {
        return new RemediationException(
            String.format("Action confidence %.2f%% below threshold %.2f%% - requires manual approval",
                confidence * 100, threshold * 100)
        );
    }
}
