package com.aios.agent.remediation;

import com.aios.shared.dto.DiagnosticIssue;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Context object containing all information needed to execute a remediation
 * action.
 * 
 * This immutable context is passed to remediation actions to provide:
 * - Target process identification (PID, name)
 * - Issue details that triggered the action
 * - Safety constraints (protected processes, dry-run mode)
 * - Additional metadata for decision making
 * 
 * Built using the Builder pattern for flexibility.
 */
@Data
@Builder
public class RemediationContext {

    /**
     * Process ID of the target process.
     * Required for all actions.
     */
    private int targetPid;

    /**
     * Name of the target process (e.g., "chrome.exe").
     * Used for logging and safety checks.
     */
    private String processName;

    /**
     * The diagnostic issue that triggered this remediation.
     * May be null for manually triggered actions.
     */
    private DiagnosticIssue issue;

    /**
     * Whether to run in dry-run mode (simulation only).
     * When true, actions log what they would do but don't actually execute.
     * Default: true (safe mode)
     */
    @Builder.Default
    private boolean dryRun = true;

    /**
     * List of protected process names that cannot be terminated.
     * Actions must check this list before executing high-risk operations.
     */
    private List<String> protectedProcesses;

    /**
     * Maximum allowed confidence score for automated execution.
     * Actions with lower confidence may require manual approval.
     * Range: 0.0 - 1.0
     */
    @Builder.Default
    private double confidenceThreshold = 0.85;

    /**
     * User who triggered the action (if manual).
     * Null for automated actions.
     */
    private String initiatedBy;

    /**
     * Additional metadata that might be useful for action execution.
     * Can include process metrics, historical data, etc.
     */
    private Map<String, Object> metadata;

    /**
     * Maximum time to wait for action completion (milliseconds).
     * Some actions may need to wait for process termination, etc.
     * Default: 30 seconds
     */
    @Builder.Default
    private long timeoutMillis = 30000;

    /**
     * Check if a process name is in the protected list.
     * 
     * @param processName Name to check
     * @return true if the process is protected
     */
    public boolean isProtected(String processName) {
        if (protectedProcesses == null || processName == null) {
            return false;
        }

        return protectedProcesses.stream()
                .anyMatch(pattern -> processName.equalsIgnoreCase(pattern) ||
                        processName.toLowerCase().contains(pattern.toLowerCase()));
    }

    /**
     * Check if the target process is protected.
     * 
     * @return true if the target process is protected
     */
    public boolean isTargetProtected() {
        return isProtected(this.processName);
    }

    /**
     * Get metadata value by key with type casting.
     * 
     * @param key          Metadata key
     * @param defaultValue Default value if key not found
     * @param <T>          Expected type
     * @return Metadata value or default
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, T defaultValue) {
        if (metadata == null || !metadata.containsKey(key)) {
            return defaultValue;
        }
        try {
            return (T) metadata.get(key);
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }
}
