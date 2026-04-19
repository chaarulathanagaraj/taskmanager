package com.aios.backend.client;

import com.aios.shared.client.AgentClient;
import com.aios.shared.dto.ActionResult;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of AgentClient using WebClient to call the agent service REST
 * API.
 */
@Component
@Slf4j
public class AgentClientImpl implements AgentClient {

    private final WebClient agentClient;
    private final WebClient mcpClient;
    private final ObjectMapper objectMapper;

    public AgentClientImpl(
            @Value("${aios.agent.url:http://localhost:8081}") String agentUrl,
            @Value("${aios.mcp.url:http://localhost:8082}") String mcpUrl,
            @Value("${aios.mcp.api-key:}") String mcpApiKey,
            ObjectMapper objectMapper) {
        this.agentClient = WebClient.builder()
                .baseUrl(agentUrl)
                .build();
        this.mcpClient = WebClient.builder()
                .baseUrl(mcpUrl)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("X-API-Key", mcpApiKey)
                .build();
        this.objectMapper = objectMapper;
        log.info("AgentClient initialized with agent URL: {} and MCP URL: {}", agentUrl, mcpUrl);
    }

    @Override
    public ActionResult executeAction(String actionType, Map<String, Object> parameters) {
        log.info("Executing action {} with parameters: {}", actionType, parameters);

        try {
            if ("KILL_PROCESS".equals(actionType)) {
                McpToolResponse response = executeMcpTool("kill_process", parameters);
                if (response.success && response.result != null) {
                    return ActionResult.success(
                            response.result.path("message").asText("Process termination requested"),
                            Map.of(
                                    "tool", "kill_process",
                                    "result", response.result,
                                    "steps", buildExecutionSteps("kill_process", response.result)));
                }
                return ActionResult.failure(
                        buildMcpFailureMessage("KILL_PROCESS", parameters, response.error, response.errorCode),
                        response.error != null ? response.error : "Unknown MCP error");
            }

            if ("SUSPEND_PROCESS".equals(actionType)) {
                McpToolResponse response = executeMcpTool("suspend_process", parameters);
                if (response.success && response.result != null) {
                    return ActionResult.success(
                            response.result.path("message").asText("Process suspended successfully"),
                            Map.of(
                                    "tool", "suspend_process",
                                    "result", response.result,
                                    "steps", buildExecutionSteps("suspend_process", response.result)));
                }
                return ActionResult.failure(
                        buildMcpFailureMessage("SUSPEND_PROCESS", parameters, response.error, response.errorCode),
                        response.error != null ? response.error : "Unknown MCP error");
            }

            if ("REDUCE_PRIORITY".equals(actionType)) {
                Map<String, Object> toolParams = new HashMap<>(parameters);
                toolParams.putIfAbsent("targetPriority", "BELOW_NORMAL");
                McpToolResponse response = executeMcpTool("reduce_priority", toolParams);
                if (response.success && response.result != null) {
                    return ActionResult.success(
                            response.result.path("message").asText("Priority reduced successfully"),
                            Map.of(
                                    "tool", "reduce_priority",
                                    "result", response.result,
                                    "steps", buildExecutionSteps("reduce_priority", response.result)));
                }
                return ActionResult.failure(
                        buildMcpFailureMessage("REDUCE_PRIORITY", parameters, response.error, response.errorCode),
                        response.error != null ? response.error : "Unknown MCP error");
            }

            if ("TRIM_WORKING_SET".equals(actionType)) {
                McpToolResponse response = executeMcpTool("trim_working_set", parameters);
                if (response.success && response.result != null) {
                    return ActionResult.success(
                            response.result.path("message").asText("Working set trimmed successfully"),
                            Map.of(
                                    "tool", "trim_working_set",
                                    "result", response.result,
                                    "steps", buildExecutionSteps("trim_working_set", response.result)));
                }
                return ActionResult.failure(
                        buildMcpFailureMessage("TRIM_WORKING_SET", parameters, response.error, response.errorCode),
                        response.error != null ? response.error : "Unknown MCP error");
            }

            if ("RESTART_PROCESS".equals(actionType)) {
                McpToolResponse response = executeMcpTool("restart_process", parameters);
                if (response.success && response.result != null) {
                    return ActionResult.success(
                            response.result.path("message").asText("Process restarted successfully"),
                            Map.of(
                                    "tool", "restart_process",
                                    "result", response.result,
                                    "steps", buildExecutionSteps("restart_process", response.result)));
                }
                return ActionResult.failure(
                        buildMcpFailureMessage("RESTART_PROCESS", parameters, response.error, response.errorCode),
                        response.error != null ? response.error : "Unknown MCP error");
            }

            if ("SET_PRIORITY".equals(actionType)) {
                McpToolResponse response = executeMcpTool("reduce_priority", parameters);
                if (response.success && response.result != null) {
                    return ActionResult.success(
                            response.result.path("message").asText("Priority updated successfully"),
                            Map.of("tool", "reduce_priority", "result", response.result));
                }
                return ActionResult.failure(
                        buildMcpFailureMessage("SET_PRIORITY", parameters, response.error, response.errorCode),
                        response.error != null ? response.error : "Unknown MCP error");
            }

            return ActionResult.failure("Unsupported action type: " + actionType);

        } catch (Exception e) {
            log.error("Failed to execute action {}: {}", actionType, e.getMessage(), e);
            return ActionResult.failure("Failed to execute action: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getProcessInfo(int pid) {
        log.info("Fetching process info for PID {}", pid);

        try {
            McpToolResponse response = executeMcpTool("get_process_list", Map.of("pid", pid, "limit", 1));
            if (!response.success || response.result == null || !response.result.has("processes")
                    || response.result.get("processes").isEmpty()) {
                return Map.of("error", response.error != null ? response.error : "Process not found");
            }

            JsonNode process = response.result.get("processes").get(0);
            Map<String, Object> processInfo = new HashMap<>();
            processInfo.put("pid", process.path("pid").asInt(pid));
            processInfo.put("name", process.path("name").asText("unknown"));
            processInfo.put("memoryBytes", process.path("memoryBytes").asLong(0L));
            processInfo.put("memoryMB", process.path("memoryMB").asLong(0L));
            processInfo.put("cpuPercent", process.path("cpuPercent").asDouble(0.0));
            processInfo.put("state", process.path("state").asText("unknown"));
            processInfo.put("threadCount", process.path("threadCount").asInt(0));
            processInfo.put("priority", process.path("priority").asInt(0));
            processInfo.put("commandLine", process.path("commandLine").asText(""));
            processInfo.put("workingDirectory", process.path("path").asText(""));
            return processInfo;

        } catch (Exception e) {
            log.error("Failed to get process info for PID {}: {}", pid, e.getMessage(), e);
            return Map.of("error", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> diagnoseResolutionFailure(String actionType, int pid, String processName,
            String errorMessage) {
        Map<String, Object> diagnostics = new HashMap<>();
        diagnostics.put("actionType", actionType);
        diagnostics.put("pid", pid);
        diagnostics.put("processName", processName == null ? "" : processName);
        diagnostics.put("errorMessage", errorMessage == null ? "" : errorMessage);
        diagnostics.put("source", "agent+mcp");
        diagnostics.put("timestamp", java.time.Instant.now().toString());

        try {
            McpToolResponse processSnapshot = executeMcpTool("get_process_list", Map.of("pid", pid, "limit", 1));
            diagnostics.put("processSnapshotSuccess", processSnapshot.success);

            if (processSnapshot.result != null) {
                diagnostics.put("processSnapshot", processSnapshot.result);
            }

            String rawSignal = ((errorMessage == null ? "" : errorMessage) + " "
                    + (processSnapshot.error == null ? "" : processSnapshot.error)).toLowerCase();

            String failureCategory = classifyFailureCategory(rawSignal, processSnapshot);
            diagnostics.put("failureCategory", failureCategory);
            diagnostics.put("retryable", isRetryable(failureCategory));
            diagnostics.put("explanation", buildHumanExplanation(failureCategory, actionType, pid, processName));

            // Pull a few recent error events for additional context; ignore failures here.
            try {
                McpToolResponse eventLogResponse = executeMcpTool(
                        "read_event_log",
                        Map.of("logName", "System", "eventType", "Error", "limit", 5));
                if (eventLogResponse.success && eventLogResponse.result != null) {
                    diagnostics.put("recentSystemErrors", eventLogResponse.result);
                }
            } catch (Exception ignored) {
                log.debug("Unable to enrich diagnostics with event log for PID {}", pid);
            }

            return diagnostics;
        } catch (Exception e) {
            diagnostics.put("failureCategory", "unknown_process_state");
            diagnostics.put("retryable", Boolean.TRUE);
            diagnostics.put("explanation",
                    "Resolution verification failed and diagnostics could not be collected from MCP.");
            diagnostics.put("diagnosticError", e.getMessage());
            return diagnostics;
        }
    }

    private String classifyFailureCategory(String rawSignal, McpToolResponse processSnapshot) {
        if (rawSignal.contains("permission") || rawSignal.contains("access is denied")
                || rawSignal.contains("unauthorized")) {
            return "insufficient_privileges";
        }
        if (rawSignal.contains("protected system process") || rawSignal.contains("protected")) {
            return "permission_denied";
        }
        if (rawSignal.contains("in use") || rawSignal.contains("locked") || rawSignal.contains("busy")) {
            return "process_locked_by_os";
        }
        if (rawSignal.contains("dependency") || rawSignal.contains("module") || rawSignal.contains("service")) {
            return "dependency_conflict";
        }
        if (processSnapshot != null && processSnapshot.success && processSnapshot.result != null
                && processSnapshot.result.has("processes") && processSnapshot.result.get("processes").isArray()) {
            List<?> processes = objectMapper.convertValue(processSnapshot.result.get("processes"), List.class);
            if (processes.isEmpty()) {
                return "unknown_process_state";
            }
        }
        if (rawSignal.contains("not found") || rawSignal.contains("does not exist")) {
            return "unknown_process_state";
        }
        return "unknown_process_state";
    }

    private boolean isRetryable(String category) {
        return switch (category) {
            case "permission_denied", "insufficient_privileges", "process_locked_by_os", "dependency_conflict" -> false;
            default -> true;
        };
    }

    private String buildHumanExplanation(String category, String actionType, int pid, String processName) {
        String target = (processName == null || processName.isBlank())
                ? "PID " + pid
                : processName + " (PID " + pid + ")";
        return switch (category) {
            case "permission_denied" -> "Permission denied while trying to apply " + actionType + " on " + target
                    + ". The process appears protected by OS or policy.";
            case "insufficient_privileges" -> "Insufficient privileges to complete " + actionType + " on " + target
                    + ". Run agent services with elevated rights.";
            case "process_locked_by_os" -> "The process appears locked by the OS, so " + actionType
                    + " could not finish reliably for " + target + ".";
            case "dependency_conflict" ->
                "A dependency conflict prevented " + actionType + " from stabilizing " + target
                        + ". Related services or modules may need restart order handling.";
            default -> "The process state is unknown after " + actionType + " for " + target
                    + ". Re-check process telemetry and retry after a short interval.";
        };
    }

    private McpToolResponse executeMcpTool(String toolName, Map<String, Object> parameters) throws Exception {
        String rawResponse = mcpClient.post()
                .uri("/api/mcp/tools/{toolName}/execute", toolName)
                .bodyValue(parameters)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(java.time.Duration.ofSeconds(20))
                .block();

        if (rawResponse == null || rawResponse.isBlank()) {
            McpToolResponse empty = new McpToolResponse();
            empty.success = false;
            empty.error = "Empty MCP response";
            return empty;
        }

        return objectMapper.readValue(rawResponse, McpToolResponse.class);
    }

    private java.util.List<String> buildExecutionSteps(String toolName, JsonNode result) {
        java.util.List<String> steps = new java.util.ArrayList<>();

        switch (toolName) {
            case "kill_process" -> {
                steps.add("Validated the target PID before execution");
                steps.add("Checked for protected system process restrictions");
                steps.add("Issued the process termination request to MCP");
                steps.add("Captured the MCP status and message for confirmation");
            }
            case "suspend_process" -> {
                steps.add("Validated the target PID before execution");
                steps.add("Checked for protected system process restrictions");
                steps.add("Issued the process suspension request to MCP");
                steps.add("Captured the MCP status and message for confirmation");
            }
            case "reduce_priority" -> {
                steps.add("Validated the target PID before execution");
                steps.add("Read the current priority from the process");
                steps.add("Applied the requested priority change through MCP");
                steps.add("Captured the old and new priority values");
            }
            case "trim_working_set" -> {
                steps.add("Validated the target PID before execution");
                steps.add("Captured working set memory before trimming");
                steps.add("Invoked the working set trim operation through MCP");
                steps.add("Captured memory usage after the trim operation");
            }
            case "restart_process" -> {
                steps.add("Validated the target PID before execution");
                steps.add("Captured the executable path and command line");
                steps.add("Stopped the original process instance");
                steps.add("Started a replacement process instance");
                steps.add("Captured the new PID returned by MCP");
            }
            default -> steps.add("Executed MCP tool: " + toolName);
        }

        if (result != null && result.has("status")) {
            steps.add("Final MCP status: " + result.path("status").asText("unknown"));
        }

        if (result != null && result.has("message")) {
            steps.add("Agent message: " + result.path("message").asText(""));
        }

        return steps;
    }

    private String buildMcpFailureMessage(String actionType, Map<String, Object> parameters, String rawError,
            String rawErrorCode) {
        int pid = parsePid(parameters.get("pid"));
        String processName = extractProcessName(parameters, pid);
        String normalizedError = rawError == null ? "UNKNOWN" : rawError.trim();
        String upperError = normalizedError.toUpperCase();
        String upperErrorCode = rawErrorCode == null ? "" : rawErrorCode.trim().toUpperCase();
        boolean isReducePriority = "REDUCE_PRIORITY".equalsIgnoreCase(actionType)
                || "SET_PRIORITY".equalsIgnoreCase(actionType);

        String actionLabel = formatActionName(actionType);

        if (upperErrorCode.contains("RESOURCE_NOT_FOUND") || upperError.contains("RESOURCE_NOT_FOUND")) {
            return String.format(
                    "%s failed for %s (PID: %d): target process was not found. It may have already exited.",
                    actionLabel, processName, pid);
        }

        if (isReducePriority && (upperErrorCode.contains("PROTECTED_RESOURCE")
                || upperError.contains("PROTECTED")
                || upperError.contains("CRITICAL PROCESS")
                || upperError.contains("SYSTEM PROCESS"))) {
            return String.format(
                    "%s failed for %s (PID: %d): this is a protected/system process and Windows blocked priority changes.",
                    actionLabel, processName, pid);
        }

        if (isReducePriority && (upperErrorCode.contains("PERMISSION_DENIED")
                || upperError.contains("ACCESS_DENIED")
                || upperError.contains("ACCESS DENIED")
                || upperError.contains("PERMISSION")
                || upperError.contains("PRIVILEGE"))) {
            return String.format(
                    "%s failed for %s (PID: %d): access denied. Run backend and MCP services as Administrator, then retry.",
                    actionLabel, processName, pid);
        }

        if (isReducePriority
                && (upperErrorCode.contains("OPERATION_FAILED") || upperError.contains("OPERATION_FAILED"))) {
            return String.format(
                    "%s failed for %s (PID: %d): Windows rejected this operation for the selected process. It may be protected or require higher privileges.",
                    actionLabel, processName, pid);
        }

        return String.format(
                "%s failed for %s (PID: %d): %s",
                actionLabel, processName, pid, normalizedError);
    }

    private int parsePid(Object pidValue) {
        if (pidValue instanceof Number number) {
            return number.intValue();
        }
        if (pidValue instanceof String pidText) {
            try {
                return Integer.parseInt(pidText);
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private String extractProcessName(Map<String, Object> parameters, int pid) {
        Object processNameObj = parameters.get("processName");
        if (processNameObj instanceof String processName && !processName.isBlank()) {
            return processName;
        }
        return pid > 0 ? "Process" : "Unknown process";
    }

    private String formatActionName(String actionType) {
        if (actionType == null || actionType.isBlank()) {
            return "Action";
        }

        StringBuilder formatted = new StringBuilder();
        for (String token : actionType.split("_")) {
            if (token.isBlank()) {
                continue;
            }
            String lower = token.toLowerCase();
            formatted.append(Character.toUpperCase(lower.charAt(0)))
                    .append(lower.substring(1))
                    .append(' ');
        }
        return formatted.toString().trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class McpToolResponse {
        public boolean success;
        public JsonNode result;
        public String error;
        public String errorCode;
    }
}
