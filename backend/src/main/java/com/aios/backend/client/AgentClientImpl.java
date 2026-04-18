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
                        "MCP kill_process failed",
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
                        "MCP reduce_priority failed",
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
                        "MCP trim_working_set failed",
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
                        "MCP restart_process failed",
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
                        "MCP reduce_priority failed for SET_PRIORITY",
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class McpToolResponse {
        public boolean success;
        public JsonNode result;
        public String error;
    }
}
