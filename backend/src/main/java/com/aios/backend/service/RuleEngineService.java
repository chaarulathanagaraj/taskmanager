package com.aios.backend.service;

import com.aios.backend.dto.IssueResolutionSummary;
import com.aios.backend.dto.BulkAutomationResult;
import com.aios.shared.client.AgentClient;
import com.aios.backend.model.ApprovalRequestEntity;
import com.aios.backend.model.IssueEntity;
import com.aios.backend.model.RuleExecutionEntity;
import com.aios.backend.repository.ApprovalRequestRepository;
import com.aios.backend.repository.IssueRepository;
import com.aios.backend.repository.RuleExecutionRepository;
import com.aios.shared.dto.AgentSettings;
import com.aios.shared.dto.ExecutionStatus;
import com.aios.shared.dto.RuleExecutionRequest;
import com.aios.shared.dto.RuleExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for executing automated remediation rules.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RuleEngineService {

    private static final Duration AUTO_REMEDIATION_LOCK_WINDOW = Duration.ofMinutes(10);

    private final RuleExecutionRepository executionRepository;
    private final ApprovalRequestRepository approvalRepository;
    private final IssueRepository issueRepository;
    private final ActionExecutorService actionExecutor;
    private final AgentClient agentClient;
    private final SafetyPolicyService safetyPolicy;
    private final RuleEvaluationService ruleEvaluationService;
    private final SettingsService settingsService;
    private final IssueService issueService;
    private final WebSocketBroadcaster broadcaster;
    private final ObjectMapper objectMapper;

    /**
     * Automate remediation for all active issues except protected processes.
     */
    @Transactional
    public BulkAutomationResult automateAllSafeActiveIssues() {
        List<IssueEntity> activeIssues = issueRepository.findByResolvedFalseOrderBySeverityDescDetectedAtDesc();
        AgentSettings settings = settingsService.getSettings();
        boolean autoRemediationEnabled = settings == null || settings.isAutoRemediation();

        BulkAutomationResult result = BulkAutomationResult.builder()
                .totalActive(activeIssues.size())
                .build();

        AtomicInteger automatedCount = new AtomicInteger(0);
        AtomicInteger resolvedCount = new AtomicInteger(0);
        AtomicInteger skippedProtectedCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        Queue<BulkAutomationResult.IssueAutomationOutcome> outcomes = new ConcurrentLinkedQueue<>();
        List<IssueEntity> automatableIssues = new ArrayList<>();

        for (IssueEntity issue : activeIssues) {
            boolean isProtected = issue.getProcessName() != null && safetyPolicy.isProtected(issue.getProcessName());
            if (isProtected) {
                skippedProtectedCount.incrementAndGet();
                outcomes.add(BulkAutomationResult.IssueAutomationOutcome.builder()
                        .issueId(issue.getId())
                        .processName(issue.getProcessName())
                        .affectedPid(issue.getAffectedPid())
                        .issueType(issue.getType() != null ? issue.getType().name() : "UNKNOWN")
                        .status("SKIPPED_PROTECTED")
                        .message("Protected process; resolve manually from Issues page")
                        .build());
                continue;
            }

            automatableIssues.add(issue);
        }

        if (!autoRemediationEnabled) {
            for (IssueEntity issue : automatableIssues) {
                failedCount.incrementAndGet();
                outcomes.add(BulkAutomationResult.IssueAutomationOutcome.builder()
                        .issueId(issue.getId())
                        .processName(issue.getProcessName())
                        .affectedPid(issue.getAffectedPid())
                        .issueType(issue.getType() != null ? issue.getType().name() : "UNKNOWN")
                        .status("FAILED")
                        .message("Auto-remediation is disabled in Settings")
                        .build());
            }

            result.setAutomated(automatedCount.get());
            result.setResolved(resolvedCount.get());
            result.setSkippedProtected(skippedProtectedCount.get());
            result.setFailed(failedCount.get());
            result.setOutcomes(new ArrayList<>(outcomes));
            return result;
        }

        int maxParallel = Math.max(1, Math.min(automatableIssues.size(), 6));
        ExecutorService executor = Executors.newFixedThreadPool(maxParallel);

        try {
            List<CompletableFuture<Void>> futures = automatableIssues.stream()
                    .map(issue -> CompletableFuture.runAsync(() -> {
                        try {
                            var evaluation = ruleEvaluationService.evaluate(issue);
                            String action = evaluation.getRecommendedAction().name();

                            RuleExecutionResult execution = requestExecution(RuleExecutionRequest.builder()
                                    .issueId(issue.getId())
                                    .actionType(action)
                                    .dryRun(false)
                                    .approvedBy("bulk-automation")
                                    .comment("Bulk automation from Issues page")
                                    .build());

                            if (execution.isSuccess()) {
                                automatedCount.incrementAndGet();
                                String status = execution.isDryRun() ? "SIMULATED" : "RESOLVED";
                                String message = execution.isDryRun()
                                        ? "Dry-run enabled in Settings; remediation simulated only"
                                        : "Automated remediation executed and issue marked resolved";

                                if (!execution.isDryRun()) {
                                    IssueResolutionSummary summary = issueService.resolveIssue(issue.getId());
                                    if (summary != null && Boolean.TRUE.equals(summary.getResolved())) {
                                        resolvedCount.incrementAndGet();
                                    }
                                }

                                outcomes.add(BulkAutomationResult.IssueAutomationOutcome.builder()
                                        .issueId(issue.getId())
                                        .processName(issue.getProcessName())
                                        .affectedPid(issue.getAffectedPid())
                                        .issueType(issue.getType() != null ? issue.getType().name() : "UNKNOWN")
                                        .action(action)
                                        .status(status)
                                        .message(message)
                                        .build());
                            } else {
                                failedCount.incrementAndGet();
                                outcomes.add(BulkAutomationResult.IssueAutomationOutcome.builder()
                                        .issueId(issue.getId())
                                        .processName(issue.getProcessName())
                                        .affectedPid(issue.getAffectedPid())
                                        .issueType(issue.getType() != null ? issue.getType().name() : "UNKNOWN")
                                        .action(action)
                                        .status("FAILED")
                                        .message(execution.getMessage())
                                        .build());
                            }
                        } catch (Exception e) {
                            failedCount.incrementAndGet();
                            outcomes.add(BulkAutomationResult.IssueAutomationOutcome.builder()
                                    .issueId(issue.getId())
                                    .processName(issue.getProcessName())
                                    .affectedPid(issue.getAffectedPid())
                                    .issueType(issue.getType() != null ? issue.getType().name() : "UNKNOWN")
                                    .status("ERROR")
                                    .message(e.getMessage())
                                    .build());
                            log.warn("Bulk automation failed for issue {}: {}", issue.getId(), e.getMessage());
                        }
                    }, executor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
        }

        result.setAutomated(automatedCount.get());
        result.setResolved(resolvedCount.get());
        result.setSkippedProtected(skippedProtectedCount.get());
        result.setFailed(failedCount.get());
        result.setOutcomes(new ArrayList<>(outcomes));

        return result;
    }

    /**
     * Request execution of a rule action.
     * Creates approval request for CRITICAL actions, executes immediately for
     * others.
     */
    @Transactional
    public RuleExecutionResult requestExecution(RuleExecutionRequest request) {
        log.info("Execution requested for issue {} with action {}",
                request.getIssueId(), request.getActionType());

        // Validate issue exists
        IssueEntity issue = issueRepository.findById(request.getIssueId())
                .orElseThrow(() -> new IllegalArgumentException("Issue not found: " + request.getIssueId()));

        if (Boolean.TRUE.equals(issue.getResolved())) {
            return RuleExecutionResult.builder()
                    .issueId(request.getIssueId())
                    .status(ExecutionStatus.CANCELLED)
                    .success(false)
                    .message("Issue already resolved. No further remediation is required.")
                    .build();
        }

        AgentSettings settings = settingsService.getSettings();
        boolean globalDryRun = settings != null && settings.isDryRunMode();
        boolean autoRemediationEnabled = settings == null || settings.isAutoRemediation();
        boolean manualApprovalContext = request.getApprovedBy() != null && !request.getApprovedBy().isBlank();
        boolean effectiveDryRun = request.isDryRun() || globalDryRun;

        if (globalDryRun && !request.isDryRun()) {
            log.info("Global dry-run is enabled; forcing simulation for issue {} action {}",
                    request.getIssueId(), request.getActionType());
        }

        if (!effectiveDryRun && !autoRemediationEnabled && !manualApprovalContext) {
            return RuleExecutionResult.builder()
                    .issueId(request.getIssueId())
                    .status(ExecutionStatus.CANCELLED)
                    .success(false)
                    .message("Auto-remediation is disabled in Settings. Use dry-run or approval workflow.")
                    .build();
        }

        // Prevent repeated remediation spam for same issue in lock window.
        if (!effectiveDryRun && issue.getLastRemediationAt() != null) {
            Duration sinceRemediation = Duration.between(issue.getLastRemediationAt(), Instant.now());
            if (sinceRemediation.compareTo(AUTO_REMEDIATION_LOCK_WINDOW) < 0) {
                long waitSeconds = AUTO_REMEDIATION_LOCK_WINDOW.minus(sinceRemediation).toSeconds();
                return RuleExecutionResult.builder()
                        .issueId(request.getIssueId())
                        .status(ExecutionStatus.CANCELLED)
                        .success(false)
                        .message("Remediation lock active. Retry in " + waitSeconds + " seconds")
                        .build();
            }
        }

        if (!effectiveDryRun) {
            var latestSameAction = executionRepository
                    .findFirstByIssueIdAndActionTypeOrderByCreatedAtDesc(request.getIssueId(), request.getActionType());
            if (latestSameAction.isPresent()) {
                Duration sinceLastExecution = Duration.between(latestSameAction.get().getCreatedAt(), Instant.now());
                if (sinceLastExecution.compareTo(AUTO_REMEDIATION_LOCK_WINDOW) < 0) {
                    return RuleExecutionResult.builder()
                            .issueId(request.getIssueId())
                            .status(ExecutionStatus.CANCELLED)
                            .success(false)
                            .message("Same remediation action recently executed. Locked for 10 minutes")
                            .build();
                }
            }
        }

        // Check safety policy
        if (!safetyPolicy.isSafe(issue, request.getActionType())) {
            log.warn("Action {} blocked by safety policy for issue {}",
                    request.getActionType(), request.getIssueId());
            return RuleExecutionResult.builder()
                    .issueId(request.getIssueId())
                    .status(ExecutionStatus.CANCELLED)
                    .success(false)
                    .message("Action blocked by safety policy")
                    .build();
        }

        // Create execution record
        RuleExecutionEntity execution = RuleExecutionEntity.builder()
                .issueId(request.getIssueId())
                .actionType(request.getActionType())
                .status(ExecutionStatus.PENDING)
                .dryRun(effectiveDryRun)
                .executedBy("system") // TODO: Get from security context
                .approvedBy(request.getApprovedBy())
                .build();

        execution = executionRepository.save(execution);

        // Determine if approval is required
        boolean needsApproval = (!autoRemediationEnabled && !effectiveDryRun)
                || requiresApproval(issue, request.getActionType());

        if (needsApproval && request.getApprovedBy() == null) {
            // Create approval request
            ApprovalRequestEntity approval = ApprovalRequestEntity.builder()
                    .executionId(execution.getId())
                    .issueId(request.getIssueId())
                    .actionType(request.getActionType())
                    .requestedBy("system")
                    .status(ExecutionStatus.PENDING)
                    .comment(request.getComment())
                    .build();

            approvalRepository.save(approval);

            log.info("Approval required for execution {}. Waiting for approval.", execution.getId());

            // Broadcast approval request
            broadcaster.broadcastApprovalRequest(approval.getId(), request.getIssueId(), request.getActionType());

            return convertToResult(execution);
        }

        // Execute immediately
        return executeAction(execution);
    }

    /**
     * Execute a rule action after approval or for non-critical actions.
     */
    @Transactional
    public RuleExecutionResult executeAction(RuleExecutionEntity execution) {
        log.info("Executing action {} for issue {}",
                execution.getActionType(), execution.getIssueId());

        Instant startTime = Instant.now();
        execution.setStatus(ExecutionStatus.EXECUTING);
        execution.setStartedAt(startTime);
        executionRepository.save(execution);

        // Broadcast execution started
        broadcaster.broadcastExecutionUpdate(execution.getId(), "EXECUTING", "Execution started");

        try {
            broadcaster.broadcastExecutionUpdate(execution.getId(), "EXECUTING",
                    "Validating and preparing remediation",
                    buildExecutionPlan(execution.getActionType()), 1, 4, null);

            // Execute the action
            Map<String, Object> result = actionExecutor.execute(
                    execution.getActionType(),
                    execution.getIssueId(),
                    execution.isDryRun());

            broadcaster.broadcastExecutionUpdate(execution.getId(), "EXECUTING",
                    "Agent returned a remediation result",
                    extractAgentSteps(result), 3, 4, null);

            Instant endTime = Instant.now();
            execution.setCompletedAt(endTime);
            execution.setDurationMs(endTime.toEpochMilli() - startTime.toEpochMilli());
            execution.setStatus(ExecutionStatus.COMPLETED);
            execution.setSuccess(true);
            execution.setMessage("Action executed successfully");
            execution.setExecutionDetails(objectMapper.writeValueAsString(result));

            if (!execution.isDryRun()) {
                issueRepository.findById(execution.getIssueId()).ifPresent(issue -> {
                    issue.setRemediationTaken(true);
                    issue.setLastRemediationAt(Instant.now());

                    ResolutionCheck resolutionCheck = verifyResolution(issue, execution.getActionType(), result);
                    if (resolutionCheck.resolved()) {
                        issue.markResolved();
                    } else {
                        issue.setLastUpdatedAt(Instant.now());
                    }

                    issueRepository.save(issue);

                    broadcaster.broadcastIssueResolved(buildResolutionSummary(issue, execution, result,
                            "AUTOMATED",
                            resolutionCheck.message(),
                            buildExecutionSteps(execution, result, resolutionCheck)));

                    broadcaster.broadcastExecutionUpdate(execution.getId(),
                            resolutionCheck.resolved() ? "COMPLETED" : "VERIFICATION_REQUIRED",
                            resolutionCheck.message(),
                            buildExecutionSteps(execution, result, resolutionCheck),
                            4, 4, resolutionCheck.message());
                });
            }

            executionRepository.save(execution);

            log.info("Execution {} completed successfully in {}ms",
                    execution.getId(), execution.getDurationMs());

            // Broadcast execution completed
            broadcaster.broadcastExecutionUpdate(execution.getId(), "COMPLETED", "Execution completed successfully");

            return convertToResult(execution);

        } catch (Exception e) {
            log.error("Execution {} failed: {}", execution.getId(), e.getMessage(), e);

            Instant endTime = Instant.now();
            execution.setCompletedAt(endTime);
            execution.setDurationMs(endTime.toEpochMilli() - startTime.toEpochMilli());
            execution.setStatus(ExecutionStatus.FAILED);
            execution.setSuccess(false);
            execution.setErrorMessage(e.getMessage());

            executionRepository.save(execution);

            // Broadcast execution failed
            broadcaster.broadcastExecutionUpdate(execution.getId(), "FAILED", "Execution failed: " + e.getMessage());

            // --- FALLBACK MECHANISM ---
            String fallbackAction = determineFallbackAction(execution.getActionType());
            if (fallbackAction != null) {
                log.info("Initiating fallback action {} for failed execution {}", fallbackAction, execution.getId());
                broadcaster.broadcastExecutionUpdate(execution.getId(), "FALLBACK",
                        "Initiating fallback: " + fallbackAction);

                try {
                    Map<String, Object> fallbackResult = actionExecutor.execute(
                            fallbackAction,
                            execution.getIssueId(),
                            execution.isDryRun());

                    log.info("Fallback action {} successful.", fallbackAction);
                    execution.setErrorMessage(
                            execution.getErrorMessage() + " | Fallback (" + fallbackAction + ") succeeded.");
                    executionRepository.save(execution);
                } catch (Exception fallbackEx) {
                    log.error("Fallback action {} also failed: {}", fallbackAction, fallbackEx.getMessage());
                    execution.setErrorMessage(execution.getErrorMessage() + " | Fallback (" + fallbackAction
                            + ") failed: " + fallbackEx.getMessage());
                    executionRepository.save(execution);
                }
            }
            // --------------------------

            return convertToResult(execution);
        }
    }

    private String determineFallbackAction(String primaryAction) {
        if (primaryAction == null)
            return null;
        return switch (primaryAction) {
            case "RESTART_PROCESS" -> "REDUCE_PRIORITY";
            case "KILL_PROCESS" -> "REDUCE_PRIORITY";
            case "TRIM_WORKING_SET" -> "KILL_PROCESS";
            default -> null;
        };
    }

    /**
     * Get execution history for an issue.
     */
    public List<RuleExecutionResult> getExecutionHistory(Long issueId) {
        return executionRepository.findByIssueId(issueId).stream()
                .map(this::convertToResult)
                .collect(Collectors.toList());
    }

    /**
     * Get recent executions.
     */
    public List<RuleExecutionResult> getRecentExecutions() {
        return executionRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .map(this::convertToResult)
                .collect(Collectors.toList());
    }

    /**
     * Determine if action requires approval.
     */
    private boolean requiresApproval(IssueEntity issue, String actionType) {
        // CRITICAL severity always requires approval
        if ("CRITICAL".equals(issue.getSeverity())) {
            return true;
        }

        // KILL_PROCESS requires approval for protected processes
        if ("KILL_PROCESS".equals(actionType)) {
            return safetyPolicy.isProtected(issue.getProcessName());
        }

        return false;
    }

    /**
     * Convert entity to DTO.
     */
    private RuleExecutionResult convertToResult(RuleExecutionEntity entity) {
        Map<String, Object> details = new HashMap<>();
        if (entity.getExecutionDetails() != null) {
            try {
                details = objectMapper.readValue(entity.getExecutionDetails(), Map.class);
            } catch (Exception e) {
                log.warn("Failed to parse execution details: {}", e.getMessage());
            }
        }

        return RuleExecutionResult.builder()
                .executionId(entity.getId())
                .issueId(entity.getIssueId())
                .status(entity.getStatus())
                .actionType(entity.getActionType())
                .dryRun(entity.isDryRun())
                .success(entity.getSuccess() != null && entity.getSuccess())
                .message(entity.getMessage())
                .executionDetails(details)
                .startedAt(entity.getStartedAt())
                .completedAt(entity.getCompletedAt())
                .durationMs(entity.getDurationMs())
                .executedBy(entity.getExecutedBy())
                .approvedBy(entity.getApprovedBy())
                .errorMessage(entity.getErrorMessage())
                .rollbackInfo(entity.getRollbackInfo())
                .build();
    }

    private IssueResolutionSummary buildResolutionSummary(IssueEntity issue, RuleExecutionEntity execution,
            Map<String, Object> actionResult, String source, String message, List<String> actionsTaken) {
        List<String> detailedActions = new java.util.ArrayList<>(actionsTaken);
        if (actionResult != null && actionResult.get("message") != null) {
            detailedActions.add("Action result: " + actionResult.get("message"));
        }
        return IssueResolutionSummary.builder()
                .issueId(issue.getId())
                .processName(issue.getProcessName())
                .affectedPid(issue.getAffectedPid())
                .issueType(issue.getType())
                .severity(issue.getSeverity())
                .status(issue.getStatus())
                .resolved(issue.getResolved())
                .remediationTaken(issue.getRemediationTaken())
                .source(source)
                .message(message)
                .resolvedAt(issue.getResolvedAt())
                .actionsTaken(detailedActions)
                .build();
    }

    private List<String> buildExecutionSteps(RuleExecutionEntity execution, Map<String, Object> actionResult,
            ResolutionCheck resolutionCheck) {
        List<String> steps = new java.util.ArrayList<>();
        steps.add("Validated safety policy before execution");
        steps.add("Created execution record #" + execution.getId());
        steps.add("Executed action " + execution.getActionType());

        List<String> agentSteps = extractAgentSteps(actionResult);
        if (!agentSteps.isEmpty()) {
            steps.addAll(agentSteps);
        }

        steps.add(resolutionCheck.resolved()
                ? "Verified the issue is resolved"
                : "Issue remains active and needs manual verification");
        return steps;
    }

    private List<String> buildExecutionPlan(String actionType) {
        List<String> steps = new java.util.ArrayList<>();
        steps.add("Validate safety policy and execution lock");
        steps.add("Capture current process snapshot");
        steps.add("Send remediation request to the agent");
        steps.add("Verify the post-action process state");
        return steps;
    }

    private List<String> extractAgentSteps(Map<String, Object> actionResult) {
        if (actionResult == null) {
            return List.of();
        }

        Object detailsObject = actionResult.get("details");
        if (!(detailsObject instanceof Map<?, ?> details)) {
            return List.of();
        }

        Object stepsObject = details.get("steps");
        if (stepsObject instanceof List<?> stepsList) {
            List<String> steps = new java.util.ArrayList<>();
            for (Object step : stepsList) {
                if (step != null) {
                    steps.add(String.valueOf(step));
                }
            }
            return steps;
        }

        Object resultObject = details.get("result");
        if (resultObject instanceof com.fasterxml.jackson.databind.JsonNode resultNode && resultNode.has("steps")) {
            List<String> steps = new java.util.ArrayList<>();
            for (var stepNode : resultNode.get("steps")) {
                steps.add(stepNode.asText());
            }
            return steps;
        }

        return List.of();
    }

    private ResolutionCheck verifyResolution(IssueEntity issue, String actionType, Map<String, Object> actionResult) {
        boolean dryRun = actionResult != null && Boolean.TRUE.equals(actionResult.get("dryRun"));
        if (dryRun) {
            return new ResolutionCheck(false, "Dry run completed. Manual verification is still required.");
        }

        if (issue == null) {
            return new ResolutionCheck(false, "Action executed, but the issue record could not be verified.");
        }

        return switch (actionType) {
            case "KILL_PROCESS" -> {
                Map<String, Object> processInfo = agentClient.getProcessInfo(issue.getAffectedPid());
                boolean processGone = processInfo == null || processInfo.containsKey("error")
                        || "unknown".equalsIgnoreCase(String.valueOf(processInfo.getOrDefault("name", "unknown")));
                yield processGone
                        ? new ResolutionCheck(true, "Process termination verified. The issue is resolved.")
                        : new ResolutionCheck(false,
                                "Process termination executed, but the process is still present. Manual verification is required.");
            }
            case "RESTART_PROCESS" -> new ResolutionCheck(false,
                    "Process restart completed. Confirm the new process is healthy and the memory leak no longer reproduces.");
            case "TRIM_WORKING_SET" -> new ResolutionCheck(false,
                    "Working set trim completed. Recheck the dashboard to confirm the memory leak trend is reduced or gone.");
            case "REDUCE_PRIORITY" -> new ResolutionCheck(false,
                    "Priority reduction completed. This mitigates load but does not prove the memory leak is resolved.");
            default -> new ResolutionCheck(false,
                    "Remediation executed successfully. Manual verification is required before marking the issue resolved.");
        };
    }

    private record ResolutionCheck(boolean resolved, String message) {
    }
}
