package com.aios.ai.agents;

import com.aios.ai.dto.RemediationPlan;
import com.aios.ai.dto.SafetyValidation;
import com.aios.shared.enums.ActionType;
import com.aios.shared.enums.SafetyLevel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * AI Agent that validates remediation plans against safety policies.
 * Ensures actions are safe to execute and identifies potential risks.
 */
@Service
@Slf4j
public class SafetyValidatorAgent {

    private final ChatLanguageModel model;

    // Protected system processes that should never be killed
    private static final Set<String> PROTECTED_PROCESSES = Set.of(
            "system", "smss.exe", "csrss.exe", "wininit.exe", "services.exe",
            "lsass.exe", "winlogon.exe", "svchost.exe", "dwm.exe", "explorer.exe",
            "system idle process", "registry", "memory compression");

    // Processes that require special approval
    private static final Set<String> SENSITIVE_PROCESSES = Set.of(
            "sqlservr.exe", "mysqld.exe", "postgres.exe", "mongod.exe",
            "nginx.exe", "httpd.exe", "iisexpress.exe", "w3wp.exe",
            "java.exe", "javaw.exe", "node.exe", "python.exe");

    public SafetyValidatorAgent(@Value("${gemini.api.key:}") String apiKey) {
        if (apiKey != null && !apiKey.isEmpty()) {
            this.model = GoogleAiGeminiChatModel.builder()
                    .apiKey(apiKey)
                    .modelName("gemini-1.5-pro")
                    .temperature(0.1)
                    .maxOutputTokens(1000)
                    .timeout(java.time.Duration.ofSeconds(30))
                    .build();
        } else {
            this.model = null;
            log.warn("Gemini API key not configured - SafetyValidatorAgent will use rule-based validation");
        }
    }

    /**
     * Validate a remediation plan for safety.
     */
    public SafetyValidation validate(RemediationPlan plan) {
        log.info("SafetyValidatorAgent validating plan for {} (PID {})",
                plan.getTargetProcessName(), plan.getTargetPid());

        List<SafetyValidation.SafetyViolation> violations = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        // Rule-based checks (always run)
        performRuleBasedChecks(plan, violations, warnings, recommendations);

        // AI-enhanced validation if available
        if (model != null && violations.stream().noneMatch(SafetyValidation.SafetyViolation::isBlocking)) {
            enhanceWithAI(plan, warnings, recommendations);
        }

        // Calculate safety score
        double safetyScore = calculateSafetyScore(plan, violations, warnings);

        // Determine if approval is required
        boolean approvalRequired = plan.isApprovalRequired() ||
                plan.getRiskLevel() == SafetyLevel.HIGH ||
                plan.getRiskLevel() == SafetyLevel.CRITICAL ||
                violations.stream().anyMatch(v -> "HIGH".equals(v.getSeverity())) ||
                !warnings.isEmpty();

        boolean isSafe = violations.stream().noneMatch(SafetyValidation.SafetyViolation::isBlocking);

        String explanation = buildExplanation(plan, violations, warnings, isSafe);

        return SafetyValidation.builder()
                .safe(isSafe)
                .approvalRequired(approvalRequired)
                .warnings(warnings)
                .violations(violations)
                .recommendations(recommendations)
                .safetyScore(safetyScore)
                .explanation(explanation)
                .build();
    }

    private void performRuleBasedChecks(RemediationPlan plan,
            List<SafetyValidation.SafetyViolation> violations,
            List<String> warnings,
            List<String> recommendations) {
        String processName = plan.getTargetProcessName().toLowerCase();

        // Check protected processes
        if (PROTECTED_PROCESSES.contains(processName)) {
            violations.add(SafetyValidation.SafetyViolation.builder()
                    .rule("PROTECTED_PROCESS")
                    .description("Cannot modify protected system process: " + plan.getTargetProcessName())
                    .severity("CRITICAL")
                    .blocking(true)
                    .build());
        }

        // Check sensitive processes
        if (SENSITIVE_PROCESSES.contains(processName)) {
            warnings.add("Target is a sensitive application process - data may be affected");
            recommendations.add("Consider graceful shutdown instead of forced termination");

            if (plan.getPrimaryAction() == ActionType.KILL_PROCESS) {
                violations.add(SafetyValidation.SafetyViolation.builder()
                        .rule("SENSITIVE_PROCESS_KILL")
                        .description("Killing sensitive process requires explicit approval")
                        .severity("HIGH")
                        .blocking(false)
                        .build());
            }
        }

        // Check high-risk actions
        if (plan.getPrimaryAction() == ActionType.KILL_PROCESS) {
            warnings.add("Kill action will terminate the process immediately");
            warnings.add("Unsaved data may be lost");
            recommendations.add("Consider using SUSPEND_PROCESS first to investigate");
        }

        // Check PID validity
        if (plan.getTargetPid() <= 4) {
            violations.add(SafetyValidation.SafetyViolation.builder()
                    .rule("INVALID_PID")
                    .description("Cannot target system PIDs (0-4)")
                    .severity("CRITICAL")
                    .blocking(true)
                    .build());
        }

        // Check for self-targeting (Java/AIOS processes)
        if (processName.contains("aios") || processName.equals("java.exe")) {
            warnings.add("Target may be part of the monitoring system");
            recommendations.add("Verify this is not the AIOS monitor process");
        }

        // Risk level warnings
        if (plan.getRiskLevel() == SafetyLevel.HIGH || plan.getRiskLevel() == SafetyLevel.CRITICAL) {
            warnings.add("High-risk action requested");
            recommendations.add("Consider less aggressive alternatives first");
        }
    }

    private void enhanceWithAI(RemediationPlan plan, List<String> warnings, List<String> recommendations) {
        String prompt = String.format("""
                Review this remediation plan for safety concerns:

                Target: %s (PID: %d)
                Action: %s
                Risk Level: %s
                Steps: %s
                Existing Warnings: %s

                Add any additional safety warnings or recommendations (respond briefly):
                ADDITIONAL_WARNINGS: <comma-separated list or NONE>
                ADDITIONAL_RECOMMENDATIONS: <comma-separated list or NONE>
                """,
                plan.getTargetProcessName(),
                plan.getTargetPid(),
                plan.getPrimaryAction(),
                plan.getRiskLevel(),
                plan.getSteps().stream().map(RemediationPlan.RemediationStep::getDescription).toList(),
                warnings);

        try {
            String response = model.generate(prompt);

            String additionalWarnings = extractField(response, "ADDITIONAL_WARNINGS:");
            if (!additionalWarnings.isEmpty() && !additionalWarnings.equalsIgnoreCase("NONE")) {
                for (String warning : additionalWarnings.split(",")) {
                    if (!warning.trim().isEmpty()) {
                        warnings.add(warning.trim());
                    }
                }
            }

            String additionalRecs = extractField(response, "ADDITIONAL_RECOMMENDATIONS:");
            if (!additionalRecs.isEmpty() && !additionalRecs.equalsIgnoreCase("NONE")) {
                for (String rec : additionalRecs.split(",")) {
                    if (!rec.trim().isEmpty()) {
                        recommendations.add(rec.trim());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("AI safety enhancement failed: {}", e.getMessage());
        }
    }

    private double calculateSafetyScore(RemediationPlan plan,
            List<SafetyValidation.SafetyViolation> violations,
            List<String> warnings) {
        double score = 1.0;

        // Deduct for violations
        for (SafetyValidation.SafetyViolation violation : violations) {
            if (violation.isBlocking()) {
                score -= 0.5;
            } else if ("HIGH".equals(violation.getSeverity())) {
                score -= 0.2;
            } else {
                score -= 0.1;
            }
        }

        // Deduct for warnings
        score -= warnings.size() * 0.05;

        // Deduct for risk level
        switch (plan.getRiskLevel()) {
            case CRITICAL -> score -= 0.3;
            case HIGH -> score -= 0.2;
            case MEDIUM -> score -= 0.1;
            case LOW -> {
            }
        }

        return Math.max(0.0, Math.min(1.0, score));
    }

    private String buildExplanation(RemediationPlan plan,
            List<SafetyValidation.SafetyViolation> violations,
            List<String> warnings,
            boolean isSafe) {
        StringBuilder sb = new StringBuilder();

        if (!isSafe) {
            sb.append("Plan BLOCKED: ");
            violations.stream()
                    .filter(SafetyValidation.SafetyViolation::isBlocking)
                    .findFirst()
                    .ifPresent(v -> sb.append(v.getDescription()));
        } else if (warnings.isEmpty() && violations.isEmpty()) {
            sb.append("Plan validated successfully. Low risk action on non-critical process.");
        } else {
            sb.append("Plan validated with ").append(warnings.size()).append(" warning(s) and ")
                    .append(violations.size()).append(" non-blocking issue(s). ");
            if (plan.isApprovalRequired()) {
                sb.append("Manual approval required.");
            }
        }

        return sb.toString();
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
}
