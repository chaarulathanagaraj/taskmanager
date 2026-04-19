package com.aios.mcp.controller;

import com.aios.mcp.dto.McpToolInfo;
import com.aios.mcp.dto.McpToolResponse;
import com.aios.mcp.tools.McpTool;
import com.aios.mcp.tools.McpToolException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * REST Controller for MCP (Model Context Protocol) tool operations.
 * Provides endpoints for listing available tools and executing them.
 */
@RestController
@RequestMapping("/api/mcp")
@CrossOrigin(origins = "*")
public class McpController {

    private final Map<String, McpTool> toolsMap;

    public McpController(List<McpTool> tools) {
        this.toolsMap = tools.stream()
                .collect(Collectors.toMap(
                        McpTool::getName,
                        Function.identity()));
    }

    /**
     * List all available MCP tools.
     */
    @GetMapping("/tools")
    public ResponseEntity<List<McpToolInfo>> listTools() {
        List<McpToolInfo> toolInfos = toolsMap.values().stream()
                .map(McpToolInfo::from)
                .sorted((a, b) -> a.getName().compareTo(b.getName()))
                .toList();
        return ResponseEntity.ok(toolInfos);
    }

    /**
     * Get information about a specific tool.
     */
    @GetMapping("/tools/{toolName}")
    public ResponseEntity<McpToolInfo> getTool(@PathVariable(name = "toolName") String toolName) {
        McpTool tool = toolsMap.get(toolName);
        if (tool == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(McpToolInfo.from(tool));
    }

    /**
     * Execute a tool with the provided parameters.
     */
    @PostMapping("/tools/{toolName}/execute")
    public ResponseEntity<McpToolResponse> executeTool(
            @PathVariable(name = "toolName") String toolName,
            @RequestBody JsonNode parameters) {

        McpTool tool = toolsMap.get(toolName);
        if (tool == null) {
            return ResponseEntity.notFound().build();
        }

        Instant startTime = Instant.now();

        try {
            JsonNode result = tool.execute(parameters);
            long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();

            McpToolResponse response = McpToolResponse.success(toolName, result, durationMs);

            return ResponseEntity.ok(response);

        } catch (McpToolException e) {
            long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();

            McpToolResponse response = McpToolResponse.error(toolName, e.getErrorCode(), e.getMessage());
            response.setDurationMs(durationMs);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();

            McpToolResponse response = McpToolResponse.builder()
                    .tool(toolName)
                    .success(false)
                    .error("Unexpected error: " + e.getMessage())
                    .errorCode(McpToolException.ERROR_OPERATION_FAILED)
                    .executedAt(startTime)
                    .durationMs(durationMs)
                    .build();

            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get categories with their tools.
     */
    @GetMapping("/categories")
    public ResponseEntity<Map<String, List<McpToolInfo>>> getCategories() {
        Map<String, List<McpToolInfo>> categories = toolsMap.values().stream()
                .map(McpToolInfo::from)
                .collect(Collectors.groupingBy(McpToolInfo::getCategory));
        return ResponseEntity.ok(categories);
    }
}
