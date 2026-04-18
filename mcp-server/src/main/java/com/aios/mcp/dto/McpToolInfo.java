package com.aios.mcp.dto;

import com.aios.mcp.tools.McpTool;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO containing information about an available MCP tool.
 * Used for tool discovery by AI agents.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpToolInfo {

    /**
     * Unique identifier for the tool
     */
    private String name;

    /**
     * Human-readable description of the tool
     */
    private String description;

    /**
     * JSON schema defining the tool's parameters
     */
    private JsonNode schema;

    /**
     * Category for grouping related tools
     */
    private String category;

    /**
     * Safety level of the tool
     */
    private McpTool.SafetyLevel safetyLevel;

    /**
     * Whether the tool requires elevated permissions
     */
    private boolean requiresElevation;

    /**
     * Create McpToolInfo from an McpTool instance
     */
    public static McpToolInfo from(McpTool tool) {
        return McpToolInfo.builder()
                .name(tool.getName())
                .description(tool.getDescription())
                .schema(tool.getSchema())
                .category(tool.getCategory())
                .safetyLevel(tool.getSafetyLevel())
                .requiresElevation(tool.requiresElevation())
                .build();
    }
}
