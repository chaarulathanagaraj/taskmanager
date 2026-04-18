package com.aios.mcp.tools;

/**
 * Exception thrown when an MCP tool execution fails.
 */
public class McpToolException extends Exception {

    private final String toolName;
    private final String errorCode;

    public McpToolException(String message) {
        super(message);
        this.toolName = null;
        this.errorCode = "UNKNOWN_ERROR";
    }

    public McpToolException(String toolName, String message) {
        super(message);
        this.toolName = toolName;
        this.errorCode = "TOOL_ERROR";
    }

    public McpToolException(String toolName, String message, Throwable cause) {
        super(message, cause);
        this.toolName = toolName;
        this.errorCode = "TOOL_ERROR";
    }

    public McpToolException(String toolName, String errorCode, String message) {
        super(message);
        this.toolName = toolName;
        this.errorCode = errorCode;
    }

    public McpToolException(String toolName, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.toolName = toolName;
        this.errorCode = errorCode;
    }

    public String getToolName() {
        return toolName;
    }

    public String getErrorCode() {
        return errorCode;
    }

    // Common error codes
    public static final String INVALID_PARAMETERS = "INVALID_PARAMETERS";
    public static final String PERMISSION_DENIED = "PERMISSION_DENIED";
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String OPERATION_FAILED = "OPERATION_FAILED";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String PROTECTED_RESOURCE = "PROTECTED_RESOURCE";

    // Aliases with ERROR_ prefix for backward compatibility
    public static final String ERROR_INVALID_PARAMETERS = INVALID_PARAMETERS;
    public static final String ERROR_PERMISSION_DENIED = PERMISSION_DENIED;
    public static final String ERROR_RESOURCE_NOT_FOUND = RESOURCE_NOT_FOUND;
    public static final String ERROR_OPERATION_FAILED = OPERATION_FAILED;
    public static final String ERROR_TIMEOUT = TIMEOUT;
    public static final String ERROR_PROTECTED_RESOURCE = PROTECTED_RESOURCE;
}
