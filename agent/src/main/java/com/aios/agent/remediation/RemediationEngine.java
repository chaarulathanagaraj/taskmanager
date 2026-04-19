package com.aios.agent.remediation;

import com.aios.agent.client.BackendClient;
import com.aios.agent.config.AgentConfiguration;
import com.aios.shared.dto.DiagnosticIssue;
import com.aios.shared.dto.PolicyViolation;
import com.aios.shared.enums.ActionType;
import com.aios.shared.enums.IssueType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * RemediationEngine orchestrates the execution of remediation actions
 * in response to detected diagnostic issues.
 * 
 * <p>
 * Features:
 * <ul>
 * <li>Automatic action selection via RuleEngine</li>
 * <li>Concurrent action execution with limits</li>
 * <li>Dry-run mode for safe testing</li>
 * <li>Protected process validation</li>
 * <li>Action history tracking</li>
 * <li>Retry logic with exponential backoff</li>
 * </ul>
 * 
 * <p>
 * Example usage:
 * 
 * <pre>{@code
 * // Execute remediation for an issue
 * ActionResult result = remediationEngine.executeRemediation(issue);
 * if (result.isSuccess()) {
 *     log.info("Remediation successful: {}", result.getMessage());
 * }
 * 
 * // Execute with custom action type
 * ActionResult result = remediationEngine.executeRemediation(
 *         issue, ActionType.REDUCE_PRIORITY);
 * }</pre>
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RemediationEngine {

    private final List<RemediationAction> remediationActions;
    private final AgentConfiguration config;
    private final BackendClient backendClient;
    private final RemediationCooldownManager cooldownManager;

    // Action registry: map ActionType to RemediationAction implementation
    private Map<String, RemediationAction> actionRegistry;

    // Executor for concurrent action execution
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    // Semaphore to limit concurrent actions
    private Semaphore concurrentActionSemaphore;

    // Action history (in-memory for now, can be moved to database)
    private final ConcurrentLinkedDeque<ActionExecutionRecord> actionHistory = new ConcurrentLinkedDeque<>();

    // Statistics
    private final AtomicInteger totalActionsExecuted = new AtomicInteger(0);
    private final AtomicInteger successfulActions = new AtomicInteger(0);
    private final AtomicInteger failedActions = new AtomicInteger(0);
    private final AtomicInteger dryRunActions = new AtomicInteger(0);

    private static final int MAX_HISTORY_SIZE = 1000;
    private static final int DEFAULT_RETRY_ATTEMPTS = 2;

    /**
     * Initialize the engine after construction.
     * Called automatically by Spring after dependency injection.
     */
    @jakarta.annotation.PostConstruct
    public void initialize() {
        // Build action registry from injected RemediationAction beans
        actionRegistry = remediationActions.stream()
                .filter(RemediationAction::isEnabled)
                .collect(Collectors.toMap(
                        action -> normalizeActionKey(action.getName()),
                        Function.identity()));

        // Initialize semaphore for concurrent action limiting
        int maxConcurrent = config.getMaxConcurrentActions() > 0 ? config.getMaxConcurrentActions() : 3;
        concurrentActionSemaphore = new Semaphore(maxConcurrent);

        log.info("RemediationEngine initialized with {} actions, max concurrent: {}",
                actionRegistry.size(), maxConcurrent);
        log.info("Registered actions: {}", actionRegistry.keySet());
        log.info("Dry-run mode: {}, Auto-remediation: {}",
                config.isDryRunMode(), config.isAutoRemediationEnabled());
    }

    /**
     * Execute remediation for a diagnostic issue.
     * Uses RuleEngine to determine the appropriate action.
     * 
     * @param issue the diagnostic issue to remediate
     * @return the result of the action execution
     */
    public ActionResult executeRemediation(DiagnosticIssue issue) {
        if (!config.isAutoRemediationEnabled() && !config.isDryRunMode()) {
            log.warn("Auto-remediation disabled and not in dry-run mode, skipping issue: {}",
                    issue.getType());
            return ActionResult.failure("Auto-remediation is disabled");
        }

        // Determine action type using simple rule engine logic
        ActionType actionType = determineActionType(issue);

        return executeRemediation(issue, actionType);
    }

    /**
     * Execute a specific remediation action for an issue.
     * 
     * @param issue      the diagnostic issue
     * @param actionType the type of action to execute
     * @return the result of the action execution
     */
    public ActionResult executeRemediation(DiagnosticIssue issue, ActionType actionType) {
        log.info("Executing remediation: action={}, issue={}, pid={}, process={}",
                actionType, issue.getType(), issue.getAffectedPid(), issue.getProcessName());

        // Find action implementation
        RemediationAction action = findAction(actionType);
        if (action == null) {
            String message = "No action implementation found for type: " + actionType;
            log.error(message);
            return ActionResult.failure(message);
        }

        // Build remediation context
        RemediationContext context = buildContext(issue, action);

        // Check confidence threshold
        if (!config.isDryRunMode() &&
                issue.getConfidence() < config.getAutoRemediationConfidenceThreshold()) {
            ActionResult result = ActionResult.confidenceThresholdNotMet(
                    issue.getProcessName(),
                    issue.getAffectedPid(),
                    issue.getType().name(),
                    issue.getConfidence(),
                    config.getAutoRemediationConfidenceThreshold());
            log.warn(result.getMessage());
            recordExecution(action, context, result);
            return result;
        }

        // Check cooldown to prevent rapid-fire repeated actions
        if (!config.isDryRunMode()) {
            ActionResult cooldownResult = cooldownManager.checkCooldown(
                    issue.getProcessName(),
                    issue.getAffectedPid(),
                    actionType);

            if (cooldownResult != null) {
                log.info("Remediation blocked by cooldown: {}", cooldownResult.getMessage());
                recordExecution(action, context, cooldownResult);
                return cooldownResult;
            }
        }

        // Check safety policy before executing action
        PolicyViolation policyViolation = checkSafetyPolicy(
                actionType, issue, context.isDryRun());

        if (policyViolation.isViolated()) {
            log.warn("Policy violation for action {} on {}: {}",
                    actionType, issue.getProcessName(), policyViolation.getReason());

            ActionResult blockedResult;

            // Use customized messages based on violation type
            String reason = policyViolation.getReason().toLowerCase();
            if (reason.contains("protected")) {
                blockedResult = ActionResult.protectedProcessBlocked(
                        issue.getProcessName(),
                        issue.getAffectedPid(),
                        actionType);
            } else {
                // Generic policy violation message
                blockedResult = ActionResult.policyBlocked(policyViolation);
            }

            recordExecution(action, context, blockedResult);
            return blockedResult;
        }

        // Execute with concurrency control
        ActionResult result;
        try {
            result = executeWithConcurrencyControl(action, context);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result = ActionResult.failure("Action execution interrupted", e);
        }

        // Record execution
        recordExecution(action, context, result);

        return result;
    }

    /**
     * Execute multiple remediations asynchronously.
     * 
     * @param issues list of issues to remediate
     * @return future that completes when all actions finish
     */
    public CompletableFuture<List<ActionResult>> executeRemediationsAsync(
            List<DiagnosticIssue> issues) {

        List<CompletableFuture<ActionResult>> futures = issues.stream()
                .map(issue -> CompletableFuture.supplyAsync(
                        () -> executeRemediation(issue),
                        executorService))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()));
    }

    /**
     * Execute action with retry logic.
     * 
     * @param issue      the diagnostic issue
     * @param actionType the action type
     * @param maxRetries maximum retry attempts
     * @return the result of the action execution
     */
    public ActionResult executeWithRetry(DiagnosticIssue issue,
            ActionType actionType,
            int maxRetries) {
        ActionResult result = null;
        int attempt = 0;

        while (attempt <= maxRetries) {
            result = executeRemediation(issue, actionType);

            if (result.isSuccess() || result.isDryRun()) {
                return result;
            }

            attempt++;
            if (attempt <= maxRetries) {
                long delayMs = (long) Math.pow(2, attempt) * 1000; // Exponential backoff
                log.warn("Action failed, retrying in {}ms (attempt {}/{})",
                        delayMs, attempt, maxRetries);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return result;
    }

    /**
     * Get action execution history.
     * 
     * @param limit maximum number of records to return
     * @return list of recent action execution records
     */
    public List<ActionExecutionRecord> getActionHistory(int limit) {
        return actionHistory.stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Get action history for a specific process.
     * 
     * @param pid process ID
     * @return list of action records for the process
     */
    public List<ActionExecutionRecord> getActionHistoryForProcess(int pid) {
        return actionHistory.stream()
                .filter(record -> record.getTargetPid() == pid)
                .collect(Collectors.toList());
    }

    /**
     * Get statistics about action executions.
     * 
     * @return statistics object
     */
    public RemediationStatistics getStatistics() {
        return RemediationStatistics.builder()
                .totalActionsExecuted(totalActionsExecuted.get())
                .successfulActions(successfulActions.get())
                .failedActions(failedActions.get())
                .dryRunActions(dryRunActions.get())
                .activeActions(config.getMaxConcurrentActions() -
                        concurrentActionSemaphore.availablePermits())
                .registeredActions(actionRegistry.size())
                .historySize(actionHistory.size())
                .build();
    }

    /**
     * Clear action history.
     */
    public void clearHistory() {
        actionHistory.clear();
        log.info("Action history cleared");
    }

    /**
     * Shutdown the engine gracefully.
     */
    @jakarta.annotation.PreDestroy
    public void shutdown() {
        log.info("Shutting down RemediationEngine...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("RemediationEngine shut down");
    }

    // ==================== Private Helper Methods ====================

    /**
     * Execute action with concurrency control.
     */
    private ActionResult executeWithConcurrencyControl(RemediationAction action,
            RemediationContext context)
            throws InterruptedException {

        // Acquire permit (blocks if max concurrent actions reached)
        boolean acquired = concurrentActionSemaphore.tryAcquire(
                context.getTimeoutMillis(), TimeUnit.MILLISECONDS);

        if (!acquired) {
            String message = "Could not acquire execution permit - max concurrent actions reached";
            log.warn(message);
            return ActionResult.failure(message)
                    .addDetail("maxConcurrent", config.getMaxConcurrentActions());
        }

        try {
            // Execute the action with timeout
            Future<ActionResult> future = executorService.submit(() -> {
                try {
                    return action.execute(context);
                } catch (RemediationException e) {
                    log.error("Remediation exception: {}", e.getMessage());
                    return ActionResult.failure(e.getMessage(), e);
                } catch (Exception e) {
                    log.error("Unexpected error during action execution", e);
                    return ActionResult.failure("Unexpected error: " + e.getMessage(), e);
                }
            });

            try {
                return future.get(context.getTimeoutMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                return ActionResult.failure("Action execution timed out after " +
                        context.getTimeoutMillis() + "ms");
            } catch (ExecutionException e) {
                return ActionResult.failure("Action execution failed", e.getCause());
            }

        } finally {
            concurrentActionSemaphore.release();
        }
    }

    /**
     * Build remediation context from issue and configuration.
     */
    private RemediationContext buildContext(DiagnosticIssue issue, RemediationAction action) {
        return RemediationContext.builder()
                .targetPid(issue.getAffectedPid())
                .processName(issue.getProcessName())
                .issue(issue)
                .dryRun(config.isDryRunMode() || action.isDryRun())
                .protectedProcesses(config.getProtectedProcesses())
                .confidenceThreshold(config.getAutoRemediationConfidenceThreshold())
                .timeoutMillis(30000L)
                .initiatedBy("RemediationEngine")
                .build();
    }

    /**
     * Check safety policy before executing an action.
     * Uses backend policy service when available, falls back to local checks.
     * 
     * @param actionType the action to be executed
     * @param issue      the diagnostic issue being remediated
     * @param isDryRun   whether this is a dry-run execution
     * @return PolicyViolation indicating whether action is allowed
     */
    private PolicyViolation checkSafetyPolicy(ActionType actionType,
            DiagnosticIssue issue,
            boolean isDryRun) {
        // First, do a quick local check for protected processes
        if (config.getProtectedProcesses() != null &&
                config.getProtectedProcesses().contains(issue.getProcessName())) {
            log.info("Process {} is in local protected list", issue.getProcessName());
            return PolicyViolation.protectedProcess(issue.getProcessName(), issue.getAffectedPid(), actionType);
        }

        // Call backend policy service for full policy check
        try {
            PolicyViolation backendResult = backendClient.checkPolicy(
                    actionType,
                    issue.getProcessName(),
                    issue.getAffectedPid(),
                    isDryRun,
                    issue.getConfidence());

            if (backendResult != null) {
                log.debug("Backend policy check result: violated={}, reason={}",
                        backendResult.isViolated(), backendResult.getReason());
                return backendResult;
            }
        } catch (Exception e) {
            log.warn("Backend policy check failed, using local fallback: {}", e.getMessage());
        }

        // Fallback to local policy check if backend unavailable
        return applyLocalPolicyCheck(actionType, issue.getProcessName(), isDryRun);
    }

    /**
     * Local policy check fallback when backend is unavailable.
     */
    private PolicyViolation applyLocalPolicyCheck(ActionType actionType,
            String processName,
            boolean isDryRun) {
        // In dry-run mode, allow most actions for testing
        if (isDryRun) {
            return PolicyViolation.allowed();
        }

        // Block dangerous actions without backend validation
        if (actionType == ActionType.KILL_PROCESS) {
            // Only allow killing non-system processes locally
            String normalizedName = processName != null ? processName.toLowerCase() : "";
            if (normalizedName.endsWith(".exe") &&
                    !normalizedName.contains("svchost") &&
                    !normalizedName.contains("system")) {
                return PolicyViolation.allowed();
            }
            return PolicyViolation.builder()
                    .violated(true)
                    .reason("Kill action requires backend policy validation")
                    .policyName("LocalSafetyPolicy")
                    .severity(com.aios.shared.enums.ViolationSeverity.HIGH)
                    .blocking(true)
                    .attemptedAction(actionType)
                    .targetProcess(processName)
                    .build();
        }

        // Allow low-risk actions locally
        return PolicyViolation.allowed();
    }

    /**
     * Record action execution for history and statistics.
     */
    private void recordExecution(RemediationAction action,
            RemediationContext context,
            ActionResult result) {

        // Update statistics
        totalActionsExecuted.incrementAndGet();
        if (result.isDryRun()) {
            dryRunActions.incrementAndGet();
        } else if (result.isSuccess()) {
            successfulActions.incrementAndGet();
            log.info("Remediation successful: {}", result.getMessage());

            // Record in cooldown manager for future rate limiting
            if (!result.isDryRun() && action != null) {
                ActionType actionType = ActionType.valueOf(action.getName().toUpperCase()
                        .replace(" ", "_")
                        .replace("ACTION", "")
                        .trim());
                try {
                    cooldownManager.recordExecution(
                            context.getProcessName(),
                            context.getTargetPid(),
                            actionType,
                            true);
                } catch (Exception e) {
                    log.warn("Failed to record cooldown: {}", e.getMessage());
                }
            }
        } else {
            failedActions.incrementAndGet();
            log.warn("Remediation failed: {}", result.getMessage());
        }

        // Add to history
        ActionExecutionRecord record = ActionExecutionRecord.builder()
                .actionName(action.getName())
                .targetPid(context.getTargetPid())
                .processName(context.getProcessName())
                .result(result)
                .safetyLevel(action.getSafetyLevel())
                .executedAt(result.getExecutedAt())
                .dryRun(result.isDryRun())
                .build();

        actionHistory.addFirst(record);

        // Trim history if too large
        while (actionHistory.size() > MAX_HISTORY_SIZE) {
            actionHistory.removeLast();
        }
    }

    /**
     * Find action implementation by type.
     */
    private RemediationAction findAction(ActionType actionType) {
        String typeName = actionType.name();

        // Primary match: enum name to action class-style name (e.g., KILL_PROCESS ->
        // KillProcessAction).
        String classStyleName = toClassStyleActionName(typeName);
        RemediationAction action = actionRegistry.get(normalizeActionKey(classStyleName));
        if (action != null) {
            return action;
        }

        // Fallback: accept direct enum key formats if an action uses them.
        action = actionRegistry.get(normalizeActionKey(typeName));
        if (action != null) {
            return action;
        }

        // Final fallback: compare normalized names across values.
        String normalizedType = normalizeActionKey(typeName);
        Optional<RemediationAction> found = actionRegistry.values().stream()
                .filter(a -> normalizeActionKey(a.getName()).contains(normalizedType)
                        || normalizedType.contains(normalizeActionKey(a.getName())))
                .findFirst();

        return found.orElse(null);
    }

    private String toClassStyleActionName(String enumName) {
        StringBuilder builder = new StringBuilder();
        for (String part : enumName.split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            String lower = part.toLowerCase();
            builder.append(Character.toUpperCase(lower.charAt(0))).append(lower.substring(1));
        }
        builder.append("Action");
        return builder.toString();
    }

    private String normalizeActionKey(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
    }

    /**
     * Determine appropriate action type for an issue.
     * Simple rule engine logic (can be extracted to separate RuleEngine later).
     */
    private ActionType determineActionType(DiagnosticIssue issue) {
        return switch (issue.getType()) {
            case MEMORY_LEAK -> issue.getConfidence() > 0.8 ? ActionType.KILL_PROCESS : ActionType.TRIM_WORKING_SET;
            case THREAD_EXPLOSION -> ActionType.KILL_PROCESS;
            case HUNG_PROCESS -> ActionType.KILL_PROCESS;
            case IO_BOTTLENECK -> ActionType.REDUCE_PRIORITY;
            case RESOURCE_HOG ->
                issue.getSeverity().name().equals("CRITICAL") ? ActionType.KILL_PROCESS : ActionType.REDUCE_PRIORITY;
            default -> ActionType.REDUCE_PRIORITY;
        };
    }

    // ==================== Inner Classes ====================

    /**
     * Record of a single action execution.
     */
    @lombok.Data
    @lombok.Builder
    public static class ActionExecutionRecord {
        private String actionName;
        private int targetPid;
        private String processName;
        private ActionResult result;
        private com.aios.shared.enums.SafetyLevel safetyLevel;
        private Instant executedAt;
        private boolean dryRun;
    }

    /**
     * Statistics about remediation engine operations.
     */
    @lombok.Data
    @lombok.Builder
    public static class RemediationStatistics {
        private int totalActionsExecuted;
        private int successfulActions;
        private int failedActions;
        private int dryRunActions;
        private int activeActions;
        private int registeredActions;
        private int historySize;

        public double getSuccessRate() {
            int nonDryRun = totalActionsExecuted - dryRunActions;
            return nonDryRun > 0 ? (double) successfulActions / nonDryRun * 100 : 0.0;
        }
    }
}
