package com.aios.agent.client;

import com.aios.agent.config.AgentConfiguration;
import com.aios.shared.dto.DiagnosticIssue;
import com.aios.shared.dto.MetricSnapshot;
import com.aios.shared.dto.PolicyViolation;
import com.aios.shared.dto.RemediationActionLog;
import com.aios.shared.enums.ActionType;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;

/**
 * Service for synchronizing agent data with the backend server.
 * 
 * <p>
 * Handles communication with the Spring Boot backend, including:
 * <ul>
 * <li>Sending metrics batches every 30 seconds</li>
 * <li>Reporting detected issues every 10 seconds</li>
 * <li>Logging remediation actions in real-time</li>
 * </ul>
 * 
 * <p>
 * Uses queuing to handle offline scenarios and implements retry logic
 * with exponential backoff for failed requests.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Service
@Slf4j
public class BackendClient {

    private final WebClient webClient;
    private final AgentConfiguration config;

    // Resilience4j retry instances
    private final Retry metricSyncRetry;
    private final Retry issueSyncRetry;
    private final Retry actionSyncRetry;
    private final Retry healthCheckRetry;

    // Queues for buffering data when backend is unavailable
    private final BlockingQueue<MetricSnapshot> metricQueue;
    private final BlockingQueue<DiagnosticIssue> issueQueue;
    private final BlockingQueue<RemediationActionLog> actionQueue;

    // Track connection state
    private boolean backendAvailable = true;
    private int consecutiveFailures = 0;
    private static final int MAX_QUEUE_SIZE = 1000;

    public BackendClient(AgentConfiguration config,
            @Qualifier("metricSyncRetry") Retry metricSyncRetry,
            @Qualifier("issueSyncRetry") Retry issueSyncRetry,
            @Qualifier("actionSyncRetry") Retry actionSyncRetry,
            @Qualifier("healthCheckRetry") Retry healthCheckRetry) {
        this.config = config;
        this.metricSyncRetry = metricSyncRetry;
        this.issueSyncRetry = issueSyncRetry;
        this.actionSyncRetry = actionSyncRetry;
        this.healthCheckRetry = healthCheckRetry;

        this.webClient = WebClient.builder()
                .baseUrl(config.getBackendUrl())
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("User-Agent", "AIOS-Agent/1.0")
                .build();

        this.metricQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
        this.issueQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
        this.actionQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);

        log.info("BackendClient initialized with URL: {}", config.getBackendUrl());
        log.info("Resilience4j retry configured: metrics={} attempts, issues={} attempts, actions={} attempts",
                metricSyncRetry.getRetryConfig().getMaxAttempts(),
                issueSyncRetry.getRetryConfig().getMaxAttempts(),
                actionSyncRetry.getRetryConfig().getMaxAttempts());
    }

    /**
     * Queue a metric snapshot for sending to backend.
     * Called by SystemMetricsCollector.
     * 
     * @param metric the metric snapshot to queue
     */
    public void queueMetric(MetricSnapshot metric) {
        if (!metricQueue.offer(metric)) {
            log.warn("Metric queue full, dropping oldest metric");
            metricQueue.poll(); // Remove oldest
            metricQueue.offer(metric);
        }
    }

    /**
     * Queue a diagnostic issue for sending to backend.
     * Called by DetectorManager.
     * 
     * @param issue the issue to queue
     */
    public void queueIssue(DiagnosticIssue issue) {
        if (!issueQueue.offer(issue)) {
            log.warn("Issue queue full, dropping oldest issue");
            issueQueue.poll();
            issueQueue.offer(issue);
        }
    }

    /**
     * Queue a remediation action log for sending to backend.
     * Called by RemediationEngine.
     * 
     * @param actionLog the action log to queue
     */
    public void queueAction(RemediationActionLog actionLog) {
        if (!actionQueue.offer(actionLog)) {
            log.warn("Action queue full, dropping oldest action");
            actionQueue.poll();
            actionQueue.offer(actionLog);
        }
    }

    /**
     * Synchronize metrics with backend.
     * Runs every 30 seconds.
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 10000)
    public void syncMetrics() {
        if (metricQueue.isEmpty()) {
            return;
        }

        List<MetricSnapshot> batch = new ArrayList<>();
        metricQueue.drainTo(batch, 100); // Send up to 100 at a time

        log.debug("Syncing {} metrics to backend", batch.size());

        // Wrap the WebClient call with Resilience4j Retry
        Supplier<Mono<Void>> supplier = () -> webClient.post()
                .uri("/api/metrics")
                .bodyValue(batch)
                .retrieve()
                .bodyToMono(Void.class);

        Supplier<Mono<Void>> decoratedSupplier = Retry.decorateSupplier(metricSyncRetry, supplier);

        decoratedSupplier.get()
                .doOnSuccess(v -> {
                    log.debug("Successfully synced {} metrics", batch.size());
                    onSyncSuccess();
                })
                .doOnError(e -> {
                    log.error("Failed to sync metrics after retries: {}", e.getMessage());
                    onSyncFailure(batch, metricQueue);
                })
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }

    /**
     * Synchronize detected issues with backend.
     * Runs every 10 seconds (issues are higher priority).
     */
    @Scheduled(fixedDelay = 10000, initialDelay = 5000)
    public void syncIssues() {
        if (issueQueue.isEmpty()) {
            return;
        }

        List<DiagnosticIssue> batch = new ArrayList<>();
        issueQueue.drainTo(batch, 50);

        log.debug("Syncing {} issues to backend", batch.size());

        // Send issues individually for better error tracking
        batch.forEach(issue -> {
            Supplier<Mono<Void>> supplier = () -> webClient.post()
                    .uri("/api/issues")
                    .bodyValue(issue)
                    .retrieve()
                    .bodyToMono(Void.class);

            Supplier<Mono<Void>> decoratedSupplier = Retry.decorateSupplier(issueSyncRetry, supplier);

            decoratedSupplier.get()
                    .doOnSuccess(v -> {
                        log.info("Reported issue: type={}, pid={}, confidence={}",
                                issue.getType(), issue.getAffectedPid(), issue.getConfidence());
                        onSyncSuccess();
                    })
                    .doOnError(e -> {
                        log.error("Failed to report issue {} after retries: {}",
                                issue.getType(), e.getMessage());
                        issueQueue.offer(issue); // Re-queue on failure
                        onSyncFailure(null, null);
                    })
                    .onErrorResume(e -> Mono.empty())
                    .subscribe();
        });
    }

    /**
     * Synchronize action logs with backend.
     * Runs every 5 seconds (actions need immediate tracking).
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 3000)
    public void syncActions() {
        if (actionQueue.isEmpty()) {
            return;
        }

        List<RemediationActionLog> batch = new ArrayList<>();
        actionQueue.drainTo(batch, 50);

        log.debug("Syncing {} actions to backend", batch.size());

        batch.forEach(action -> {
            Supplier<Mono<Void>> supplier = () -> webClient.post()
                    .uri("/api/actions")
                    .bodyValue(action)
                    .retrieve()
                    .bodyToMono(Void.class);

            Supplier<Mono<Void>> decoratedSupplier = Retry.decorateSupplier(actionSyncRetry, supplier);

            decoratedSupplier.get()
                    .doOnSuccess(v -> {
                        log.info("Logged action: type={}, pid={}, status={}",
                                action.getActionType(), action.getTargetPid(), action.getStatus());
                        onSyncSuccess();
                    })
                    .doOnError(e -> {
                        log.error("Failed to log action {} after retries: {}",
                                action.getActionType(), e.getMessage());
                        actionQueue.offer(action); // Re-queue on failure
                        onSyncFailure(null, null);
                    })
                    .onErrorResume(e -> Mono.empty())
                    .subscribe();
        });
    }

    /**
     * Health check endpoint to verify backend availability.
     * Runs every 60 seconds.
     */
    @Scheduled(fixedDelay = 60000)
    public void checkBackendHealth() {
        Supplier<Mono<String>> supplier = () -> webClient.get()
                .uri("/actuator/health")
                .retrieve()
                .bodyToMono(String.class);

        Supplier<Mono<String>> decoratedSupplier = Retry.decorateSupplier(healthCheckRetry, supplier);

        decoratedSupplier.get()
                .doOnSuccess(health -> {
                    if (!backendAvailable) {
                        log.info("Backend connection restored");
                        backendAvailable = true;
                        consecutiveFailures = 0;
                    }
                })
                .doOnError(e -> {
                    if (backendAvailable) {
                        log.warn("Backend health check failed: {}", e.getMessage());
                        backendAvailable = false;
                    }
                })
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }

    /**
     * Check if an action is allowed by safety policies.
     * 
     * <p>
     * Calls the backend policy service to validate an action before execution.
     * This is a synchronous call that blocks until a response is received.
     * 
     * @param actionType  the type of action to validate
     * @param processName the name of the target process
     * @param pid         the PID of the target process (nullable)
     * @param isDryRun    whether this is a dry-run execution
     * @param confidence  the confidence level of the issue (0.0 - 1.0)
     * @return PolicyViolation object indicating if action is allowed
     */
    public PolicyViolation checkPolicy(ActionType actionType,
            String processName,
            Integer pid,
            boolean isDryRun,
            double confidence) {
        if (!backendAvailable) {
            log.warn("Backend unavailable, applying local fallback policy check");
            return applyLocalPolicyFallback(actionType, processName, pid);
        }

        try {
            java.util.Map<String, Object> request = new java.util.HashMap<>();
            request.put("actionType", actionType.name());
            request.put("processName", processName);
            request.put("pid", pid);
            request.put("isDryRun", isDryRun);
            request.put("confidence", confidence);

            PolicyViolation result = webClient.post()
                    .uri("/api/policies/check")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(PolicyViolation.class)
                    .timeout(java.time.Duration.ofSeconds(5))
                    .block();

            if (result == null) {
                log.warn("Policy check returned null, allowing action");
                return PolicyViolation.allowed();
            }

            return result;
        } catch (Exception e) {
            log.error("Policy check failed, applying local fallback: {}", e.getMessage());
            return applyLocalPolicyFallback(actionType, processName, pid);
        }
    }

    /**
     * Local fallback policy check when backend is unavailable.
     * Applies conservative safety rules.
     */
    private PolicyViolation applyLocalPolicyFallback(ActionType actionType,
            String processName,
            Integer pid) {
        // Critical system processes that should never be killed
        java.util.Set<String> criticalProcesses = java.util.Set.of(
                "system", "csrss.exe", "wininit.exe", "winlogon.exe", "services.exe",
                "lsass.exe", "smss.exe", "svchost.exe", "dwm.exe", "explorer.exe");

        String normalizedName = processName != null ? processName.toLowerCase() : "";

        // Block actions on critical system processes
        if (criticalProcesses.contains(normalizedName)) {
            return PolicyViolation.protectedProcess(processName, pid != null ? pid : -1, actionType);
        }

        // Block dangerous actions when backend is unavailable
        if (actionType == ActionType.KILL_PROCESS ||
                actionType == ActionType.RESTART_PROCESS) {
            return PolicyViolation.builder()
                    .violated(true)
                    .reason("Dangerous action blocked - backend unavailable for policy validation")
                    .policyName("LocalFallbackPolicy")
                    .severity(com.aios.shared.enums.ViolationSeverity.HIGH)
                    .blocking(true)
                    .overridable(false)
                    .attemptedAction(actionType)
                    .targetProcess(processName)
                    .targetPid(pid)
                    .build();
        }

        // Allow safe actions
        return PolicyViolation.allowed();
    }

    /**
     * Get current queue sizes for monitoring.
     * 
     * @return map of queue names to sizes
     */
    public java.util.Map<String, Integer> getQueueSizes() {
        return java.util.Map.of(
                "metrics", metricQueue.size(),
                "issues", issueQueue.size(),
                "actions", actionQueue.size());
    }

    /**
     * Check if backend is currently available.
     * 
     * @return true if backend is responding
     */
    public boolean isBackendAvailable() {
        return backendAvailable;
    }

    /**
     * Handle successful sync operation.
     */
    private void onSyncSuccess() {
        if (consecutiveFailures > 0) {
            consecutiveFailures = 0;
            backendAvailable = true;
            log.info("Backend communication recovered");
        }
    }

    /**
     * Handle failed sync operation.
     * Re-queues data and tracks failures.
     */
    private <T> void onSyncFailure(List<T> batch, BlockingQueue<T> queue) {
        consecutiveFailures++;

        if (consecutiveFailures >= 5) {
            if (backendAvailable) {
                log.error("Backend appears to be down after {} consecutive failures",
                        consecutiveFailures);
                backendAvailable = false;
            }
        }

        // Re-queue failed batch if provided
        if (batch != null && queue != null && !batch.isEmpty()) {
            batch.forEach(item -> {
                if (!queue.offer(item)) {
                    log.warn("Queue full, cannot re-queue item");
                }
            });
        }
    }

    /**
     * Clear all queues (for testing or emergency reset).
     */
    public void clearQueues() {
        metricQueue.clear();
        issueQueue.clear();
        actionQueue.clear();
        log.info("All queues cleared");
    }

    /**
     * Get statistics about backend communication.
     * 
     * @return statistics map
     */
    public java.util.Map<String, Object> getStatistics() {
        return java.util.Map.of(
                "backendUrl", config.getBackendUrl(),
                "backendAvailable", backendAvailable,
                "consecutiveFailures", consecutiveFailures,
                "queuedMetrics", metricQueue.size(),
                "queuedIssues", issueQueue.size(),
                "queuedActions", actionQueue.size(),
                "maxQueueSize", MAX_QUEUE_SIZE);
    }
}
