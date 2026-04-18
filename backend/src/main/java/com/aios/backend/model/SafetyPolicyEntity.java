package com.aios.backend.model;

import com.aios.shared.enums.ActionType;
import com.aios.shared.enums.SafetyLevel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA Entity for storing safety policies.
 * 
 * <p>
 * Safety policies define rules that control when and how
 * remediation actions can be executed. They enforce:
 * <ul>
 * <li>Which actions require approval</li>
 * <li>Which processes are protected</li>
 * <li>Which actions are allowed in production vs dry-run mode</li>
 * </ul>
 * 
 * <p>
 * Table: safety_policies
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Entity
@Table(name = "safety_policies", indexes = {
        @Index(name = "idx_policy_action_type", columnList = "action_type"),
        @Index(name = "idx_policy_safety_level", columnList = "safety_level"),
        @Index(name = "idx_policy_enabled", columnList = "enabled")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SafetyPolicyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Name of the policy for display and identification.
     */
    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    /**
     * Description of what this policy enforces.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * The action type this policy applies to.
     * If null, the policy applies to all action types.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", length = 50)
    private ActionType actionType;

    /**
     * The minimum safety level required for this policy to apply.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "safety_level", length = 20)
    private SafetyLevel safetyLevel;

    /**
     * Whether this action requires user approval before execution.
     */
    @Column(name = "requires_approval", nullable = false)
    @Builder.Default
    private Boolean requiresApproval = false;

    /**
     * Whether this action is allowed in dry-run mode.
     */
    @Column(name = "allowed_in_dry_run", nullable = false)
    @Builder.Default
    private Boolean allowedInDryRun = true;

    /**
     * Whether this action is allowed in production mode.
     */
    @Column(name = "allowed_in_production", nullable = false)
    @Builder.Default
    private Boolean allowedInProduction = true;

    /**
     * Whether the policy is currently enabled.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * Priority for policy evaluation (lower = higher priority).
     * When multiple policies match, the highest priority wins.
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 100;

    /**
     * Minimum AI confidence required for auto-execution.
     * Range: 0.0 - 1.0
     */
    @Column(name = "min_confidence_threshold")
    @Builder.Default
    private Double minConfidenceThreshold = 0.8;

    /**
     * Maximum number of times this action can be executed per hour.
     * Null means unlimited.
     */
    @Column(name = "max_executions_per_hour")
    private Integer maxExecutionsPerHour;

    /**
     * Patterns for protected processes that this policy blocks.
     * Uses glob patterns (e.g., "csrss.exe", "System*", "*svchost*").
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "safety_policy_protected_patterns", joinColumns = @JoinColumn(name = "policy_id"))
    @Column(name = "pattern")
    @Builder.Default
    private List<String> protectedProcessPatterns = new ArrayList<>();

    /**
     * Patterns for process names that are explicitly allowed (whitelist).
     * If not empty, only matching processes can be targeted.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "safety_policy_allowed_patterns", joinColumns = @JoinColumn(name = "policy_id"))
    @Column(name = "pattern")
    @Builder.Default
    private List<String> allowedProcessPatterns = new ArrayList<>();

    /**
     * When this policy was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * When this policy was last updated.
     */
    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    /**
     * Who created this policy.
     */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Check if a process name matches any protected pattern.
     */
    public boolean isProcessProtected(String processName) {
        if (protectedProcessPatterns == null || protectedProcessPatterns.isEmpty()) {
            return false;
        }
        return protectedProcessPatterns.stream()
                .anyMatch(pattern -> matchesGlobPattern(processName, pattern));
    }

    /**
     * Check if a process name is in the allowed whitelist.
     * Returns true if whitelist is empty (all allowed by default).
     */
    public boolean isProcessAllowed(String processName) {
        if (allowedProcessPatterns == null || allowedProcessPatterns.isEmpty()) {
            return true; // Empty whitelist means all allowed
        }
        return allowedProcessPatterns.stream()
                .anyMatch(pattern -> matchesGlobPattern(processName, pattern));
    }

    /**
     * Simple glob pattern matching.
     * Supports * (matches any characters) and ? (matches single character).
     */
    private boolean matchesGlobPattern(String text, String pattern) {
        if (text == null || pattern == null) {
            return false;
        }
        // Convert glob to regex
        String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".");
        return text.toLowerCase().matches(regex.toLowerCase());
    }

    /**
     * Check if this policy applies to the given action type.
     */
    public boolean appliesTo(ActionType action) {
        // If actionType is null, policy applies to all actions
        return this.actionType == null || this.actionType == action;
    }
}
