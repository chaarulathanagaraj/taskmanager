package com.aios.agent.remediation;

import com.aios.shared.dto.PolicyViolation;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Result of a remediation action execution.
 * 
 * Contains:
 * - Success/failure status
 * - Human-readable message
 * - Detailed execution information
 * - Timestamp
 * - Optional error details
 * 
 * Built using the Builder pattern for flexibility.
 */
@Data
@Builder
public class ActionResult {

    /**
     * Whether the action executed successfully.
     * true = action completed as intended (or simulated in dry-run)
     * false = action failed or was blocked
     */
    private boolean success;

    /**
     * Human-readable message describing the result.
     * Examples:
     * - "[DRY RUN] Would terminate process chrome.exe"
     * - "Process terminated successfully"
     * - "Failed: Cannot kill protected process"
     */
    private String message;

    /**
     * Additional details about the execution.
     * Can include:
     * - "dryRun": boolean
     * - "executionTimeMs": long
     * - "previousValue": Object (e.g., priority before change)
     * - "newValue": Object (e.g., priority after change)
     * - "affectedThreads": int
     * - "memoryFreed": long
     */
    @Builder.Default
    private Map<String, Object> details = new HashMap<>();

    /**
     * Timestamp when the action was executed.
     */
    private Instant executedAt;

    /**
     * Error message if the action failed.
     * Null if success = true.
     */
    private String error;

    /**
     * Exception that caused the failure (if any).
     * Null if no exception occurred.
     */
    private Exception exception;

    /**
     * Whether this was a dry-run execution.
     * Convenience field, may also be in details map.
     */
    private boolean dryRun;

    /**
     * The action type that was executed.
     * Useful for logging and tracking.
     */
    private String actionType;

    /**
     * Policy violation that blocked the action (if any).
     * Present when the action was blocked by safety policy.
     */
    private PolicyViolation policyViolation;

    /**
     * Add a detail to the details map.
     * Convenience method for fluent API.
     * 
     * @param key Detail key
     * @param value Detail value
     * @return this (for chaining)
     */
    public ActionResult addDetail(String key, Object value) {
        if (this.details == null) {
            this.details = new HashMap<>();
        }
        this.details.put(key, value);
        return this;
    }

    /**
     * Get a detail value by key with type casting.
     * 
     * @param key Detail key
     * @param defaultValue Default value if key not found
     * @param <T> Expected type
     * @return Detail value or default
     */
    @SuppressWarnings("unchecked")
    public <T> T getDetail(String key, T defaultValue) {
        if (details == null || !details.containsKey(key)) {
            return defaultValue;
        }
        try {
            return (T) details.get(key);
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    /**
     * Create a successful dry-run result.
     * 
     * @param message Description of what would happen
     * @return ActionResult for dry-run
     */
    public static ActionResult dryRunSuccess(String message) {
        return ActionResult.builder()
            .success(true)
            .message("[DRY RUN] " + message)
            .dryRun(true)
            .executedAt(Instant.now())
            .build();
    }

    /**
     * Create a successful execution result.
     * 
     * @param message Description of what happened
     * @return ActionResult for successful execution
     */
    public static ActionResult success(String message) {
        return ActionResult.builder()
            .success(true)
            .message(message)
            .dryRun(false)
            .executedAt(Instant.now())
            .build();
    }

    /**
     * Create a failure result.
     * 
     * @param message Error description
     * @return ActionResult for failed execution
     */
    public static ActionResult failure(String message) {
        return ActionResult.builder()
            .success(false)
            .message(message)
            .error(message)
            .executedAt(Instant.now())
            .build();
    }

    /**
     * Create a failure result with exception.
     * 
     * @param message Error description
     * @param exception The exception that caused the failure
     * @return ActionResult for failed execution
     */
    public static ActionResult failure(String message, Exception exception) {
        return ActionResult.builder()
            .success(false)
            .message(message)
            .error(message)
            .exception(exception)
            .executedAt(Instant.now())
            .build();
    }

    /**
     * Create a failure result with Throwable.
     * 
     * @param message Error description
     * @param throwable The throwable that caused the failure
     * @return ActionResult for failed execution
     */
    public static ActionResult failure(String message, Throwable throwable) {
        return ActionResult.builder()
            .success(false)
            .message(message)
            .error(message)
            .exception(throwable instanceof Exception ? (Exception) throwable : new RuntimeException(throwable))
            .executedAt(Instant.now())
            .build();
    }

    /**
     * Create a failure result due to policy violation.
     * 
     * @param violation the policy violation that blocked the action
     * @return ActionResult for policy-blocked execution
     */
    public static ActionResult policyBlocked(PolicyViolation violation) {
        String message = "Blocked by policy: " + violation.getReason();
        return ActionResult.builder()
            .success(false)
            .message(message)
            .error(message)
            .policyViolation(violation)
            .executedAt(Instant.now())
            .build()
            .addDetail("policyName", violation.getPolicyName())
            .addDetail("severity", violation.getSeverity())
            .addDetail("overridable", violation.isOverridable())
            .addDetail("blockedByPolicy", true);
    }

    /**
     * Check if this result was blocked by policy.
     * 
     * @return true if action was blocked by safety policy
     */
    public boolean isBlockedByPolicy() {
        return policyViolation != null && policyViolation.isViolated();
    }
}
