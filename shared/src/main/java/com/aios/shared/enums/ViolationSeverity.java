package com.aios.shared.enums;

/**
 * Severity levels for policy violations.
 * 
 * <p>
 * Used by the safety policy system to categorize
 * how severe a policy violation is.
 * 
 * @author AIOS Team
 * @since 1.0
 */
public enum ViolationSeverity {

    /**
     * Low severity - informational warning only.
     * Action can proceed but will be logged.
     */
    LOW,

    /**
     * Medium severity - requires additional confirmation.
     * Action can proceed with user acknowledgment.
     */
    MEDIUM,

    /**
     * High severity - requires explicit approval.
     * Action blocked until approved by administrator.
     */
    HIGH,

    /**
     * Critical severity - action absolutely blocked.
     * Cannot be overridden, system protection in place.
     */
    CRITICAL
}
