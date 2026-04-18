package com.aios.ai.service;

import com.aios.ai.dto.CompleteDiagnosisReport;
import com.aios.ai.dto.RemediationPlan;
import com.aios.ai.dto.RuleExecutionPreview;
import com.aios.shared.enums.ActionType;
import com.aios.shared.enums.SafetyLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates rule execution previews from diagnosis reports.
 * Shows users what will happen before automation executes.
 */
@Service
@Slf4j
public class RulePreviewService {

    /**
     * Generate a preview from a diagnosis report.
     *
     * @param report the diagnosis report
     * @return preview of what will be executed
     */
    public RuleExecutionPreview generatePreview(CompleteDiagnosisReport report) {
        log.info("Generating rule execution preview for issue {}", report.getIssueId());

        RemediationPlan plan = report.getRemediationPlan();
        if (plan == null) {
            throw new IllegalArgumentException("No remediation plan in diagnosis report");
        }

        return RuleExecutionPreview.builder()
                .primaryAction(plan.getPrimaryAction())
                .actionDescription(getActionDescription(plan.getPrimaryAction()))
                .target(buildTargetInfo(report))
                .riskLevel(plan.getRiskLevel())
                .confidence(report.getConfidence())
                .steps(convertSteps(plan.getSteps()))
                .estimatedSeconds(plan.getEstimatedExecutionSeconds())
                .warnings(convertWarnings(plan.getWarnings(), plan.getRiskLevel()))
                .expectedOutcome(plan.getExpectedOutcome())
                .fallbackAction(plan.getFallbackAction())
                .fallbackDescription(getFallbackDescription(plan.getFallbackAction()))
                .approvalRequired(plan.isApprovalRequired() || plan.getRiskLevel() == SafetyLevel.CRITICAL)
                .canRollback(canRollback(plan.getPrimaryAction()))
                .issueId(report.getIssueId())
                .build();
    }

    /**
     * Get human-readable action description
     */
    private String getActionDescription(ActionType action) {
        return switch (action) {
            case RESTART_PROCESS -> "Restart the process to clear memory leaks and reset state";
            case KILL_PROCESS -> "Terminate the process immediately to free system resources";
            case CLEAR_TEMP_FILES -> "Clear temporary files to reduce disk usage";
            case REDUCE_PRIORITY -> "Lower process priority to free resources for other tasks";
            case TRIM_WORKING_SET -> "Trim process memory to reduce RAM usage";
            case SUSPEND_PROCESS -> "Suspend the process temporarily to investigate the issue";
            case RESTART_SERVICE -> "Restart the Windows service to reset the application";
            case DISABLE_STARTUP_APP -> "Disable application from starting automatically";
            case NOTIFY_USER -> "Send notification to user about this issue";
            case SUGGEST_REBOOT -> "Suggest system reboot to resolve the issue";
        };
    }

    /**
     * Build target process information
     */
    private RuleExecutionPreview.TargetInfo buildTargetInfo(CompleteDiagnosisReport report) {
        return RuleExecutionPreview.TargetInfo.builder()
                .pid(report.getAnalyzedPid())
                .processName(report.getAnalyzedProcessName())
                .description(String.format("%s (PID: %d)",
                        report.getAnalyzedProcessName(),
                        report.getAnalyzedPid()))
                .memoryUsageMB(0L) // Will be populated from actual metrics
                .threadCount(0) // Will be populated from actual metrics
                .isCriticalProcess(isCriticalProcess(report.getAnalyzedProcessName()))
                .build();
    }

    /**
     * Convert remediation steps to preview steps
     */
    private List<RuleExecutionPreview.ExecutionStep> convertSteps(List<RemediationPlan.RemediationStep> planSteps) {
        if (planSteps == null || planSteps.isEmpty()) {
            return generateDefaultSteps();
        }

        List<RuleExecutionPreview.ExecutionStep> steps = new ArrayList<>();
        for (RemediationPlan.RemediationStep planStep : planSteps) {
            steps.add(RuleExecutionPreview.ExecutionStep.builder()
                    .order(planStep.getOrder())
                    .safetyLevel(inferSafetyLevel(planStep.getAction()))
                    .description(planStep.getDescription())
                    .details(planStep.getParameters())
                    .optional(planStep.isOptional())
                    .estimatedSeconds(estimateStepDuration(planStep.getAction()))
                    .build());
        }
        return steps;
    }

    /**
     * Generate default steps when none provided
     */
    private List<RuleExecutionPreview.ExecutionStep> generateDefaultSteps() {
        return List.of(
                RuleExecutionPreview.ExecutionStep.builder()
                        .order(1)
                        .safetyLevel(SafetyLevel.LOW)
                        .description("Analyze current process state")
                        .details("Read process metrics and validate target")
                        .optional(false)
                        .estimatedSeconds(2)
                        .build(),
                RuleExecutionPreview.ExecutionStep.builder()
                        .order(2)
                        .safetyLevel(SafetyLevel.MEDIUM)
                        .description("Execute primary action")
                        .details("Perform the remediation action")
                        .optional(false)
                        .estimatedSeconds(5)
                        .build(),
                RuleExecutionPreview.ExecutionStep.builder()
                        .order(3)
                        .safetyLevel(SafetyLevel.LOW)
                        .description("Verify outcome")
                        .details("Confirm action completed successfully")
                        .optional(false)
                        .estimatedSeconds(3)
                        .build());
    }

    /**
     * Convert warnings to preview warnings
     */
    private List<RuleExecutionPreview.Warning> convertWarnings(List<String> warnings, SafetyLevel riskLevel) {
        List<RuleExecutionPreview.Warning> previewWarnings = new ArrayList<>();

        // Add risk-based warnings
        if (riskLevel == SafetyLevel.CRITICAL) {
            previewWarnings.add(RuleExecutionPreview.Warning.builder()
                    .severity("CRITICAL")
                    .message("This is a high-risk operation that may cause data loss or system instability")
                    .mitigation("Create a system restore point before proceeding")
                    .build());
        }

        // Convert existing warnings
        if (warnings != null) {
            for (String warning : warnings) {
                previewWarnings.add(RuleExecutionPreview.Warning.builder()
                        .severity(inferWarningSeverity(warning))
                        .message(warning)
                        .mitigation(suggestMitigation(warning))
                        .build());
            }
        }

        // Add default warnings if none exist
        if (previewWarnings.isEmpty()) {
            previewWarnings.add(RuleExecutionPreview.Warning.builder()
                    .severity("INFO")
                    .message("Action will be logged in the audit trail")
                    .mitigation("Review execution logs after completion")
                    .build());
        }

        return previewWarnings;
    }

    /**
     * Get fallback action description
     */
    private String getFallbackDescription(ActionType fallbackAction) {
        if (fallbackAction == null) {
            return "No fallback action defined";
        }
        return "If primary action fails: " + getActionDescription(fallbackAction);
    }

    /**
     * Check if action can be rolled back
     */
    private boolean canRollback(ActionType action) {
        return switch (action) {
            case RESTART_PROCESS, RESTART_SERVICE -> true; // Can restart again
            case REDUCE_PRIORITY, TRIM_WORKING_SET -> true; // Can restore settings
            case CLEAR_TEMP_FILES -> false; // Files are deleted
            case KILL_PROCESS -> false; // Can't resurrect process
            case SUSPEND_PROCESS -> true; // Can resume
            case DISABLE_STARTUP_APP -> true; // Can re-enable
            case NOTIFY_USER, SUGGEST_REBOOT -> false; // No action taken
        };
    }

    /**
     * Check if process is critical to system
     */
    private boolean isCriticalProcess(String processName) {
        if (processName == null)
            return false;
        String name = processName.toLowerCase();
        return name.contains("system")
                || name.contains("csrss")
                || name.contains("smss")
                || name.contains("winlogon")
                || name.contains("services")
                || name.contains("lsass");
    }

    /**
     * Infer safety level from action type
     */
    private SafetyLevel inferSafetyLevel(ActionType action) {
        if (action == null) {
            return SafetyLevel.MEDIUM; // Default safety level for undefined actions
        }
        return switch (action) {
            case NOTIFY_USER, SUGGEST_REBOOT -> SafetyLevel.LOW;
            case REDUCE_PRIORITY, TRIM_WORKING_SET, CLEAR_TEMP_FILES, SUSPEND_PROCESS -> SafetyLevel.MEDIUM;
            case RESTART_PROCESS, DISABLE_STARTUP_APP -> SafetyLevel.HIGH;
            case KILL_PROCESS, RESTART_SERVICE -> SafetyLevel.CRITICAL;
        };
    }

    /**
     * Estimate duration for a step
     */
    private int estimateStepDuration(ActionType action) {
        if (action == null) {
            return 5; // Default duration
        }
        return switch (action) {
            case NOTIFY_USER, SUGGEST_REBOOT -> 1;
            case CLEAR_TEMP_FILES, REDUCE_PRIORITY, TRIM_WORKING_SET -> 3;
            case SUSPEND_PROCESS, DISABLE_STARTUP_APP -> 5;
            case RESTART_PROCESS, RESTART_SERVICE -> 10;
            case KILL_PROCESS -> 2;
        };
    }

    /**
     * Infer warning severity from message
     */
    private String inferWarningSeverity(String warning) {
        String lower = warning.toLowerCase();
        if (lower.contains("data loss") || lower.contains("crash") || lower.contains("critical")) {
            return "CRITICAL";
        }
        if (lower.contains("unsaved") || lower.contains("may fail") || lower.contains("depend")) {
            return "WARNING";
        }
        return "INFO";
    }

    /**
     * Suggest mitigation for a warning
     */
    private String suggestMitigation(String warning) {
        String lower = warning.toLowerCase();
        if (lower.contains("unsaved")) {
            return "Save your work before proceeding";
        }
        if (lower.contains("depend")) {
            return "Check for dependent processes in Task Manager";
        }
        if (lower.contains("restart")) {
            return "Ensure the process can be safely restarted";
        }
        return "Review the warning carefully before proceeding";
    }
}
