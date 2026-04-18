package com.aios.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Result of a remediation action execution (DTO version for shared use).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionResult {

    /**
     * Whether the action executed successfully.
     */
    private boolean success;

    /**
     * Human-readable message describing the result.
     */
    private String message;

    /**
     * Additional details about the execution.
     */
    @Builder.Default
    private Map<String, Object> details = new HashMap<>();

    /**
     * Timestamp when the action was executed.
     */
    private Instant executedAt;

    /**
     * Error message if the action failed.
     */
    private String error;

    /**
     * Whether this was a dry-run execution.
     */
    private boolean dryRun;

    /**
     * The action type that was executed.
     */
    private String actionType;

    /**
     * Create a successful result.
     */
    public static ActionResult success(String message) {
        return ActionResult.builder()
                .success(true)
                .message(message)
                .executedAt(Instant.now())
                .build();
    }

    /**
     * Create a successful result with details.
     */
    public static ActionResult success(String message, Map<String, Object> details) {
        return ActionResult.builder()
                .success(true)
                .message(message)
                .details(details)
                .executedAt(Instant.now())
                .build();
    }

    /**
     * Create a failure result.
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
     * Create a failure result with error details.
     */
    public static ActionResult failure(String message, String error) {
        return ActionResult.builder()
                .success(false)
                .message(message)
                .error(error)
                .executedAt(Instant.now())
                .build();
    }
}
