package com.aios.backend.service;

import com.aios.backend.dto.RuleEvaluationDto;
import com.aios.backend.dto.RuleMatchDto;
import com.aios.backend.model.IssueEntity;
import com.aios.shared.dto.AgentSettings;
import com.aios.shared.dto.PolicyViolation;
import com.aios.shared.enums.ActionType;
import com.aios.shared.enums.Severity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic, backend-side rule evaluation for issue triage and remediation
 * planning.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RuleEvaluationService {

    private static final Duration PERSISTENCE_SIGNAL = Duration.ofMinutes(10);

    private final SafetyPolicyService safetyPolicyService;
    private final SettingsService settingsService;

    /**
     * Evaluate one issue against deterministic backend rules.
     */
    public RuleEvaluationDto evaluate(IssueEntity issue) {
        if (issue == null) {
            throw new IllegalArgumentException("Issue cannot be null");
        }

        List<RuleMatchDto> matches = new ArrayList<>();
        String details = issue.getDetails() == null ? "" : issue.getDetails().toLowerCase(Locale.ROOT);
        Duration age = issue.getDetectedAt() == null
                ? Duration.ZERO
                : Duration.between(issue.getDetectedAt(), Instant.now());

        ActionType recommendedAction = recommendAction(issue, details, age, matches);

        boolean isProtected = issue.getProcessName() != null
                && safetyPolicyService.isProtected(issue.getProcessName(), issue.getAffectedPid());

        PolicyViolation policy = safetyPolicyService.checkPolicy(
                recommendedAction,
                issue.getProcessName(),
                issue.getAffectedPid(),
                false,
                safeConfidence(issue.getConfidence()));

        boolean blocked = policy.isViolated() && policy.isBlocking();
        boolean requiresApproval = policy.isViolated() && policy.isOverridable();

        double evaluationConfidence = deriveEvaluationConfidence(issue, matches);
        AgentSettings settings = settingsService.getSettings();
        double threshold = settings.getConfidenceThreshold();
        boolean autoRemediationEnabled = settings.isAutoRemediation();
        boolean autoEligible = autoRemediationEnabled
                && !blocked
                && !requiresApproval
                && !isProtected
                && evaluationConfidence >= threshold;

        return RuleEvaluationDto.builder()
                .issueId(issue.getId())
                .issueType(issue.getType())
                .severity(issue.getSeverity())
                .confidence(evaluationConfidence)
                .matchedRules(matches)
                .recommendedAction(recommendedAction)
                .recommendationReason(buildReason(matches, issue))
                .protectedProcess(isProtected)
                .policyBlocked(blocked)
                .requiresApproval(requiresApproval)
                .autoRemediationEligible(autoEligible)
                .policyReason(policy.isViolated() ? policy.getReason() : null)
                .build();
    }

    /**
     * Evaluate all currently active issues.
     */
    public List<RuleEvaluationDto> evaluateAll(List<IssueEntity> issues) {
        return issues.stream().map(this::evaluate).toList();
    }

    private ActionType recommendAction(IssueEntity issue,
            String details,
            Duration age,
            List<RuleMatchDto> matches) {
        if (issue.getType() == null) {
            matches.add(match("RULE_UNKNOWN_TYPE", "Unknown Type Fallback", "Issue type is absent", 0.3));
            return ActionType.NOTIFY_USER;
        }

        switch (issue.getType()) {
            case MEMORY_LEAK:
                matches.add(match("MEM_LEAK_BASE", "Memory Leak Signature", "Memory leak pattern detected", 0.72));
                if (isNonRestartFriendlyProcess(issue)) {
                    matches.add(match("MEM_LEAK_SAFER", "Safe Memory Leak Remediation",
                            "Process looks service/system-like, so restart is deferred in favor of a safer action",
                            0.87));
                    return ActionType.TRIM_WORKING_SET;
                }
                if (age.compareTo(PERSISTENCE_SIGNAL) >= 0) {
                    matches.add(match("MEM_LEAK_PERSIST", "Leak Persistence", "Leak persisted over 10 minutes", 0.88));
                }
                if (hasAny(details, "heap", "outofmemory", "allocation")) {
                    matches.add(match("MEM_HEAP_PRESSURE", "Heap Pressure", "Details indicate heap pressure", 0.82));
                }
                return age.compareTo(PERSISTENCE_SIGNAL) >= 0
                        ? ActionType.RESTART_PROCESS
                        : ActionType.TRIM_WORKING_SET;

            case THREAD_EXPLOSION:
                matches.add(match("THREAD_BASE", "Thread Count Spike", "Thread explosion signature detected", 0.74));
                if (issue.getSeverity() == Severity.CRITICAL) {
                    matches.add(match("THREAD_CRITICAL", "Critical Thread Pressure", "Thread issue is critical", 0.9));
                }
                return issue.getSeverity() == Severity.CRITICAL
                        ? ActionType.SUSPEND_PROCESS
                        : ActionType.REDUCE_PRIORITY;

            case HUNG_PROCESS:
                matches.add(match("HUNG_BASE", "Hung Process Signature", "Process appears non-responsive", 0.8));
                if (hasAny(details, "timeout", "not responding", "deadlock")) {
                    matches.add(match("HUNG_DEEP", "Hung Deep Evidence", "Timeout/deadlock evidence found", 0.91));
                }
                return safeConfidence(issue.getConfidence()) >= 0.9
                        ? ActionType.KILL_PROCESS
                        : ActionType.RESTART_PROCESS;

            case IO_BOTTLENECK:
                matches.add(match("IO_BASE", "I/O Saturation", "I/O bottleneck pattern detected", 0.7));
                if (hasAny(details, "disk full", "temp", "cache")) {
                    matches.add(
                            match("IO_STORAGE", "Storage Pressure", "Storage-related I/O pressure identified", 0.81));
                    return ActionType.CLEAR_TEMP_FILES;
                }
                return ActionType.REDUCE_PRIORITY;

            case RESOURCE_HOG:
                matches.add(match("HOG_BASE", "Resource Hog Pattern", "Sustained resource hog behavior", 0.69));
                if (hasAny(details, "cpu", "90%", "95%", "sustained")) {
                    matches.add(match("HOG_CPU", "CPU Saturation", "Sustained CPU pressure detected", 0.83));
                }
                return ActionType.REDUCE_PRIORITY;

            case UNKNOWN:
            default:
                matches.add(match("UNKNOWN_BASE", "Unknown Issue Type", "Fallback to safe recommendation", 0.4));
                return ActionType.NOTIFY_USER;
        }
    }

    private boolean isNonRestartFriendlyProcess(IssueEntity issue) {
        if (issue == null || issue.getProcessName() == null) {
            return false;
        }

        String normalizedName = issue.getProcessName().trim().toLowerCase(Locale.ROOT).replace(".exe", "");

        if (safetyPolicyService.isProtected(issue.getProcessName(), issue.getAffectedPid())) {
            return true;
        }

        List<String> restartAvoidPatterns = List.of(
                "wmi",
                "msmpeng",
                "searchindexer",
                "mscorsvw",
                "svchost",
                "pcconnectionservice",
                "instanttransfer",
                "onedrive",
                "wmiapsrv");

        for (String pattern : restartAvoidPatterns) {
            if (normalizedName.contains(pattern)) {
                return true;
            }
        }

        return normalizedName.startsWith("system")
                || normalizedName.equals("explorer")
                || normalizedName.equals("services")
                || normalizedName.equals("lsass")
                || normalizedName.equals("winlogon")
                || normalizedName.equals("wininit");
    }

    private RuleMatchDto match(String id, String name, String rationale, double score) {
        return RuleMatchDto.builder()
                .ruleId(id)
                .ruleName(name)
                .rationale(rationale)
                .score(score)
                .build();
    }

    private String buildReason(List<RuleMatchDto> matches, IssueEntity issue) {
        if (matches.isEmpty()) {
            return "No deterministic rule matched, using safe fallback action";
        }
        return "Recommended action based on " + matches.size() + " rule match(es) for " + issue.getType();
    }

    private boolean hasAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private double deriveEvaluationConfidence(IssueEntity issue, List<RuleMatchDto> matches) {
        double detectorConfidence = safeConfidence(issue.getConfidence());
        double ruleSupport = matches.stream().mapToDouble(RuleMatchDto::getScore).average().orElse(0.0);
        double weighted = (detectorConfidence * 0.65) + (ruleSupport * 0.35);
        return Math.max(0.0, Math.min(0.99, weighted));
    }

    private double safeConfidence(Double value) {
        if (value == null) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
