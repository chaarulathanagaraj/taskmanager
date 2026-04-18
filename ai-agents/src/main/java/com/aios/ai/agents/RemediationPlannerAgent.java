package com.aios.ai.agents;

import com.aios.ai.dto.AiAnalysisResult;
import com.aios.ai.dto.RemediationPlan;
import com.aios.shared.enums.ActionType;
import com.aios.shared.enums.SafetyLevel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AI Agent that creates safe remediation plans based on analysis results.
 * Generates step-by-step action plans with safety considerations.
 */
@Service
@Slf4j
public class RemediationPlannerAgent {

    private final ChatLanguageModel model;

    public RemediationPlannerAgent(@Value("${gemini.api.key:}") String apiKey) {
        if (apiKey != null && !apiKey.isEmpty()) {
            this.model = GoogleAiGeminiChatModel.builder()
                    .apiKey(apiKey)
                    .modelName("gemini-1.5-pro")
                    .temperature(0.2)
                    .maxOutputTokens(1500)
                    .timeout(java.time.Duration.ofSeconds(30))
                    .build();
        } else {
            this.model = null;
            log.warn("Gemini API key not configured - RemediationPlannerAgent will use rule-based planning");
        }
    }

    /**
     * Create a remediation plan based on AI analysis.
     */
    public RemediationPlan createPlan(AiAnalysisResult analysis, int targetPid, String processName) {
        log.info("RemediationPlannerAgent creating plan for PID {} based on {} recommendation",
                targetPid, analysis.getRecommendedAction());

        if (model != null && analysis.getConfidence() > 0.5) {
            return createPlanWithAI(analysis, targetPid, processName);
        } else {
            return createPlanWithRules(analysis, targetPid, processName);
        }
    }

    private RemediationPlan createPlanWithAI(AiAnalysisResult analysis, int targetPid, String processName) {
        String prompt = String.format("""
                You are a system remediation expert. Create a safe remediation plan.

                ## Analysis Results:
                - Root Cause: %s
                - Confidence: %.2f
                - Recommended Action: %s
                - Risk Assessment: %s
                - Reasoning: %s

                ## Target:
                - Process: %s (PID: %d)

                ## Requirements:
                Create a step-by-step remediation plan that:
                1. Minimizes risk of data loss
                2. Allows for rollback if possible
                3. Includes verification steps

                Respond with:
                PRIMARY_ACTION: <KILL_PROCESS|REDUCE_PRIORITY|TRIM_WORKING_SET|SUSPEND_PROCESS|RESTART_SERVICE>
                FALLBACK_ACTION: <action if primary fails>
                RISK_LEVEL: <LOW|MEDIUM|HIGH|CRITICAL>
                APPROVAL_REQUIRED: <true|false>
                STEPS:
                1. <step description>
                2. <step description>
                3. <step description>
                WARNINGS: <comma-separated list of warnings>
                EXPECTED_OUTCOME: <what should happen after remediation>
                ESTIMATED_TIME_SECONDS: <number>
                """,
                analysis.getRootCause(),
                analysis.getConfidence(),
                analysis.getRecommendedAction(),
                analysis.getRiskAssessment(),
                analysis.getReasoning(),
                processName,
                targetPid);

        try {
            String response = model.generate(prompt);
            return parseAiPlan(response, targetPid, processName);
        } catch (Exception e) {
            log.error("AI planning failed: {}", e.getMessage());
            return createPlanWithRules(analysis, targetPid, processName);
        }
    }

    private RemediationPlan parseAiPlan(String response, int targetPid, String processName) {
        try {
            ActionType primaryAction = parseActionType(extractField(response, "PRIMARY_ACTION:"));
            ActionType fallbackAction = parseActionType(extractField(response, "FALLBACK_ACTION:"));
            SafetyLevel riskLevel = parseSafetyLevel(extractField(response, "RISK_LEVEL:"));
            boolean approvalRequired = Boolean.parseBoolean(extractField(response, "APPROVAL_REQUIRED:").trim());
            String warnings = extractField(response, "WARNINGS:");
            String expectedOutcome = extractField(response, "EXPECTED_OUTCOME:");
            int estimatedTime = 30;
            try {
                estimatedTime = Integer.parseInt(extractField(response, "ESTIMATED_TIME_SECONDS:").trim());
            } catch (NumberFormatException ignored) {
            }

            // Parse steps
            List<RemediationPlan.RemediationStep> steps = parseSteps(response);

            return RemediationPlan.builder()
                    .primaryAction(primaryAction)
                    .targetPid(targetPid)
                    .targetProcessName(processName)
                    .steps(steps)
                    .expectedOutcome(expectedOutcome)
                    .riskLevel(riskLevel)
                    .approvalRequired(approvalRequired)
                    .warnings(List.of(warnings.split(",")))
                    .fallbackAction(fallbackAction)
                    .estimatedExecutionSeconds(estimatedTime)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse AI plan: {}", e.getMessage());
            return createDefaultPlan(targetPid, processName, ActionType.REDUCE_PRIORITY);
        }
    }

    private List<RemediationPlan.RemediationStep> parseSteps(String response) {
        List<RemediationPlan.RemediationStep> steps = new ArrayList<>();
        int stepsStart = response.indexOf("STEPS:");
        if (stepsStart == -1)
            return steps;

        String stepsSection = response.substring(stepsStart + 6);
        int stepsEnd = stepsSection.indexOf("\n\n");
        if (stepsEnd == -1)
            stepsEnd = Math.min(stepsSection.length(), 500);
        stepsSection = stepsSection.substring(0, stepsEnd);

        String[] lines = stepsSection.split("\n");
        int order = 1;
        for (String line : lines) {
            line = line.trim();
            if (line.matches("^\\d+\\..*")) {
                String description = line.replaceFirst("^\\d+\\.\\s*", "");
                steps.add(RemediationPlan.RemediationStep.builder()
                        .order(order++)
                        .description(description)
                        .optional(false)
                        .build());
            }
        }
        return steps;
    }

    private RemediationPlan createPlanWithRules(AiAnalysisResult analysis, int targetPid, String processName) {
        ActionType primaryAction = parseActionType(analysis.getRecommendedAction());

        List<RemediationPlan.RemediationStep> steps = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        SafetyLevel riskLevel;
        boolean approvalRequired;
        ActionType fallbackAction;
        String expectedOutcome;

        switch (primaryAction) {
            case KILL_PROCESS -> {
                riskLevel = SafetyLevel.HIGH;
                approvalRequired = true;
                fallbackAction = ActionType.SUSPEND_PROCESS;
                expectedOutcome = "Process terminated, resources freed";
                warnings.add("Process data may be lost");
                warnings.add("Dependent services may be affected");

                steps.add(step(1, "Verify process is not system-critical"));
                steps.add(step(2, "Log current process state"));
                steps.add(step(3, "Send termination signal"));
                steps.add(step(4, "Verify process terminated"));
                steps.add(step(5, "Monitor for automatic restart"));
            }
            case REDUCE_PRIORITY -> {
                riskLevel = SafetyLevel.LOW;
                approvalRequired = false;
                fallbackAction = ActionType.TRIM_WORKING_SET;
                expectedOutcome = "Process priority reduced, system responsiveness improved";

                steps.add(step(1, "Get current priority level"));
                steps.add(step(2, "Set priority to BELOW_NORMAL"));
                steps.add(step(3, "Verify priority change applied"));
            }
            case TRIM_WORKING_SET -> {
                riskLevel = SafetyLevel.LOW;
                approvalRequired = false;
                fallbackAction = ActionType.REDUCE_PRIORITY;
                expectedOutcome = "Working set trimmed, memory returned to system";

                steps.add(step(1, "Record current memory usage"));
                steps.add(step(2, "Trim working set"));
                steps.add(step(3, "Verify memory reduction"));
            }
            case SUSPEND_PROCESS -> {
                riskLevel = SafetyLevel.MEDIUM;
                approvalRequired = true;
                fallbackAction = ActionType.KILL_PROCESS;
                expectedOutcome = "Process suspended, can be resumed later";
                warnings.add("Process will stop responding");

                steps.add(step(1, "Verify process can be safely suspended"));
                steps.add(step(2, "Suspend all process threads"));
                steps.add(step(3, "Verify suspended state"));
            }
            default -> {
                riskLevel = SafetyLevel.LOW;
                approvalRequired = false;
                fallbackAction = null;
                expectedOutcome = "Continued monitoring";

                steps.add(step(1, "Log current state"));
                steps.add(step(2, "Schedule next check"));
            }
        }

        return RemediationPlan.builder()
                .primaryAction(primaryAction)
                .targetPid(targetPid)
                .targetProcessName(processName)
                .steps(steps)
                .expectedOutcome(expectedOutcome)
                .riskLevel(riskLevel)
                .approvalRequired(approvalRequired)
                .warnings(warnings)
                .fallbackAction(fallbackAction)
                .estimatedExecutionSeconds(30)
                .build();
    }

    private RemediationPlan.RemediationStep step(int order, String description) {
        return RemediationPlan.RemediationStep.builder()
                .order(order)
                .description(description)
                .optional(false)
                .build();
    }

    private RemediationPlan createDefaultPlan(int targetPid, String processName, ActionType action) {
        return RemediationPlan.builder()
                .primaryAction(action)
                .targetPid(targetPid)
                .targetProcessName(processName)
                .steps(List.of(step(1, "Execute primary action"), step(2, "Verify result")))
                .expectedOutcome("Issue addressed")
                .riskLevel(SafetyLevel.MEDIUM)
                .approvalRequired(true)
                .warnings(List.of("Default plan - manual verification recommended"))
                .estimatedExecutionSeconds(30)
                .build();
    }

    private String extractField(String response, String fieldName) {
        int startIdx = response.indexOf(fieldName);
        if (startIdx == -1)
            return "";
        startIdx += fieldName.length();
        int endIdx = response.indexOf("\n", startIdx);
        if (endIdx == -1)
            endIdx = response.length();
        return response.substring(startIdx, endIdx).trim();
    }

    private ActionType parseActionType(String action) {
        if (action == null || action.isEmpty())
            return ActionType.REDUCE_PRIORITY;
        try {
            return ActionType.valueOf(action.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            if (action.toUpperCase().contains("KILL"))
                return ActionType.KILL_PROCESS;
            if (action.toUpperCase().contains("PRIORITY"))
                return ActionType.REDUCE_PRIORITY;
            if (action.toUpperCase().contains("TRIM"))
                return ActionType.TRIM_WORKING_SET;
            if (action.toUpperCase().contains("SUSPEND"))
                return ActionType.SUSPEND_PROCESS;
            return ActionType.REDUCE_PRIORITY;
        }
    }

    private SafetyLevel parseSafetyLevel(String level) {
        if (level == null || level.isEmpty())
            return SafetyLevel.MEDIUM;
        try {
            return SafetyLevel.valueOf(level.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return SafetyLevel.MEDIUM;
        }
    }
}
