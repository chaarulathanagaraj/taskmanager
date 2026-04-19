package com.aios.shared.dto;

/**
 * Execution status for rule actions.
 */
public enum ExecutionStatus {
    PENDING, // Waiting for approval
    APPROVED, // Approved, ready to execute
    EXECUTING, // Currently executing
    COMPLETED, // Successfully completed
    NEEDS_ATTENTION, // Executed but resolution still needs follow-up
    FAILED, // Execution failed
    ROLLED_BACK, // Action was rolled back
    CANCELLED // Cancelled by user
}
