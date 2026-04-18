package com.aios.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Result of a rule execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleExecutionResult {
    private Long executionId;
    private Long issueId;
    private ExecutionStatus status;
    private String actionType;
    private boolean dryRun;
    private boolean success;
    private String message;
    private Map<String, Object> executionDetails;
    private Instant startedAt;
    private Instant completedAt;
    private Long durationMs;
    private String executedBy;
    private String approvedBy;
    private String errorMessage;
    private String rollbackInfo;
}
