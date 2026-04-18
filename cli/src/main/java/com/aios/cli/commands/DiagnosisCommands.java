package com.aios.cli.commands;

import com.aios.ai.agents.*;
import com.aios.ai.dto.*;
import com.aios.ai.service.AiDiagnosticOrchestrator;
import com.aios.shared.dto.DiagnosticIssue;
import com.aios.shared.enums.ActionType;
import com.aios.shared.enums.IssueType;
import com.aios.shared.enums.SafetyLevel;
import com.aios.shared.enums.Severity;
import lombok.RequiredArgsConstructor;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.time.Instant;
import java.util.Map;

/**
 * Shell commands for testing AI diagnosis.
 */
@ShellComponent
@RequiredArgsConstructor
public class DiagnosisCommands {

    private final AiDiagnosticOrchestrator orchestrator;
    private final LeakDetectorAgent leakDetector;
    private final ThreadExpertAgent threadExpert;
    private final IOAnalystAgent ioAnalyst;
    private final RemediationPlannerAgent remediationPlanner;
    private final SafetyValidatorAgent safetyValidator;

    @ShellMethod(key = "diagnose", value = "Run full AI diagnosis on a simulated issue")
    public String diagnose(
            @ShellOption(help = "Process ID") int pid,
            @ShellOption(help = "Process name") String processName,
            @ShellOption(help = "Issue type (MEMORY_LEAK, THREAD_EXPLOSION, IO_BOTTLENECK, HUNG_PROCESS, RESOURCE_HOG)") String type,
            @ShellOption(help = "Severity (LOW, MEDIUM, HIGH, CRITICAL)", defaultValue = "HIGH") String severity,
            @ShellOption(help = "Initial confidence (0.0-1.0)", defaultValue = "0.5") double confidence) {

        DiagnosticIssue issue = createTestIssue(pid, processName, type, severity, confidence);

        long startTime = System.currentTimeMillis();
        CompleteDiagnosisReport report = orchestrator.diagnose(issue);
        long duration = System.currentTimeMillis() - startTime;

        return formatDiagnosisReport(report, duration);
    }

    @ShellMethod(key = "analyze-memory", value = "Run memory leak analysis on a process")
    public String analyzeMemory(
            @ShellOption(help = "Process ID") int pid,
            @ShellOption(help = "Process name") String processName) {

        DiagnosticIssue issue = createTestIssue(pid, processName, "MEMORY_LEAK", "HIGH", 0.5);
        AiAnalysisResult result = leakDetector.analyzeMemoryLeak(issue);
        return formatAnalysisResult("Memory Leak Analysis", result);
    }

    @ShellMethod(key = "analyze-threads", value = "Run thread behavior analysis on a process")
    public String analyzeThreads(
            @ShellOption(help = "Process ID") int pid,
            @ShellOption(help = "Process name") String processName) {

        DiagnosticIssue issue = createTestIssue(pid, processName, "THREAD_EXPLOSION", "HIGH", 0.5);
        AiAnalysisResult result = threadExpert.analyzeThreadBehavior(issue);
        return formatAnalysisResult("Thread Behavior Analysis", result);
    }

    @ShellMethod(key = "analyze-io", value = "Run I/O bottleneck analysis")
    public String analyzeIO(
            @ShellOption(help = "Process ID") int pid,
            @ShellOption(help = "Process name") String processName) {

        DiagnosticIssue issue = createTestIssue(pid, processName, "IO_BOTTLENECK", "MEDIUM", 0.5);
        AiAnalysisResult result = ioAnalyst.analyzeIO(issue);
        return formatAnalysisResult("I/O Bottleneck Analysis", result);
    }

    @ShellMethod(key = "plan-remediation", value = "Create remediation plan for an action")
    public String planRemediation(
            @ShellOption(help = "Action type (KILL_PROCESS, REDUCE_PRIORITY, TRIM_WORKING_SET, MONITOR)") String actionType,
            @ShellOption(help = "Target PID") int pid,
            @ShellOption(help = "Process name") String processName) {

        AiAnalysisResult analysis = AiAnalysisResult.builder()
                .rootCause("Test root cause for remediation planning")
                .confidence(0.85)
                .recommendedAction(actionType)
                .reasoning("CLI test request")
                .agentName("CLI")
                .build();

        RemediationPlan plan = remediationPlanner.createPlan(analysis, pid, processName);
        return formatRemediationPlan(plan);
    }

    @ShellMethod(key = "validate-safety", value = "Validate safety of a remediation plan")
    public String validateSafety(
            @ShellOption(help = "Action type (KILL_PROCESS, REDUCE_PRIORITY, etc.)") String actionType,
            @ShellOption(help = "Target PID") int pid,
            @ShellOption(help = "Process name") String processName) {

        RemediationPlan plan = RemediationPlan.builder()
                .primaryAction(ActionType.valueOf(actionType))
                .targetPid(pid)
                .targetProcessName(processName)
                .riskLevel(SafetyLevel.HIGH)
                .build();

        SafetyValidation validation = safetyValidator.validate(plan);
        return formatSafetyValidation(validation);
    }

    @ShellMethod(key = "test-scenario", value = "Run predefined test scenarios")
    public String testScenario(
            @ShellOption(help = "Scenario: memory-hog, thread-explosion, io-bound, critical-process") String scenario) {

        return switch (scenario.toLowerCase()) {
            case "memory-hog" -> runMemoryHogScenario();
            case "thread-explosion" -> runThreadExplosionScenario();
            case "io-bound" -> runIOBoundScenario();
            case "critical-process" -> runCriticalProcessScenario();
            default -> "Unknown scenario. Available: memory-hog, thread-explosion, io-bound, critical-process";
        };
    }

    private String runMemoryHogScenario() {
        DiagnosticIssue issue = DiagnosticIssue.builder()
                .id(1L)
                .type(IssueType.MEMORY_LEAK)
                .severity(Severity.HIGH)
                .confidence(0.45)
                .affectedPid(9999)
                .processName("test-memory-hog.exe")
                .details("Process consuming 4GB memory with continuous growth")
                .detectedAt(Instant.now())
                .build();

        CompleteDiagnosisReport report = orchestrator.diagnose(issue);
        return "=== Memory Hog Scenario ===\n\n" + formatDiagnosisReport(report, report.getProcessingTimeMs());
    }

    private String runThreadExplosionScenario() {
        DiagnosticIssue issue = DiagnosticIssue.builder()
                .id(2L)
                .type(IssueType.THREAD_EXPLOSION)
                .severity(Severity.CRITICAL)
                .confidence(0.35)
                .affectedPid(8888)
                .processName("runaway-service.exe")
                .details("Thread count increased from 50 to 2000 in 5 minutes")
                .detectedAt(Instant.now())
                .build();

        CompleteDiagnosisReport report = orchestrator.diagnose(issue);
        return "=== Thread Explosion Scenario ===\n\n" + formatDiagnosisReport(report, report.getProcessingTimeMs());
    }

    private String runIOBoundScenario() {
        DiagnosticIssue issue = DiagnosticIssue.builder()
                .id(3L)
                .type(IssueType.IO_BOTTLENECK)
                .severity(Severity.MEDIUM)
                .confidence(0.55)
                .affectedPid(7777)
                .processName("data-processor.exe")
                .details("Disk queue length consistently above 15")
                .detectedAt(Instant.now())
                .build();

        CompleteDiagnosisReport report = orchestrator.diagnose(issue);
        return "=== I/O Bound Scenario ===\n\n" + formatDiagnosisReport(report, report.getProcessingTimeMs());
    }

    private String runCriticalProcessScenario() {
        // Try to remediate a critical system process - should be blocked by safety
        RemediationPlan plan = RemediationPlan.builder()
                .primaryAction(ActionType.KILL_PROCESS)
                .targetPid(4)
                .targetProcessName("System")
                .riskLevel(SafetyLevel.CRITICAL)
                .build();

        SafetyValidation validation = safetyValidator.validate(plan);

        StringBuilder sb = new StringBuilder();
        sb.append("=== Critical Process Scenario ===\n\n");
        sb.append("Attempting to kill System process (PID 4)...\n\n");
        sb.append(formatSafetyValidation(validation));
        return sb.toString();
    }

    private DiagnosticIssue createTestIssue(int pid, String processName, String type, String severity,
            double confidence) {
        return DiagnosticIssue.builder()
                .id((long) (Math.random() * 10000))
                .type(IssueType.valueOf(type))
                .severity(Severity.valueOf(severity))
                .confidence(confidence)
                .affectedPid(pid)
                .processName(processName)
                .details("CLI test issue")
                .detectedAt(Instant.now())
                .build();
    }

    private String formatDiagnosisReport(CompleteDiagnosisReport report, long duration) {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════════╗\n");
        sb.append("║                    DIAGNOSIS REPORT                           ║\n");
        sb.append("╚══════════════════════════════════════════════════════════════╝\n\n");

        sb.append(String.format("Status: %s\n", report.isSuccess() ? "✓ SUCCESS" : "✗ FAILED"));
        sb.append(String.format("Message: %s\n", report.getMessage()));
        sb.append(String.format("Processing Time: %d ms\n", duration));
        sb.append(String.format("Overall Confidence: %.1f%%\n\n", report.getConfidence() * 100));

        if (report.getAnalysis() != null) {
            sb.append("─── Analysis ───────────────────────────────────────────────────\n");
            AiAnalysisResult analysis = report.getAnalysis();
            sb.append(String.format("Agent: %s\n", analysis.getAgentName()));
            sb.append(String.format("Root Cause: %s\n", analysis.getRootCause()));
            sb.append(String.format("Confidence: %.1f%%\n", analysis.getConfidence() * 100));
            sb.append(String.format("Recommended Action: %s\n", analysis.getRecommendedAction()));
            sb.append(String.format("Reasoning: %s\n\n", analysis.getReasoning()));
        }

        if (report.getRemediationPlan() != null) {
            sb.append("─── Remediation Plan ───────────────────────────────────────────\n");
            RemediationPlan plan = report.getRemediationPlan();
            sb.append(String.format("Primary Action: %s\n", plan.getPrimaryAction()));
            sb.append(String.format("Target: %s (PID: %d)\n", plan.getTargetProcessName(), plan.getTargetPid()));
            sb.append(String.format("Risk Level: %s\n", plan.getRiskLevel()));
            sb.append(String.format("Approval Required: %s\n", plan.isApprovalRequired() ? "YES" : "NO"));

            if (plan.getSteps() != null && !plan.getSteps().isEmpty()) {
                sb.append("\nSteps:\n");
                for (RemediationPlan.RemediationStep step : plan.getSteps()) {
                    sb.append(String.format("  %d. %s%s\n",
                            step.getOrder(),
                            step.getDescription(),
                            step.isOptional() ? " (optional)" : ""));
                }
            }

            if (plan.getWarnings() != null && !plan.getWarnings().isEmpty()) {
                sb.append("\nWarnings:\n");
                for (String warning : plan.getWarnings()) {
                    sb.append(String.format("  ⚠ %s\n", warning));
                }
            }
            sb.append("\n");
        }

        if (report.getSafetyValidation() != null) {
            sb.append("─── Safety Validation ──────────────────────────────────────────\n");
            SafetyValidation validation = report.getSafetyValidation();
            sb.append(String.format("Safe: %s\n", validation.isSafe() ? "✓ YES" : "✗ NO"));
            sb.append(String.format("Safety Score: %.1f%%\n", validation.getSafetyScore() * 100));
            sb.append(String.format("Approval Required: %s\n", validation.isApprovalRequired() ? "YES" : "NO"));

            if (validation.getViolations() != null && !validation.getViolations().isEmpty()) {
                sb.append("\nViolations:\n");
                for (SafetyValidation.SafetyViolation v : validation.getViolations()) {
                    sb.append(String.format("  ✗ [%s] %s: %s%s\n",
                            v.getSeverity(),
                            v.getRule(),
                            v.getDescription(),
                            v.isBlocking() ? " (BLOCKING)" : ""));
                }
            }
        }

        return sb.toString();
    }

    private String formatAnalysisResult(String title, AiAnalysisResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("═══ %s ═══\n\n", title));
        sb.append(String.format("Agent: %s\n", result.getAgentName()));
        sb.append(String.format("Root Cause: %s\n", result.getRootCause()));
        sb.append(String.format("Confidence: %.1f%%\n", result.getConfidence() * 100));
        sb.append(String.format("Recommended Action: %s\n", result.getRecommendedAction()));
        sb.append(String.format("Risk Assessment: %s\n", result.getRiskAssessment()));
        sb.append(String.format("\nReasoning:\n%s\n", result.getReasoning()));

        if (result.getEvidence() != null && !result.getEvidence().isEmpty()) {
            sb.append("\nEvidence:\n");
            for (Map.Entry<String, Object> entry : result.getEvidence().entrySet()) {
                sb.append(String.format("  • %s: %s\n", entry.getKey(), entry.getValue()));
            }
        }

        if (result.getAlternativeCauses() != null && !result.getAlternativeCauses().isEmpty()) {
            sb.append("\nAlternative Causes:\n");
            for (String alt : result.getAlternativeCauses()) {
                sb.append(String.format("  • %s\n", alt));
            }
        }

        return sb.toString();
    }

    private String formatRemediationPlan(RemediationPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("═══ Remediation Plan ═══\n\n");
        sb.append(String.format("Action: %s\n", plan.getPrimaryAction()));
        sb.append(String.format("Target: %s (PID: %d)\n", plan.getTargetProcessName(), plan.getTargetPid()));
        sb.append(String.format("Risk Level: %s\n", plan.getRiskLevel()));
        sb.append(String.format("Approval Required: %s\n\n", plan.isApprovalRequired() ? "YES" : "NO"));

        if (plan.getSteps() != null && !plan.getSteps().isEmpty()) {
            sb.append("Steps:\n");
            for (RemediationPlan.RemediationStep step : plan.getSteps()) {
                sb.append(String.format("  %d. [%s] %s\n",
                        step.getOrder(),
                        step.getAction(),
                        step.getDescription()));
            }
        }

        if (plan.getWarnings() != null && !plan.getWarnings().isEmpty()) {
            sb.append("\nWarnings:\n");
            for (String warning : plan.getWarnings()) {
                sb.append(String.format("  ⚠ %s\n", warning));
            }
        }

        if (plan.getFallbackAction() != null) {
            sb.append(String.format("\nFallback: %s\n", plan.getFallbackAction()));
        }

        return sb.toString();
    }

    private String formatSafetyValidation(SafetyValidation validation) {
        StringBuilder sb = new StringBuilder();
        sb.append("═══ Safety Validation ═══\n\n");
        sb.append(String.format("Safe: %s\n", validation.isSafe() ? "✓ YES" : "✗ NO"));
        sb.append(String.format("Safety Score: %.1f%%\n", validation.getSafetyScore() * 100));
        sb.append(String.format("Approval Required: %s\n", validation.isApprovalRequired() ? "YES" : "NO"));
        sb.append(String.format("\nExplanation: %s\n", validation.getExplanation()));

        if (validation.getViolations() != null && !validation.getViolations().isEmpty()) {
            sb.append("\nViolations:\n");
            for (SafetyValidation.SafetyViolation v : validation.getViolations()) {
                sb.append(String.format("  [%s] %s: %s\n",
                        v.isBlocking() ? "BLOCKING" : "WARNING",
                        v.getRule(),
                        v.getDescription()));
            }
        }

        if (validation.getWarnings() != null && !validation.getWarnings().isEmpty()) {
            sb.append("\nWarnings:\n");
            for (String warning : validation.getWarnings()) {
                sb.append(String.format("  ⚠ %s\n", warning));
            }
        }

        return sb.toString();
    }
}
