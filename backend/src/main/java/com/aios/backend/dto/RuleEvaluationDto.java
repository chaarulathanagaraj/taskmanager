package com.aios.backend.dto;

import com.aios.shared.enums.ActionType;
import com.aios.shared.enums.IssueType;
import com.aios.shared.enums.Severity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic evaluation result for an issue.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleEvaluationDto {

    private Long issueId;
    private IssueType issueType;
    private Severity severity;
    private double confidence;

    @Builder.Default
    private List<RuleMatchDto> matchedRules = new ArrayList<>();

    private ActionType recommendedAction;
    private String recommendationReason;

    private boolean protectedProcess;
    private boolean policyBlocked;
    private boolean requiresApproval;
    private boolean autoRemediationEligible;

    private String policyReason;

    @Builder.Default
    private Instant evaluatedAt = Instant.now();
}
