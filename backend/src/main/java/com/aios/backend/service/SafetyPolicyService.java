package com.aios.backend.service;

import com.aios.backend.model.SafetyPolicyEntity;
import com.aios.backend.repository.SafetyPolicyRepository;
import com.aios.shared.client.AgentClient;
import com.aios.shared.dto.PolicyViolation;
import com.aios.shared.enums.ActionType;
import com.aios.shared.enums.SafetyLevel;
import com.aios.shared.enums.ViolationSeverity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for managing and enforcing safety policies.
 * 
 * <p>
 * Provides policy enforcement before remediation actions are executed.
 * Checks for protected processes, required approvals, and rate limits.
 * 
 * <p>
 * Example usage:
 * 
 * <pre>{@code
 * PolicyViolation violation = safetyPolicyService.checkPolicy(
 *         ActionType.KILL_PROCESS, "chrome.exe", 1234, false, 0.85);
 * 
 * if (violation.isViolated()) {
 *     log.warn("Policy violation: {}", violation.getReason());
 *     // Block action or request approval
 * }
 * }</pre>
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SafetyPolicyService {

    private final SafetyPolicyRepository policyRepository;
    private final SettingsService settingsService;
    private final AgentClient agentClient;

    private static final Set<String> DEV_PROCESS_NAMES = Set.of(
            "java",
            "javaw",
            "node",
            "npm",
            "pnpm",
            "yarn",
            "powershell",
            "pwsh",
            "cmd",
            "conhost",
            "code",
            "devenv");

    /**
     * Default protected system processes that should never be terminated.
     */
    @Value("${aios.safety.protected-processes:csrss.exe,winlogon.exe,services.exe,lsass.exe,System,smss.exe,svchost.exe,wininit.exe,dwm.exe}")
    private String defaultProtectedProcesses;

    /**
     * Whether to enforce policies in production mode.
     */
    @Value("${aios.safety.enforce-policies:true}")
    private boolean enforcePolicies;

    /**
     * Whether active workspace-linked processes should be auto-protected.
     */
    @Value("${aios.safety.protect-active-workspace-processes:true}")
    private boolean protectActiveWorkspaceProcesses;

    /**
     * Workspace markers used to identify development/runtime processes that should
     * never be auto-remediated.
     */
    @Value("${aios.safety.workspace-markers:taskmanager,aios,backend,frontend,mcp-server,agent}")
    private String workspaceMarkers;

    /**
     * Global dry-run mode setting.
     */
    @Value("${agent.dry-run-mode:true}")
    private boolean globalDryRunMode;

    /**
     * Rate limit tracking: action type -> execution count per hour.
     */
    private final ConcurrentHashMap<ActionType, AtomicInteger> executionCounts = new ConcurrentHashMap<>();

    /**
     * Last reset timestamp for rate limiting.
     */
    private volatile Instant lastRateLimitReset = Instant.now();

    /**
     * Cached list of system-critical processes.
     */
    private List<String> systemProtectedProcesses;

    @PostConstruct
    public void initialize() {
        // Parse default protected processes
        systemProtectedProcesses = List.of(defaultProtectedProcesses.split(","));
        log.info("SafetyPolicyService initialized with {} system-protected processes",
                systemProtectedProcesses.size());
        log.info("Policy enforcement: {}, Global dry-run: {}", enforcePolicies, globalDryRunMode);

        // Initialize default policies if none exist
        initializeDefaultPolicies();
    }

    /**
     * Check if an action is allowed by safety policies.
     * 
     * @param actionType  the action to check
     * @param processName name of the target process
     * @param pid         process ID
     * @param isDryRun    whether this is a dry-run execution
     * @param confidence  AI confidence level (0.0 - 1.0)
     * @return PolicyViolation indicating if action is blocked and why
     */
    public PolicyViolation checkPolicy(ActionType actionType, String processName,
            int pid, boolean isDryRun, double confidence) {
        log.debug("Checking policy: action={}, process={}, pid={}, dryRun={}, confidence={}",
                actionType, processName, pid, isDryRun, confidence);

        // If policies are not enforced, allow everything
        if (!enforcePolicies) {
            log.debug("Policy enforcement disabled, allowing action");
            return PolicyViolation.allowed();
        }

        // Check 1: System-critical protected processes (always blocked)
        if (isSystemProtectedProcess(processName)) {
            log.warn("Blocked: {} is a system-critical protected process", processName);
            return PolicyViolation.protectedProcess(processName, pid, actionType);
        }

        // Check 1b: User/configured protected processes (also always blocked)
        if (settingsService.isProcessProtected(processName)) {
            log.warn("Blocked: {} is protected by current settings", processName);
            return PolicyViolation.protectedProcess(processName, pid, actionType);
        }

        // Check 1c: Active workspace/dev processes should never be auto-remediated.
        if (isWorkspaceLinkedProcess(processName, pid)) {
            log.warn("Blocked: {} (PID {}) appears to be an active workspace/dev process", processName, pid);
            return PolicyViolation.protectedProcess(processName, pid, actionType)
                    .addDetail("Protected by active-workspace guard")
                    .addDetail("This process is related to current development/runtime workflow");
        }

        // Check 2: Get applicable policies for this action type
        List<SafetyPolicyEntity> policies = policyRepository.findApplicablePolicies(actionType);

        // Check 3: Iterate through policies by priority
        for (SafetyPolicyEntity policy : policies) {
            PolicyViolation violation = evaluatePolicy(policy, actionType, processName, pid, isDryRun, confidence);
            if (violation.isViolated()) {
                log.warn("Policy '{}' violated: {}", policy.getName(), violation.getReason());
                return violation;
            }
        }

        // Check 4: Rate limiting
        PolicyViolation rateLimitViolation = checkRateLimit(actionType);
        if (rateLimitViolation.isViolated()) {
            return rateLimitViolation;
        }

        log.debug("All policy checks passed for action {} on {}", actionType, processName);
        return PolicyViolation.allowed();
    }

    /**
     * Evaluate a single policy against the action context.
     */
    private PolicyViolation evaluatePolicy(SafetyPolicyEntity policy, ActionType actionType,
            String processName, int pid, boolean isDryRun,
            double confidence) {
        // Check if process is protected by this policy
        if (policy.isProcessProtected(processName)) {
            return PolicyViolation.builder()
                    .violated(true)
                    .reason("Process '" + processName + "' is protected by policy '" + policy.getName() + "'")
                    .policyName(policy.getName())
                    .severity(ViolationSeverity.HIGH)
                    .targetProcess(processName)
                    .targetPid(pid)
                    .attemptedAction(actionType)
                    .blocking(true)
                    .overridable(true)
                    .build();
        }

        // Check if process is in the allowed whitelist (if whitelist is defined)
        if (!policy.isProcessAllowed(processName)) {
            return PolicyViolation.builder()
                    .violated(true)
                    .reason("Process '" + processName + "' is not in allowed list for policy '" + policy.getName()
                            + "'")
                    .policyName(policy.getName())
                    .severity(ViolationSeverity.MEDIUM)
                    .targetProcess(processName)
                    .targetPid(pid)
                    .attemptedAction(actionType)
                    .blocking(true)
                    .overridable(true)
                    .build();
        }

        // Check dry-run mode restrictions
        if (!isDryRun && !policy.getAllowedInProduction()) {
            return PolicyViolation.builder()
                    .violated(true)
                    .reason("Action '" + actionType + "' is not allowed in production mode by policy '"
                            + policy.getName() + "'")
                    .policyName(policy.getName())
                    .severity(ViolationSeverity.HIGH)
                    .attemptedAction(actionType)
                    .blocking(true)
                    .overridable(false)
                    .suggestedAlternatives(List.of(ActionType.REDUCE_PRIORITY, ActionType.TRIM_WORKING_SET))
                    .build();
        }

        // Check confidence threshold
        if (policy.getMinConfidenceThreshold() != null && confidence < policy.getMinConfidenceThreshold()) {
            return PolicyViolation.requiresApproval(actionType,
                    String.format("AI confidence %.1f%% is below policy threshold %.1f%% - requires approval",
                            confidence * 100, policy.getMinConfidenceThreshold() * 100));
        }

        // Check if approval is required
        if (policy.getRequiresApproval() && !isDryRun) {
            return PolicyViolation.requiresApproval(actionType,
                    "Policy '" + policy.getName() + "' requires user approval for this action");
        }

        return PolicyViolation.allowed();
    }

    /**
     * Check rate limits for an action type.
     */
    private PolicyViolation checkRateLimit(ActionType actionType) {
        // Reset counters if hour has passed
        Instant now = Instant.now();
        if (now.isAfter(lastRateLimitReset.plusSeconds(3600))) {
            executionCounts.clear();
            lastRateLimitReset = now;
        }

        // Get rate-limited policies
        List<SafetyPolicyEntity> rateLimitedPolicies = policyRepository.findRateLimitedPolicies();
        for (SafetyPolicyEntity policy : rateLimitedPolicies) {
            if (policy.appliesTo(actionType) && policy.getMaxExecutionsPerHour() != null) {
                int currentCount = executionCounts
                        .computeIfAbsent(actionType, k -> new AtomicInteger(0))
                        .get();

                if (currentCount >= policy.getMaxExecutionsPerHour()) {
                    return PolicyViolation.builder()
                            .violated(true)
                            .reason(String.format("Rate limit exceeded: %d/%d executions this hour for %s",
                                    currentCount, policy.getMaxExecutionsPerHour(), actionType))
                            .policyName(policy.getName())
                            .severity(ViolationSeverity.MEDIUM)
                            .attemptedAction(actionType)
                            .blocking(true)
                            .overridable(true)
                            .build();
                }
            }
        }

        return PolicyViolation.allowed();
    }

    /**
     * Record that an action was executed (for rate limiting).
     */
    public void recordExecution(ActionType actionType) {
        executionCounts
                .computeIfAbsent(actionType, k -> new AtomicInteger(0))
                .incrementAndGet();
    }

    /**
     * Check if a process is system-critical (hardcoded protection).
     */
    private boolean isSystemProtectedProcess(String processName) {
        if (processName == null) {
            return false;
        }
        String lowerName = processName.toLowerCase();
        return systemProtectedProcesses.stream()
                .anyMatch(p -> lowerName.equalsIgnoreCase(p.trim()));
    }

    /**
     * Get all enabled policies.
     */
    public List<SafetyPolicyEntity> getAllPolicies() {
        return policyRepository.findByEnabledTrueOrderByPriorityAsc();
    }

    /**
     * Get a policy by name.
     */
    public Optional<SafetyPolicyEntity> getPolicyByName(String name) {
        return policyRepository.findByName(name);
    }

    /**
     * Create or update a policy.
     */
    @Transactional
    public SafetyPolicyEntity savePolicy(SafetyPolicyEntity policy) {
        log.info("Saving policy: {}", policy.getName());
        return policyRepository.save(policy);
    }

    /**
     * Delete a policy by ID.
     */
    @Transactional
    public void deletePolicy(Long id) {
        log.info("Deleting policy with ID: {}", id);
        policyRepository.deleteById(id);
    }

    /**
     * Enable or disable a policy.
     */
    @Transactional
    public void setPolicyEnabled(Long id, boolean enabled) {
        policyRepository.findById(id).ifPresent(policy -> {
            policy.setEnabled(enabled);
            policyRepository.save(policy);
            log.info("Policy '{}' enabled={}", policy.getName(), enabled);
        });
    }

    /**
     * Add a protected process pattern to a policy.
     */
    @Transactional
    public void addProtectedProcess(Long policyId, String pattern) {
        policyRepository.findById(policyId).ifPresent(policy -> {
            policy.getProtectedProcessPatterns().add(pattern);
            policyRepository.save(policy);
            log.info("Added protected pattern '{}' to policy '{}'", pattern, policy.getName());
        });
    }

    /**
     * Get the list of system-protected processes.
     */
    public List<String> getSystemProtectedProcesses() {
        return new ArrayList<>(systemProtectedProcesses);
    }

    /**
     * Initialize default policies if database is empty.
     */
    @Transactional
    public void initializeDefaultPolicies() {
        if (policyRepository.count() == 0) {
            log.info("Initializing default safety policies...");

            // Policy 1: Kill process requires high confidence or approval
            SafetyPolicyEntity killPolicy = SafetyPolicyEntity.builder()
                    .name("KILL_PROCESS_POLICY")
                    .description("Requires high confidence or approval to kill processes")
                    .actionType(ActionType.KILL_PROCESS)
                    .safetyLevel(SafetyLevel.HIGH)
                    .requiresApproval(false)
                    .minConfidenceThreshold(0.8)
                    .allowedInDryRun(true)
                    .allowedInProduction(true)
                    .maxExecutionsPerHour(10)
                    .priority(10)
                    .protectedProcessPatterns(List.of("explorer.exe", "taskmgr.exe"))
                    .createdBy("system")
                    .build();
            policyRepository.save(killPolicy);

            // Policy 2: Low-risk actions allowed with lower confidence
            SafetyPolicyEntity lowRiskPolicy = SafetyPolicyEntity.builder()
                    .name("LOW_RISK_ACTIONS_POLICY")
                    .description("Lower confidence threshold for low-risk actions")
                    .actionType(ActionType.REDUCE_PRIORITY)
                    .safetyLevel(SafetyLevel.LOW)
                    .requiresApproval(false)
                    .minConfidenceThreshold(0.5)
                    .allowedInDryRun(true)
                    .allowedInProduction(true)
                    .maxExecutionsPerHour(50)
                    .priority(50)
                    .createdBy("system")
                    .build();
            policyRepository.save(lowRiskPolicy);

            // Policy 3: Trim working set - very low risk
            SafetyPolicyEntity trimPolicy = SafetyPolicyEntity.builder()
                    .name("TRIM_MEMORY_POLICY")
                    .description("Memory trimming allowed with minimal restrictions")
                    .actionType(ActionType.TRIM_WORKING_SET)
                    .safetyLevel(SafetyLevel.LOW)
                    .requiresApproval(false)
                    .minConfidenceThreshold(0.3)
                    .allowedInDryRun(true)
                    .allowedInProduction(true)
                    .priority(100)
                    .createdBy("system")
                    .build();
            policyRepository.save(trimPolicy);

            // Policy 4: Service restart - requires approval in production
            SafetyPolicyEntity restartPolicy = SafetyPolicyEntity.builder()
                    .name("RESTART_SERVICE_POLICY")
                    .description("Service restarts require approval in production")
                    .actionType(ActionType.RESTART_SERVICE)
                    .safetyLevel(SafetyLevel.HIGH)
                    .requiresApproval(true)
                    .minConfidenceThreshold(0.9)
                    .allowedInDryRun(true)
                    .allowedInProduction(true)
                    .maxExecutionsPerHour(5)
                    .priority(5)
                    .createdBy("system")
                    .build();
            policyRepository.save(restartPolicy);

            log.info("Created 4 default safety policies");
        }
    }

    /**
     * Reset rate limit counters (for testing or manual reset).
     */
    public void resetRateLimits() {
        executionCounts.clear();
        lastRateLimitReset = Instant.now();
        log.info("Rate limit counters reset");
    }

    /**
     * Get current execution counts for rate limiting.
     */
    public ConcurrentHashMap<ActionType, AtomicInteger> getExecutionCounts() {
        return new ConcurrentHashMap<>(executionCounts);
    }

    /**
     * Check if a process is protected (for RuleEngine).
     */
    public boolean isProtected(String processName) {
        return isProtected(processName, null);
    }

    /**
     * Check if a process is protected using name and optional PID context.
     */
    public boolean isProtected(String processName, Integer pid) {
        int safePid = pid == null ? -1 : pid;
        return isSystemProtectedProcess(processName)
                || settingsService.isProcessProtected(processName)
                || isWorkspaceLinkedProcess(processName, safePid);
    }

    /**
     * Check if an action is safe to execute on an issue (for RuleEngine).
     */
    public boolean isSafe(com.aios.backend.model.IssueEntity issue, String actionTypeStr) {
        try {
            ActionType actionType = ActionType.valueOf(actionTypeStr);
            PolicyViolation violation = checkPolicy(
                    actionType,
                    issue.getProcessName(),
                    issue.getAffectedPid(),
                    false, // Not dry run
                    issue.getConfidence());
            return !violation.isViolated();
        } catch (Exception e) {
            log.error("Error checking safety for action {}: {}", actionTypeStr, e.getMessage());
            return false;
        }
    }

    private boolean isWorkspaceLinkedProcess(String processName, int pid) {
        if (!protectActiveWorkspaceProcesses) {
            return false;
        }

        String normalizedName = normalizeProcessName(processName);
        boolean devNameMatch = DEV_PROCESS_NAMES.contains(normalizedName);

        if (pid <= 0) {
            return devNameMatch;
        }

        try {
            var processInfo = agentClient.getProcessInfo(pid);
            String commandLine = String.valueOf(processInfo.getOrDefault("commandLine", "")).toLowerCase();
            String workingDirectory = String.valueOf(processInfo.getOrDefault("workingDirectory", "")).toLowerCase();
            String combined = commandLine + " " + workingDirectory;

            if (!workspaceMarkers.isBlank()) {
                String[] markers = workspaceMarkers.split(",");
                for (String marker : markers) {
                    String token = marker.trim().toLowerCase();
                    if (!token.isEmpty() && combined.contains(token)) {
                        return true;
                    }
                }
            }

            return devNameMatch;
        } catch (Exception ex) {
            log.debug("Could not inspect process context for PID {}. Falling back to name-based protection.", pid, ex);
            return devNameMatch;
        }
    }

    private String normalizeProcessName(String processName) {
        if (processName == null) {
            return "";
        }
        return processName
                .trim()
                .toLowerCase()
                .replace(".exe", "");
    }
}
