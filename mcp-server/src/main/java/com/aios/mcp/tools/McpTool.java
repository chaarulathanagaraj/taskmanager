package com.aios.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Interface defining an MCP (Model Context Protocol) tool.
 * Each tool provides a standardized way for AI agents to interact with system
 * resources.
 */
public interface McpTool {

    /**
     * Get the unique name of this tool.
     * Used to identify the tool in API calls.
     *
     * @return Tool name (e.g., "get_process_list", "kill_process")
     */
    String getName();

    /**
     * Get a human-readable description of what the tool does.
     * Used by AI agents to understand tool capabilities.
     *
     * @return Tool description
     */
    String getDescription();

    /**
     * Execute the tool with the given parameters.
     *
     * @param parameters JSON parameters for the tool execution
     * @return JSON result of the tool execution
     * @throws McpToolException if tool execution fails
     */
    JsonNode execute(JsonNode parameters) throws McpToolException;

    /**
     * Get the JSON schema defining the parameters this tool accepts.
     * Used for validation and AI agent tool discovery.
     *
     * @return JSON schema for tool parameters
     */
    JsonNode getSchema();

    /**
     * Get the category of this tool.
     * Used for grouping tools in the UI and documentation.
     *
     * @return Tool category (default: "general")
     */
    default String getCategory() {
        return "general";
    }

    /**
     * Check if this tool requires elevated permissions.
     *
     * @return true if elevated permissions required
     */
    default boolean requiresElevation() {
        return false;
    }

    /**
     * Get the safety level of this tool.
     * Higher levels require more confirmation.
     *
     * @return Safety level (LOW, MEDIUM, HIGH, CRITICAL)
     */
    default SafetyLevel getSafetyLevel() {
        return SafetyLevel.LOW;
    }

    /**
     * Safety levels for MCP tools
     */
    enum SafetyLevel {
        LOW, // Read-only operations
        MEDIUM, // Modifications with rollback capability
        HIGH, // Modifications that may affect system stability
        CRITICAL // Operations that could cause system failure
    }
}
