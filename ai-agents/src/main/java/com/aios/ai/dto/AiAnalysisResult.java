package com.aios.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Result of AI analysis from specialized agents.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisResult {

    /**
     * Root cause identified by the AI
     */
    private String rootCause;

    /**
     * Confidence level (0.0 - 1.0)
     */
    private double confidence;

    /**
     * Recommended action to take
     */
    private String recommendedAction;

    /**
     * Detailed reasoning for the diagnosis
     */
    private String reasoning;

    /**
     * Additional evidence collected
     */
    private Map<String, Object> evidence;

    /**
     * List of potential alternative causes
     */
    private List<String> alternativeCauses;

    /**
     * Risk assessment for remediation
     */
    private String riskAssessment;

    /**
     * Agent that produced this result
     */
    private String agentName;
}
