package com.aios.ai.service;

import com.aios.shared.dto.ProcessInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Service for calling MCP tools from AI agents.
 * Wraps MCP server REST calls for easy consumption.
 */
@Service
@Slf4j
public class McpToolService {

    private final WebClient mcpClient;
    private final ObjectMapper objectMapper;

    public McpToolService(
            @Value("${mcp.server.url:http://localhost:8081}") String mcpUrl,
            @Value("${aios.mcp.api-key:}") String apiKey,
            ObjectMapper objectMapper) {

        this.mcpClient = WebClient.builder()
                .baseUrl(mcpUrl)
                .defaultHeader("X-API-Key", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        this.objectMapper = objectMapper;

        log.info("McpToolService initialized with MCP URL: {}", mcpUrl);
    }

    /**
     * Get information about a specific process.
     */
    public ProcessInfo getProcessInfo(int pid) {
        try {
            JsonNode response = executeTool("get_process_list", Map.of(
                    "limit", 1000,
                    "pid", pid));

            if (response != null && response.has("processes")) {
                for (JsonNode proc : response.get("processes")) {
                    if (proc.get("pid").asInt() == pid) {
                        return ProcessInfo.builder()
                                .pid(proc.get("pid").asInt())
                                .name(proc.get("name").asText())
                                .cpuPercent(proc.get("cpuPercent").asDouble())
                                .memoryBytes(proc.get("memoryBytes").asLong())
                                .threadCount(proc.get("threadCount").asInt())
                                .build();
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to get process info for PID {}: {}", pid, e.getMessage());
            return null;
        }
    }

    /**
     * Get thread information for a process.
     */
    public JsonNode getThreads(int pid) {
        return executeTool("get_process_threads", Map.of("pid", pid));
    }

    /**
     * Get I/O statistics.
     */
    public JsonNode getIOStats() {
        return executeTool("get_io_stats", Map.of(
                "includePartitions", true,
                "includeFileSystems", true));
    }

    /**
     * Get performance counters.
     */
    public JsonNode getPerformanceCounters(String category, boolean detailed) {
        return executeTool("get_performance_counters", Map.of(
                "category", category,
                "detailed", detailed));
    }

    /**
     * Read event log entries.
     */
    public JsonNode readEventLog(String logName, int limit, String eventType) {
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("logName", logName);
        params.put("limit", limit);
        if (eventType != null) {
            params.put("eventType", eventType);
        }
        return executeTool("read_event_log", params);
    }

    /**
     * Get list of top processes.
     */
    public List<ProcessInfo> getTopProcesses(int limit, String sortBy) {
        try {
            JsonNode response = executeTool("get_process_list", Map.of(
                    "limit", limit,
                    "sortBy", sortBy));

            if (response != null && response.has("processes")) {
                return objectMapper.convertValue(
                        response.get("processes"),
                        new TypeReference<List<ProcessInfo>>() {
                        });
            }
            return List.of();
        } catch (Exception e) {
            log.error("Failed to get top processes: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Execute an MCP tool and return the result.
     */
    private JsonNode executeTool(String toolName, Map<String, Object> parameters) {
        try {
            log.info("Executing MCP tool: {} with parameters: {}", toolName, parameters);

            // First, get raw response as String to debug deserialization
            String rawResponse = mcpClient.post()
                    .uri("/api/mcp/tools/{toolName}/execute", toolName)
                    .bodyValue(parameters)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(30))
                    .block();

            log.info("=== RAW MCP RESPONSE for {} ===\n{}", toolName, rawResponse);

            // Try to parse as Map to see actual fields
            Map<String, Object> responseMap = objectMapper.readValue(rawResponse,
                    new TypeReference<Map<String, Object>>() {
                    });
            log.info("MCP Response fields: {}", responseMap.keySet());
            log.info("Success field value: {}", responseMap.get("success"));

            // Now try to deserialize to McpToolResponse
            McpToolResponse response = objectMapper.readValue(rawResponse, McpToolResponse.class);

            if (response != null && response.isSuccess()) {
                log.debug("MCP tool {} executed successfully", toolName);
                return response.getResult();
            } else if (response != null) {
                log.warn("MCP tool {} failed: {}", toolName, response.getError());
            } else {
                log.warn("MCP tool {} returned null response", toolName);
            }
            return null;
        } catch (Exception e) {
            log.error("Error executing MCP tool {}: {} - {}", toolName, e.getClass().getName(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Response wrapper for MCP tool execution.
     */
    @lombok.Data
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private static class McpToolResponse {
        private String tool;
        private JsonNode result;
        private boolean success;
        private String error;
        private String errorCode;
    }
}
