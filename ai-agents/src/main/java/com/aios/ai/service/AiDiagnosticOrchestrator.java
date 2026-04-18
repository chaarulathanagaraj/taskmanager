package com.aios.ai.service;

import com.aios.ai.agents.*;
import com.aios.ai.dto.*;
import com.aios.shared.dto.DiagnosticIssue;
import com.aios.shared.enums.IssueType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Orchestrates multi-agent AI diagnosis workflow.
 * Coordinates specialized agents to analyze issues and create safe remediation
 * plans.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiDiagnosticOrchestrator {

    private final LeakDetectorAgent leakDetector;
    private final ThreadExpertAgent threadExpert;
    private final IOAnalystAgent ioAnalyst;
    private final RemediationPlannerAgent remediationPlanner;
    private final SafetyValidatorAgent safetyValidator;

    /**
     * Run complete AI diagnosis on an issue.
     * Routes to specialized agent, creates remediation plan, validates safety.
     */
    public CompleteDiagnosisReport diagnose(DiagnosticIssue issue) {
        log.info("Starting AI diagnosis for issue: {} (PID: {}, Type: {})",
                issue.getId(), issue.getAffectedPid(), issue.getType());

        long startTime = System.currentTimeMillis();

        try {
            // Step 1: Route to specialized agent based on issue type
            AiAnalysisResult analysis = routeToSpecializedAgent(issue);

            if (analysis == null || analysis.getConfidence() < 0.3) {
                String message = "Unable to analyze issue with sufficient confidence";
                if (analysis != null && analysis.getRootCause() != null && !analysis.getRootCause().isEmpty()) {
                    message = analysis.getRootCause();
                }
                return CompleteDiagnosisReport.builder()
                        .success(false)
                        .message(message)
                        .confidence(analysis != null ? analysis.getConfidence() : 0.0)
                        .timestamp(Instant.now())
                        .processingTimeMs(System.currentTimeMillis() - startTime)
                        .issueId(issue.getId())
                        .analyzedPid(issue.getAffectedPid())
                        .analyzedProcessName(issue.getProcessName())
                        .build();
            }

            log.info("Specialized agent analysis complete: confidence={}, rootCause={}",
                    analysis.getConfidence(), analysis.getRootCause());

            // Step 2: Create remediation plan
            RemediationPlan plan = remediationPlanner.createPlan(
                    analysis, issue.getAffectedPid(), issue.getProcessName());

            log.info("Remediation plan created: action={}, riskLevel={}",
                    plan.getPrimaryAction(), plan.getRiskLevel());

            // Step 3: Validate safety
            SafetyValidation validation = safetyValidator.validate(plan);

            log.info("Safety validation complete: safe={}, approvalRequired={}, score={}",
                    validation.isSafe(), validation.isApprovalRequired(), validation.getSafetyScore());

            // Apply safety warnings to plan
            if (!validation.isSafe()) {
                plan.setApprovalRequired(true);
            }
            if (!validation.getWarnings().isEmpty()) {
                plan.getWarnings().addAll(validation.getWarnings());
            }

            return CompleteDiagnosisReport.builder()
                    .success(true)
                    .message("Diagnosis complete")
                    .analysis(analysis)
                    .remediationPlan(plan)
                    .safetyValidation(validation)
                    .confidence(analysis.getConfidence())
                    .timestamp(Instant.now())
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .issueId(issue.getId())
                    .analyzedPid(issue.getAffectedPid())
                    .analyzedProcessName(issue.getProcessName())
                    .build();

        } catch (Exception e) {
            log.error("AI diagnosis failed: {}", e.getMessage(), e);
            return CompleteDiagnosisReport.builder()
                    .success(false)
                    .message("Diagnosis failed: " + e.getMessage())
                    .timestamp(Instant.now())
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .issueId(issue.getId())
                    .analyzedPid(issue.getAffectedPid())
                    .analyzedProcessName(issue.getProcessName())
                    .build();
        }
    }

    /**
     * Route issue to the appropriate specialized agent.
     */
    private AiAnalysisResult routeToSpecializedAgent(DiagnosticIssue issue) {
        IssueType type = issue.getType();

        if (type == null) {
            // Unknown type - try each agent and use best confidence
            return analyzeWithBestAgent(issue);
        }

        return switch (type) {
            case MEMORY_LEAK -> leakDetector.analyzeMemoryLeak(issue);
            case THREAD_EXPLOSION, HUNG_PROCESS -> threadExpert.analyzeThreadBehavior(issue);
            case IO_BOTTLENECK -> ioAnalyst.analyzeIO(issue);
            case RESOURCE_HOG -> analyzeResourceHog(issue);
            default -> analyzeWithBestAgent(issue);
        };
    }

    /**
     * Analyze resource hog - may involve multiple agents.
     */
    private AiAnalysisResult analyzeResourceHog(DiagnosticIssue issue) {
        // Resource hogs can be memory, CPU, or I/O related
        // Try each specialized agent and use the one with highest confidence

        AiAnalysisResult memoryAnalysis = leakDetector.analyzeMemoryLeak(issue);
        AiAnalysisResult threadAnalysis = threadExpert.analyzeThreadBehavior(issue);
        AiAnalysisResult ioAnalysis = ioAnalyst.analyzeIO(issue);

        AiAnalysisResult best = memoryAnalysis;
        if (threadAnalysis.getConfidence() > best.getConfidence()) {
            best = threadAnalysis;
        }
        if (ioAnalysis.getConfidence() > best.getConfidence()) {
            best = ioAnalysis;
        }

        return best;
    }

    /**
     * Try all agents and return the best analysis.
     */
    private AiAnalysisResult analyzeWithBestAgent(DiagnosticIssue issue) {
        AiAnalysisResult memoryAnalysis = leakDetector.analyzeMemoryLeak(issue);
        AiAnalysisResult threadAnalysis = threadExpert.analyzeThreadBehavior(issue);
        AiAnalysisResult ioAnalysis = ioAnalyst.analyzeIO(issue);

        // Return the analysis with highest confidence
        AiAnalysisResult best = memoryAnalysis;
        if (threadAnalysis.getConfidence() > best.getConfidence()) {
            best = threadAnalysis;
        }
        if (ioAnalysis.getConfidence() > best.getConfidence()) {
            best = ioAnalysis;
        }

        log.info("Best agent for unknown issue type: {} with confidence {}",
                best.getAgentName(), best.getConfidence());

        return best;
    }
}
