package com.aios.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to execute a rule action.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleExecutionRequest {
    private Long issueId;
    private String actionType;
    private boolean dryRun;
    private String approvedBy;
    private String comment;
}
