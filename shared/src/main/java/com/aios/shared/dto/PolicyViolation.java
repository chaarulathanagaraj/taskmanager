package com.aios.shared.dto;

import com.aios.shared.enums.ActionType;
import com.aios.shared.enums.ViolationSeverity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a policy violation detected during remediation planning.
 * 
 * <p>
 * When the safety policy system detects that an action would violate
 * a configured policy (e.g., attempting to kill a protected process),
 * it returns a PolicyViolation containing details about the violation.
 * 
 * <p>
 * Example usage:
 * 
 * <pre>{@code
 * PolicyViolation violation = safetyPolicyService.checkPolicy(context);
 * if (violation.isViolated()) {
 *     log.warn("Policy violation: {}", violation.getReason());
 *     // Block action or request approval
 * }
 * }</pre>
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyViolation {

    /**
     * Whether a violation was detected.
     */
    private boolean violated;

    /**
     * Human-readable reason for the violation.
     */
    private String reason;

    /**
     * The policy name/ID that was violated.
     */
    private String policyName;

    /**
     * Severity of the violation.
     */
    private ViolationSeverity severity;

    /**
     * The action type that was attempted.
     */
    private ActionType attemptedAction;

    /**
     * The process that was targeted.
     */
    private String targetProcess;

    /**
     * The PID that was targeted.
     */
    private Integer targetPid;

    /**
     * Whether the violation is blocking (action cannot proceed).
     */
    @Builder.Default
    private boolean blocking = true;

    /**
     * Whether admin approval can override this violation.
     */
    @Builder.Default
    private boolean overridable = false;

    /**
     * Timestamp when the violation was detected.
     */
    @Builder.Default
    private Instant detectedAt = Instant.now();

    /**
     * Additional details about the violation.
     */
    @Builder.Default
    private List<String> details = new ArrayList<>();

    /**
     * Suggested alternative actions.
     */
    @Builder.Default
    private List<ActionType> suggestedAlternatives = new ArrayList<>();

    /**
     * Create a non-violation result (action is allowed).
     */
    public static PolicyViolation allowed() {
        return PolicyViolation.builder()
                .violated(false)
                .blocking(false)
                .build();
    }

    /**
     * Create a violation for a protected process.
     */
    public static PolicyViolation protectedProcess(String processName, int pid, ActionType attemptedAction) {
        return PolicyViolation.builder()
                .violated(true)
                .reason("Process '" + processName + "' is protected and cannot be modified")
                .policyName("PROTECTED_PROCESS")
                .severity(ViolationSeverity.CRITICAL)
                .targetProcess(processName)
                .targetPid(pid)
                .attemptedAction(attemptedAction)
                .blocking(true)
                .overridable(false)
                .build();
    }

    /**
     * Create a violation requiring user approval.
     */
    public static PolicyViolation requiresApproval(ActionType action, String reason) {
        return PolicyViolation.builder()
                .violated(true)
                .reason(reason)
                .policyName("REQUIRES_APPROVAL")
                .severity(ViolationSeverity.HIGH)
                .attemptedAction(action)
                .blocking(true)
                .overridable(true)
                .build();
    }

    /**
     * Create a violation for disabled action type.
     */
    public static PolicyViolation actionDisabled(ActionType action) {
        return PolicyViolation.builder()
                .violated(true)
                .reason("Action type '" + action + "' is disabled in current configuration")
                .policyName("ACTION_DISABLED")
                .severity(ViolationSeverity.HIGH)
                .attemptedAction(action)
                .blocking(true)
                .overridable(false)
                .build();
    }

    /**
     * Create a warning (non-blocking violation).
     */
    public static PolicyViolation warning(String reason) {
        return PolicyViolation.builder()
                .violated(true)
                .reason(reason)
                .policyName("WARNING")
                .severity(ViolationSeverity.LOW)
                .blocking(false)
                .overridable(true)
                .build();
    }

    /**
     * Add a detail to the violation.
     */
    public PolicyViolation addDetail(String detail) {
        if (this.details == null) {
            this.details = new ArrayList<>();
        }
        this.details.add(detail);
        return this;
    }

    /**
     * Add a suggested alternative action.
     */
    public PolicyViolation addAlternative(ActionType alternative) {
        if (this.suggestedAlternatives == null) {
            this.suggestedAlternatives = new ArrayList<>();
        }
        this.suggestedAlternatives.add(alternative);
        return this;
    }
}
