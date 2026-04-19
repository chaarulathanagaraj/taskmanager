package com.aios.backend.service;

import com.aios.backend.service.ProcessClassifier.ProcessClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Defines action safety matrix: which actions are safe for each process class.
 * Provides intelligent fallback action chains based on process type and issue
 * type.
 */
@Service
@Slf4j
public class ActionSafetyMatrix {

    /**
     * Safety decision for an action on a process class.
     */
    public record ActionDecision(
            boolean allowed, // Can action be executed?
            String reason, // Why allowed/disallowed
            List<String> fallbacks, // Alternative actions to try
            boolean requiresElevation // Needs admin privileges
    ) {
    }

    /**
     * Action to attempt for a process class and issue type.
     */
    public record ActionRecommendation(
            String primaryAction,
            List<String> fallbackChain,
            String rationale) {
    }

    private final Map<ProcessClass, ActionDecision> actionMatrix = new HashMap<>();

    /**
     * Build safety matrix for each action type per process class.
     */
    public ActionDecision canExecute(String actionType, ProcessClass processClass) {
        if (actionType == null || processClass == null) {
            return new ActionDecision(false, "Invalid action or process class", List.of(), false);
        }

        return switch (processClass) {
            case SYSTEM_CRITICAL -> handleSystemCritical(actionType);
            case SECURITY_PROCESS -> handleSecurityProcess(actionType);
            case WINDOWS_SERVICE -> handleWindowsService(actionType);
            case USER_PROCESS -> handleUserProcess(actionType);
            case TEMP_PROCESS -> handleTempProcess(actionType);
            case UNKNOWN -> handleUnknown(actionType);
        };
    }

    /**
     * System-critical processes: NO actions should be attempted.
     */
    private ActionDecision handleSystemCritical(String actionType) {
        return new ActionDecision(
                false,
                "SYSTEM_CRITICAL: Core OS process - no actions permitted",
                List.of(),
                false);
    }

    /**
     * Security processes: NO restart, but allow non-destructive actions.
     */
    private ActionDecision handleSecurityProcess(String actionType) {
        if ("RESTART_PROCESS".equals(actionType) || "KILL_PROCESS".equals(actionType)) {
            return new ActionDecision(
                    false,
                    "SECURITY_PROCESS: Never restart or kill security processes",
                    List.of("TRIM_WORKING_SET", "REDUCE_PRIORITY"),
                    true);
        }

        if ("TRIM_WORKING_SET".equals(actionType) || "REDUCE_PRIORITY".equals(actionType)) {
            return new ActionDecision(
                    true,
                    "SECURITY_PROCESS: Non-destructive action allowed",
                    List.of(),
                    true);
        }

        return new ActionDecision(
                false,
                "SECURITY_PROCESS: Action not recommended for security process",
                List.of("TRIM_WORKING_SET", "REDUCE_PRIORITY"),
                true);
    }

    /**
     * Windows services: Require service manager for restart, not process kill.
     */
    private ActionDecision handleWindowsService(String actionType) {
        if ("RESTART_PROCESS".equals(actionType)) {
            return new ActionDecision(
                    false,
                    "WINDOWS_SERVICE: Process restart will fail; use Restart-Service instead",
                    List.of("TRIM_WORKING_SET", "REDUCE_PRIORITY"),
                    true);
        }

        if ("KILL_PROCESS".equals(actionType)) {
            return new ActionDecision(
                    false,
                    "WINDOWS_SERVICE: Cannot kill service processes",
                    List.of("TRIM_WORKING_SET", "REDUCE_PRIORITY"),
                    true);
        }

        if ("TRIM_WORKING_SET".equals(actionType) || "REDUCE_PRIORITY".equals(actionType)) {
            return new ActionDecision(
                    true,
                    "WINDOWS_SERVICE: Non-destructive action allowed",
                    List.of(),
                    true);
        }

        return new ActionDecision(
                false,
                "WINDOWS_SERVICE: Action not applicable to service process",
                List.of("TRIM_WORKING_SET", "REDUCE_PRIORITY"),
                true);
    }

    /**
     * User processes: All actions available.
     */
    private ActionDecision handleUserProcess(String actionType) {
        return new ActionDecision(
                true,
                "USER_PROCESS: All actions available",
                List.of(),
                true);
    }

    /**
     * Temporary processes: Ignore - likely already gone or harmless.
     */
    private ActionDecision handleTempProcess(String actionType) {
        return new ActionDecision(
                false,
                "TEMP_PROCESS: Ephemeral process - no action needed",
                List.of(),
                false);
    }

    /**
     * Unknown: Conservative - only allow non-destructive actions.
     */
    private ActionDecision handleUnknown(String actionType) {
        if ("TRIM_WORKING_SET".equals(actionType) || "REDUCE_PRIORITY".equals(actionType)) {
            return new ActionDecision(
                    true,
                    "UNKNOWN: Conservative - non-destructive action allowed",
                    List.of(),
                    true);
        }

        return new ActionDecision(
                false,
                "UNKNOWN: Unable to classify - conservative approach",
                List.of("TRIM_WORKING_SET", "REDUCE_PRIORITY"),
                true);
    }

    /**
     * Get recommended action for an issue type and process class.
     */
    public ActionRecommendation getRecommendedActions(
            String issueType,
            ProcessClass processClass) {

        return switch (processClass) {
            case SYSTEM_CRITICAL ->
                new ActionRecommendation(
                        null,
                        List.of(),
                        "System-critical: Skip automation - manual review required");

            case SECURITY_PROCESS ->
                buildSecurityActionPlan(issueType);

            case WINDOWS_SERVICE ->
                buildServiceActionPlan(issueType);

            case USER_PROCESS ->
                buildUserActionPlan(issueType);

            case TEMP_PROCESS ->
                new ActionRecommendation(
                        null,
                        List.of(),
                        "Temporary process: Ignore - likely already gone");

            case UNKNOWN ->
                buildConservativeActionPlan(issueType);
        };
    }

    /**
     * Build action plan for security processes (avoid restart).
     */
    private ActionRecommendation buildSecurityActionPlan(String issueType) {
        if ("MEMORY_LEAK".equals(issueType)) {
            return new ActionRecommendation(
                    "TRIM_WORKING_SET",
                    List.of("REDUCE_PRIORITY"),
                    "Security process memory leak: use memory trimming first");
        }

        if ("RESOURCE_HOG".equals(issueType)) {
            return new ActionRecommendation(
                    "REDUCE_PRIORITY",
                    List.of("TRIM_WORKING_SET"),
                    "Security process resource hog: reduce priority");
        }

        return new ActionRecommendation(
                "TRIM_WORKING_SET",
                List.of("REDUCE_PRIORITY"),
                "Security process: use non-destructive actions only");
    }

    /**
     * Build action plan for Windows services (don't use process restart).
     */
    private ActionRecommendation buildServiceActionPlan(String issueType) {
        if ("MEMORY_LEAK".equals(issueType)) {
            return new ActionRecommendation(
                    "TRIM_WORKING_SET",
                    List.of("REDUCE_PRIORITY"),
                    "Service memory leak: trim working set first; avoid restart");
        }

        if ("RESOURCE_HOG".equals(issueType)) {
            return new ActionRecommendation(
                    "REDUCE_PRIORITY",
                    List.of("TRIM_WORKING_SET"),
                    "Service resource hog: reduce priority; use SC restart if needed");
        }

        return new ActionRecommendation(
                "TRIM_WORKING_SET",
                List.of("REDUCE_PRIORITY"),
                "Service process: use memory/priority management; avoid restart");
    }

    /**
     * Build action plan for normal user processes.
     */
    private ActionRecommendation buildUserActionPlan(String issueType) {
        if ("MEMORY_LEAK".equals(issueType)) {
            return new ActionRecommendation(
                    "TRIM_WORKING_SET",
                    List.of("REDUCE_PRIORITY", "RESTART_PROCESS"),
                    "User process memory leak: trim first, then restart if needed");
        }

        if ("RESOURCE_HOG".equals(issueType)) {
            return new ActionRecommendation(
                    "REDUCE_PRIORITY",
                    List.of("TRIM_WORKING_SET", "RESTART_PROCESS"),
                    "User process resource hog: reduce priority, trim memory, restart");
        }

        if ("HUNG_PROCESS".equals(issueType)) {
            return new ActionRecommendation(
                    "RESTART_PROCESS",
                    List.of("REDUCE_PRIORITY", "TRIM_WORKING_SET"),
                    "Hung user process: restart process");
        }

        return new ActionRecommendation(
                "TRIM_WORKING_SET",
                List.of("REDUCE_PRIORITY", "RESTART_PROCESS"),
                "User process: standard remediation chain");
    }

    /**
     * Build conservative action plan for unknown processes.
     */
    private ActionRecommendation buildConservativeActionPlan(String issueType) {
        return new ActionRecommendation(
                "TRIM_WORKING_SET",
                List.of("REDUCE_PRIORITY"),
                "Unknown process class: conservative non-destructive actions only");
    }

    /**
     * Validate if a specific action is safe for a process class.
     */
    public boolean isActionSafeFor(String actionType, ProcessClass processClass) {
        return canExecute(actionType, processClass).allowed();
    }

    /**
     * Get the best safe action from a list for a process class.
     */
    public String getFirstSafeAction(List<String> actions, ProcessClass processClass) {
        for (String action : actions) {
            if (isActionSafeFor(action, processClass)) {
                return action;
            }
        }
        return null;
    }
}
