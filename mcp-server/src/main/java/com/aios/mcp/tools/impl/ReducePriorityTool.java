package com.aios.mcp.tools.impl;

import com.aios.mcp.tools.McpTool;
import com.aios.mcp.tools.McpToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;

/**
 * MCP tool to reduce process priority on Windows.
 */
@Component
public class ReducePriorityTool implements McpTool {

    private final ObjectMapper objectMapper;
    private final SystemInfo systemInfo;

    public ReducePriorityTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.systemInfo = new SystemInfo();
    }

    @Override
    public String getName() {
        return "reduce_priority";
    }

    @Override
    public String getDescription() {
        return "Reduces a process priority to BelowNormal or Idle. Supports dry-run mode.";
    }

    @Override
    public JsonNode execute(JsonNode parameters) throws McpToolException {
        if (!parameters.has("pid")) {
            throw new McpToolException(getName(),
                    "Missing required parameter: pid",
                    McpToolException.ERROR_INVALID_PARAMETERS);
        }

        int pid = parameters.get("pid").asInt();
        boolean dryRun = parameters.has("dryRun") && parameters.get("dryRun").asBoolean(false);
        String target = parameters.has("targetPriority")
                ? parameters.get("targetPriority").asText("BelowNormal")
                : "BelowNormal";

        String normalizedTarget = normalizePriority(target);
        if (normalizedTarget == null) {
            throw new McpToolException(getName(),
                    "targetPriority must be BELOW_NORMAL or IDLE",
                    McpToolException.ERROR_INVALID_PARAMETERS);
        }

        OSProcess process = systemInfo.getOperatingSystem().getProcess(pid);
        if (process == null) {
            throw new McpToolException(getName(),
                    "Process not found with PID: " + pid,
                    McpToolException.ERROR_RESOURCE_NOT_FOUND);
        }

        ObjectNode result = objectMapper.createObjectNode();
        result.put("pid", pid);
        result.put("processName", process.getName());
        result.put("dryRun", dryRun);
        result.put("targetPriority", normalizedTarget);

        if (dryRun) {
            result.put("status", "dry_run");
            result.put("message", "Would reduce priority for PID " + pid + " to " + normalizedTarget);
            return result;
        }

        String script = "$ErrorActionPreference='Stop';"
                + "$p=Get-Process -Id " + pid + ";"
                + "$old=$p.PriorityClass.ToString();"
                + "$p.PriorityClass='" + normalizedTarget + "';"
                + "$p.Refresh();"
                + "$new=$p.PriorityClass.ToString();"
                + "[PSCustomObject]@{oldPriority=$old;newPriority=$new}|ConvertTo-Json -Compress";

        String stdout = runPowerShell(script);
        try {
            JsonNode details = objectMapper.readTree(stdout);
            result.put("status", "updated");
            result.put("message", "Priority updated successfully");
            result.set("details", details);
        } catch (Exception e) {
            throw new McpToolException(getName(),
                    "Failed to parse PowerShell output: " + e.getMessage(),
                    McpToolException.ERROR_OPERATION_FAILED,
                    e);
        }

        return result;
    }

    @Override
    public JsonNode getSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode pid = objectMapper.createObjectNode();
        pid.put("type", "integer");
        pid.put("description", "Process ID to reprioritize");
        properties.set("pid", pid);

        ObjectNode targetPriority = objectMapper.createObjectNode();
        targetPriority.put("type", "string");
        targetPriority.put("description", "Target priority (BELOW_NORMAL or IDLE)");
        targetPriority.put("default", "BELOW_NORMAL");
        ArrayNode enumValues = objectMapper.createArrayNode();
        enumValues.add("BELOW_NORMAL");
        enumValues.add("IDLE");
        targetPriority.set("enum", enumValues);
        properties.set("targetPriority", targetPriority);

        ObjectNode dryRun = objectMapper.createObjectNode();
        dryRun.put("type", "boolean");
        dryRun.put("description", "If true, simulate without applying");
        dryRun.put("default", false);
        properties.set("dryRun", dryRun);

        schema.set("properties", properties);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("pid");
        schema.set("required", required);

        return schema;
    }

    @Override
    public String getCategory() {
        return "process";
    }

    @Override
    public SafetyLevel getSafetyLevel() {
        return SafetyLevel.MEDIUM;
    }

    private String normalizePriority(String priority) {
        if (priority == null) {
            return "BelowNormal";
        }

        return switch (priority.trim().toUpperCase()) {
            case "BELOW_NORMAL", "BELOWNORMAL" -> "BelowNormal";
            case "IDLE" -> "Idle";
            default -> null;
        };
    }

    private String runPowerShell(String script) throws McpToolException {
        ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command", script);
        pb.redirectErrorStream(false);

        try {
            Process process = pb.start();
            int exit = process.waitFor();
            String stdout = new String(process.getInputStream().readAllBytes());
            String stderr = new String(process.getErrorStream().readAllBytes());

            if (exit != 0) {
                throw new McpToolException(getName(),
                        "PowerShell failed: " + (stderr.isBlank() ? stdout : stderr),
                        McpToolException.ERROR_OPERATION_FAILED);
            }

            return stdout;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new McpToolException(getName(),
                    "Interrupted while executing PowerShell command",
                    McpToolException.ERROR_TIMEOUT,
                    e);
        } catch (Exception e) {
            throw new McpToolException(getName(),
                    "Failed to execute PowerShell command: " + e.getMessage(),
                    McpToolException.ERROR_OPERATION_FAILED,
                    e);
        }
    }
}