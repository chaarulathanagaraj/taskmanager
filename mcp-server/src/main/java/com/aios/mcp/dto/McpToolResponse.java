package com.aios.mcp.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for MCP tool execution.
 * Contains the result of a tool invocation along with metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpToolResponse {

    /**
     * Name of the tool that was executed
     */
    private String tool;

    /**
     * Result of the tool execution (JSON structure)
     */
    private JsonNode result;

    /**
     * Whether the tool execution was successful
     */
    private boolean success;

    /**
     * Error message if the execution failed
     */
    private String error;

    /**
     * Error code for programmatic error handling
     */
    private String errorCode;

    /**
     * Timestamp when the tool was executed
     */
    private Instant executedAt;

    /**
     * Execution duration in milliseconds
     */
    private Long durationMs;

    /**
     * Create a successful response
     */
    public static McpToolResponse success(String tool, JsonNode result, long durationMs) {
        return McpToolResponse.builder()
                .tool(tool)
                .result(result)
                .success(true)
                .executedAt(Instant.now())
                .durationMs(durationMs)
                .build();
    }

    /**
     * Create an error response
     */
    public static McpToolResponse error(String tool, String errorCode, String errorMessage) {
        return McpToolResponse.builder()
                .tool(tool)
                .success(false)
                .error(errorMessage)
                .errorCode(errorCode)
                .executedAt(Instant.now())
                .build();
    }

    /**
     * Create an error response from exception
     */
    public static McpToolResponse error(String tool, Exception e) {
        return McpToolResponse.builder()
                .tool(tool)
                .success(false)
                .error(e.getMessage())
                .errorCode("EXCEPTION")
                .executedAt(Instant.now())
                .build();
    }
}
