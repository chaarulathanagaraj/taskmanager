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
    private final ProcessClassifier processClassifier;
    private final ActionSafetyMatrix actionSafetyMatrix;

    /**
     * Automate remediation for all active issues with intelligent classification.
     * Classifies processes and skips those that can't be safely automated.
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
        AtomicInteger needsManualCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        Queue<BulkAutomationResult.IssueAutomationOutcome> outcomes = new ConcurrentLinkedQueue<>();
        List<IssueEntity> automatableIssues = new ArrayList<>();

        // Classify each issue and determine automation eligibility
        for (IssueEntity issue : activeIssues) {
            ProcessClassifier.ProcessClass classification = processClassifier.classify(issue);

            // Skip system-critical and temp processes entirely
            if (classification == ProcessClassifier.ProcessClass.SYSTEM_CRITICAL) {
                skippedProtectedCount.incrementAndGet();
                outcomes.add(BulkAutomationResult.IssueAutomationOutcome.builder()
                        .issueId(issue.getId())
                        .processName(issue.getProcessName())
                        .affectedPid(issue.getAffectedPid())
                        .issueType(issue.getType() != null ? issue.getType().name() : "UNKNOWN")
                        .status("SKIPPED_PROTECTED")
                        .message("System-critical process: " + processClassifier.getDescription(classification))
                        .build());
                continue;
            }

            if (classification == ProcessClassifier.ProcessClass.TEMP_PROCESS) {
                skippedProtectedCount.incrementAndGet();
                outcomes.add(BulkAutomationResult.IssueAutomationOutcome.builder()
                        .issueId(issue.getId())
                        .processName(issue.getProcessName())
                        .affectedPid(issue.getAffectedPid())
                        .issueType(issue.getType() != null ? issue.getType().name() : "UNKNOWN")
                        .status("SKIPPED_PROTECTED")
                        .message("Ephemeral process: likely already terminated")
                        .build());
                continue;
            }

            // Check user-configured protection
            boolean isProtected = issue.getProcessName() != null
                    && safetyPolicy.isProtected(issue.getProcessName(), issue.getAffectedPid());
            if (isProtected) {
                skippedProtectedCount.incrementAndGet();
                outcomes.add(BulkAutomationResult.IssueAutomationOutcome.builder()
                        .issueId(issue.getId())
                        .processName(issue.getProcessName())
                        .affectedPid(issue.getAffectedPid())
                        .issueType(issue.getType() != null ? issue.getType().name() : "UNKNOWN")
                        .status("SKIPPED_PROTECTED")
                        .message("User-protected process; resolve manually from Issues page")
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
            result.setNeedsManualReview(needsManualCount.get());
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
                            ProcessClassifier.ProcessClass classification = processClassifier.classify(issue);
                            var evaluation = ruleEvaluationService.evaluate(issue);
                            String recommendedAction = evaluation.getRecommendedAction().name();

                            // Check if recommended action is safe for this process class
                            if (!actionSafetyMatrix.isActionSafeFor(recommendedAction, classification)) {
                                ActionSafetyMatrix.ActionRecommendation safer = actionSafetyMatrix
                                        .getRecommendedActions(
                                                issue.getType() != null ? issue.getType().name() : "UNKNOWN",
                                                classification);

                                if (safer.primaryAction() == null) {
                                    needsManualCount.incrementAndGet();
                                    outcomes.add(BulkAutomationResult.IssueAutomationOutcome.builder()
                                            .issueId(issue.getId())
                                            .processName(issue.getProcessName())
                                            .affectedPid(issue.getAffectedPid())
                                            .issueType(issue.getType() != null ? issue.getType().name() : "UNKNOWN")
                                            .action(recommendedAction)
                                            .status("NEEDS_MANUAL_REVIEW")
                                            .message(safer.rationale())
                                            .build());
                                    return;
                                }

                                recommendedAction = safer.primaryAction();
                            }

                            RuleExecutionResult execution = requestExecution(RuleExecutionRequest.builder()
                                    .issueId(issue.getId())
                                    .actionType(recommendedAction)
                                    .dryRun(false)
                                    .approvedBy("bulk-automation")
                                    .comment("Bulk automation from Issues page")
                                    .build());

                            if (execution.isSuccess()) {
                                automatedCount.incrementAndGet();
                                String status = execution.isDryRun() ? "SIMULATED" : execution.getStatus().name();
                                String message = execution.isDryRun()
                                        ? "Dry-run enabled in Settings; remediation simulated only"
                                        : "Automation executed.";

                                if (!execution.isDryRun()) {
                                    IssueResolutionSummary summary = issueService.resolveIssue(issue.getId());
                                    if (summary != null && Boolean.TRUE.equals(summary.getResolved())) {
                                        resolvedCount.incrementAndGet();
                                        status = "RESOLVED";
                                        message = "Issue resolved via automated remediation";
                                    } else if (execution.getMessage() != null && !execution.getMessage().isBlank()) {
                                        status = "AUTOMATED";
                                        message = execution.getMessage();
                                    }
                                }

                                outcomes.add(BulkAutomationResult.IssueAutomationOutcome.builder()
                                        .issueId(issue.getId())
                                        .processName(issue.getProcessName())
                                        .affectedPid(issue.getAffectedPid())
                                        .issueType(issue.getType() != null ? issue.getType().name() : "UNKNOWN")
                                        .action(recommendedAction)
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
                                        .action(recommendedAction)
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
                                    .status("FAILED")
                                    .message("Automation error: " + e.getMessage())
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
        result.setNeedsManualReview(needsManualCount.get());
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
                String alternateAction = chooseAdaptiveAction(issue, request.getActionType());
                if (alternateAction != null && !alternateAction.equals(request.getActionType())) {
                    log.info("Recent remediation lock for {}, switching from {} to {}",
                            issue.getProcessName(), request.getActionType(), alternateAction);
                    request.setActionType(alternateAction);
                }
            }
        }

        if (!effectiveDryRun) {
            var latestSameAction = executionRepository
                    .findFirstByIssueIdAndActionTypeOrderByCreatedAtDesc(request.getIssueId(), request.getActionType());
            if (latestSameAction.isPresent()) {
                Duration sinceLastExecution = Duration.between(latestSameAction.get().getCreatedAt(), Instant.now());
                if (sinceLastExecution.compareTo(AUTO_REMEDIATION_LOCK_WINDOW) < 0) {
                    String alternateAction = chooseAdaptiveAction(issue, request.getActionType());
                    if (alternateAction != null && !alternateAction.equals(request.getActionType())) {
                        log.info("Cooldown active for {}, switching remediation from {} to {}",
                                issue.getProcessName(), request.getActionType(), alternateAction);
                        request.setActionType(alternateAction);
                    }
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
            IssueEntity issue = issueRepository.findById(execution.getIssueId())
                    .orElseThrow(() -> new IllegalArgumentException("Issue not found: " + execution.getIssueId()));

            List<String> actionPlan = buildAdaptiveActionPlan(issue, execution.getActionType());
            Map<String, Object> result = new HashMap<>();
            result.put("actionPlan", actionPlan);
            result.put("attemptedActions", new ArrayList<String>());
            result.put("dryRun", execution.isDryRun());

            broadcaster.broadcastExecutionUpdate(execution.getId(), "EXECUTING",
                    "Validating and preparing remediation",
                    buildExecutionPlan(actionPlan), 1, 4, null);

            String finalAction = execution.getActionType();
            String finalMessage = null;
            boolean resolutionVerified = false;

            for (int index = 0; index < actionPlan.size(); index++) {
                String candidateAction = actionPlan.get(index);
                @SuppressWarnings("unchecked")
                List<String> attemptedActions = (List<String>) result.get("attemptedActions");
                attemptedActions.add(candidateAction);

                result.put("lastAttemptedAction", candidateAction);
                broadcaster.broadcastExecutionUpdate(execution.getId(), "EXECUTING",
                        "Attempting remediation action: " + candidateAction,
                        List.of("Trying " + candidateAction + " for issue " + execution.getIssueId()),
                        2, 4, null);

                Map<String, Object> actionResult;
                try {
                    actionResult = actionExecutor.execute(
                            candidateAction,
                            execution.getIssueId(),
                            execution.isDryRun());
                } catch (Exception actionEx) {
                    actionResult = new HashMap<>();
                    actionResult.put("success", false);
                    actionResult.put("message", actionEx.getMessage());
                    actionResult.put("error", actionEx.getMessage());
                }

                result.put("lastActionResult", actionResult);

                if (Boolean.TRUE.equals(actionResult.get("success"))) {
                    broadcaster.broadcastExecutionUpdate(execution.getId(), "EXECUTING",
                            "Agent returned a remediation result",
                            extractAgentSteps(actionResult), 3, 4, null);

                    if (execution.isDryRun()) {
                        finalAction = candidateAction;
                        finalMessage = String.valueOf(actionResult.getOrDefault("message",
                                "Dry run completed. No system change was applied."));
                        resolutionVerified = false;
                        break;
                    }

                    ResolutionCheck resolutionCheck = verifyResolution(issue, candidateAction, actionResult);
                    result.put("resolutionVerified", resolutionCheck.resolved());
                    result.put("verificationMessage", resolutionCheck.message());
                    finalAction = candidateAction;
                    finalMessage = resolutionCheck.message();
                    resolutionVerified = resolutionCheck.resolved();

                    if (resolutionVerified) {
                        issue.setRemediationTaken(true);
                        issue.setLastRemediationAt(Instant.now());
                        issue.markResolved();
                        issueRepository.save(issue);
                        break;
                    }

                    if (index < actionPlan.size() - 1) {
                        continue;
                    }
                } else {
                    finalMessage = String.valueOf(actionResult.getOrDefault("message",
                            actionResult.getOrDefault("error", "Action did not complete successfully")));
                    if (index < actionPlan.size() - 1) {
                        continue;
                    }
                }
            }

            Instant endTime = Instant.now();
            execution.setCompletedAt(endTime);
            execution.setDurationMs(endTime.toEpochMilli() - startTime.toEpochMilli());

            if (execution.isDryRun()) {
                execution.setStatus(ExecutionStatus.COMPLETED);
                execution.setSuccess(true);
                execution.setActionType(finalAction);
                execution.setMessage(finalMessage != null
                        ? finalMessage
                        : "Dry run completed. No system change was applied.");
                result.put("resolutionVerified", Boolean.FALSE);
                result.put("verificationMessage", execution.getMessage());
            } else if (resolutionVerified) {
                execution.setStatus(ExecutionStatus.COMPLETED);
                execution.setSuccess(true);
                execution.setActionType(finalAction);
                execution.setMessage(finalMessage);
                broadcaster.broadcastIssueResolved(buildResolutionSummary(issue, execution, result,
                        "AUTOMATED",
                        finalMessage,
                        buildExecutionSteps(execution, result, new ResolutionCheck(true, finalMessage))));
            } else {
                issue.setLastUpdatedAt(Instant.now());
                issueRepository.save(issue);

                Map<String, Object> failureDiagnostics;
                try {
                    failureDiagnostics = agentClient.diagnoseResolutionFailure(
                            finalAction,
                            issue.getAffectedPid(),
                            issue.getProcessName(),
                            finalMessage);
                } catch (NoSuchMethodError linkageError) {
                    log.warn("AgentClient diagnoseResolutionFailure is unavailable at runtime. Falling back.",
                            linkageError);
                    failureDiagnostics = Map.of(
                            "failureCategory", "unknown_process_state",
                            "explanation", finalMessage,
                            "retryable", Boolean.TRUE,
                            "source", "fallback-runtime",
                            "actionType", finalAction,
                            "pid", issue.getAffectedPid(),
                            "processName", issue.getProcessName());
                }
                result.put("failureDiagnostics", failureDiagnostics);
                result.put("resolutionVerified", Boolean.FALSE);
                result.put("verificationMessage", finalMessage);

                // Keep execution in a compatible terminal state while signaling
                // follow-up via message/details.
                execution.setStatus(ExecutionStatus.COMPLETED);
                execution.setSuccess(true);
                execution.setActionType(finalAction);
                execution.setMessage(finalMessage != null
                        ? finalMessage
                        : "Remediation executed, but the issue still needs attention.");

                broadcaster.broadcastExecutionUpdate(execution.getId(), "NEEDS_ATTENTION",
                        execution.getMessage(),
                        buildExecutionSteps(execution, result, new ResolutionCheck(false, execution.getMessage())),
                        4, 4, execution.getMessage());
            }

            execution.setExecutionDetails(objectMapper.writeValueAsString(result));
            executionRepository.save(execution);

            log.info("Execution {} completed in {}ms with status {}",
                    execution.getId(), execution.getDurationMs(), execution.getStatus());

            broadcaster.broadcastExecutionUpdate(execution.getId(), execution.getStatus().name(),
                    execution.getMessage());

            return convertToResult(execution);

        } catch (Exception e) {
            log.error("Execution {} failed: {}", execution.getId(), e.getMessage(), e);

            Instant endTime = Instant.now();
            execution.setCompletedAt(endTime);
            execution.setDurationMs(endTime.toEpochMilli() - startTime.toEpochMilli());
            execution.setStatus(ExecutionStatus.FAILED);
            execution.setSuccess(false);
            execution.setErrorMessage(e.getMessage());
            execution.setMessage(e.getMessage());

            issueRepository.findById(execution.getIssueId()).ifPresent(issue -> {
                Map<String, Object> failureDiagnostics;
                try {
                    failureDiagnostics = agentClient.diagnoseResolutionFailure(
                            execution.getActionType(),
                            issue.getAffectedPid(),
                            issue.getProcessName(),
                            e.getMessage());
                } catch (NoSuchMethodError linkageError) {
                    log.warn("AgentClient diagnoseResolutionFailure is unavailable at runtime. Falling back.",
                            linkageError);
                    failureDiagnostics = Map.of(
                            "failureCategory", "unknown_process_state",
                            "explanation", e.getMessage(),
                            "retryable", Boolean.TRUE,
                            "source", "fallback-runtime",
                            "actionType", execution.getActionType(),
                            "pid", issue.getAffectedPid(),
                            "processName", issue.getProcessName());
                }
                Map<String, Object> details = new HashMap<>();
                details.put("resolutionVerified", Boolean.FALSE);
                details.put("verificationMessage", "Execution failed before verification.");
                details.put("failureDiagnostics", failureDiagnostics);
                try {
                    execution.setExecutionDetails(objectMapper.writeValueAsString(details));
                } catch (Exception jsonError) {
                    log.warn("Failed to serialize failure diagnostics for execution {}", execution.getId(), jsonError);
                }
            });

            executionRepository.save(execution);

            // Broadcast execution failed
            broadcaster.broadcastExecutionUpdate(execution.getId(), "FAILED", "Execution failed: " + e.getMessage());

            // --- INTELLIGENT FALLBACK MECHANISM ---
            // Try fallback actions based on process classification
            issueRepository.findById(execution.getIssueId()).ifPresent(issue -> {
                ProcessClassifier.ProcessClass classification = processClassifier.classify(issue);
                List<String> actionPlan = buildAdaptiveActionPlan(issue, execution.getActionType());

                String fallbackAction = null;
                for (String candidateAction : actionPlan) {
                    if (candidateAction != null
                            && !candidateAction.equals(execution.getActionType())
                            && actionSafetyMatrix.isActionSafeFor(candidateAction, classification)) {
                        fallbackAction = candidateAction;
                        break;
                    }
                }

                if (fallbackAction != null) {
                    log.info("Initiating fallback action {} for failed execution {}", fallbackAction,
                            execution.getId());
                    broadcaster.broadcastExecutionUpdate(execution.getId(), "FALLBACK",
                            "Attempting fallback: " + fallbackAction);

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
            });
            // -----------------------

            return convertToResult(execution);
        }
    }

    /**
     * Build an adaptive action plan based on issue type and process classification.
     * Uses ActionSafetyMatrix to filter safe actions for the process type.
     */
    private List<String> buildAdaptiveActionPlan(IssueEntity issue, String primaryAction) {
        List<String> plan = new ArrayList<>();

        if (issue == null) {
            addUnique(plan, primaryAction);
            return plan;
        }

        ProcessClassifier.ProcessClass classification = processClassifier.classify(issue);

        // Get action recommendation from safety matrix based on process class and issue
        // type
        String issueTypeStr = issue.getType() != null ? issue.getType().name() : "UNKNOWN";
        ActionSafetyMatrix.ActionRecommendation recommendation = actionSafetyMatrix.getRecommendedActions(issueTypeStr,
                classification);

        // Add the primary action if it's the one recommended
        if (recommendation.primaryAction() != null && recommendation.primaryAction().equals(primaryAction)) {
            addUnique(plan, primaryAction);
        }

        // Add all fallback actions that are safe for this process class
        if (recommendation.fallbackChain() != null) {
            for (String action : recommendation.fallbackChain()) {
                if (actionSafetyMatrix.isActionSafeFor(action, classification)) {
                    addUnique(plan, action);
                }
            }
        }

        // If plan is empty, use the recommended action from the matrix
        if (plan.isEmpty() && recommendation.primaryAction() != null) {
            addUnique(plan, recommendation.primaryAction());
        }

        // Final fallback: generic strategy
        if (plan.isEmpty()) {
            if (actionSafetyMatrix.isActionSafeFor("TRIM_WORKING_SET", classification)) {
                addUnique(plan, "TRIM_WORKING_SET");
            }
            if (actionSafetyMatrix.isActionSafeFor("REDUCE_PRIORITY", classification)) {
                addUnique(plan, "REDUCE_PRIORITY");
            }
        }

        return plan;
    }

    /**
     * Choose an adaptive action different from the requested one.
     * Respects process classification safety constraints.
     */
    private String chooseAdaptiveAction(IssueEntity issue, String requestedAction) {
        List<String> plan = buildAdaptiveActionPlan(issue, requestedAction);
        for (String action : plan) {
            if (action != null && !action.equals(requestedAction)) {
                return action;
            }
        }
        return null;
    }

    private void addUnique(List<String> plan, String action) {
        if (action != null && !plan.contains(action)) {
            plan.add(action);
        }
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
            return safetyPolicy.isProtected(issue.getProcessName(), issue.getAffectedPid());
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

        Object attemptedActions = actionResult != null ? actionResult.get("attemptedActions") : null;
        if (attemptedActions instanceof List<?> attemptedList && !attemptedList.isEmpty()) {
            steps.add("Attempted actions: "
                    + attemptedList.stream().map(String::valueOf).collect(Collectors.joining(" -> ")));
        }

        List<String> agentSteps = extractAgentSteps(actionResult);
        if (!agentSteps.isEmpty()) {
            steps.addAll(agentSteps);
        }

        steps.add(resolutionCheck.resolved()
                ? "Verified the issue is resolved"
                : "Issue remains active and needs manual verification");
        return steps;
    }

    private List<String> buildExecutionPlan(List<String> actionPlan) {
        List<String> steps = new java.util.ArrayList<>();
        steps.add("Validate safety policy and execution lock");
        steps.add("Capture current process snapshot");
        steps.add("Send remediation request(s) to the agent: " + String.join(" -> ", actionPlan));
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
            case "RESTART_PROCESS" -> {
                Map<String, Object> processInfo = agentClient.getProcessInfo(issue.getAffectedPid());
                boolean processPresent = processInfo != null
                        && !processInfo.containsKey("error")
                        && !"unknown".equalsIgnoreCase(String.valueOf(processInfo.getOrDefault("name", "unknown")));
                yield processPresent
                        ? new ResolutionCheck(true,
                                "Process restart verified. A healthy process instance is running and the issue is marked resolved.")
                        : new ResolutionCheck(false,
                                "Restart was attempted, but no healthy process instance was verified. Manual follow-up is required.");
            }
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

    /**
     * Format an action type name for display in messages.
     * Converts enum names to readable format (e.g., KILL_PROCESS -> Kill Process).
     * 
     * @param actionType The action type string
     * @return Formatted action name
     */
    private String formatActionName(String actionType) {
        if (actionType == null) {
            return "Unknown";
        }

        StringBuilder result = new StringBuilder();
        for (String word : actionType.split("_")) {
            if (!word.isEmpty()) {
                result.append(word.charAt(0))
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }
        return result.toString().trim();
    }
}
