package com.aios.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single deterministic rule match for an issue.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleMatchDto {

    /**
     * Stable identifier for the matched rule.
     */
    private String ruleId;

    /**
     * Human-readable rule name.
     */
    private String ruleName;

    /**
     * Why this rule matched.
     */
    private String rationale;

    /**
     * Rule match score (0.0 - 1.0).
     */
    private double score;
}
