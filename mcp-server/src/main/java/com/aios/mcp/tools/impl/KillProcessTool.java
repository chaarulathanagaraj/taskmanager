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
import oshi.software.os.OperatingSystem;

import java.util.Set;

/**
 * MCP Tool for terminating a process.
 * Supports graceful termination and force kill options.
 * Includes safety checks for protected system processes.
 */
@Component
public class KillProcessTool implements McpTool {

    private final ObjectMapper objectMapper;
    private final SystemInfo systemInfo;

    // Protected system processes that should not be killed
    private static final Set<String> PROTECTED_PROCESSES = Set.of(
            "system", "smss.exe", "csrss.exe", "wininit.exe", "services.exe",
            "lsass.exe", "winlogon.exe", "svchost.exe", "dwm.exe", "explorer.exe",
            "system idle process", "registry", "memory compression");

    public KillProcessTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.systemInfo = new SystemInfo();
    }

    @Override
    public String getName() {
        return "kill_process";
    }

    @Override
    public String getDescription() {
        return "Terminates a running process by PID. Supports graceful termination " +
                "and force kill options. Protected system processes cannot be terminated.";
    }

    @Override
    public JsonNode execute(JsonNode parameters) throws McpToolException {
        if (!parameters.has("pid")) {
            throw new McpToolException(getName(),
                    "Missing required parameter: pid",
                    McpToolException.ERROR_INVALID_PARAMETERS);
        }

        int pid = parameters.get("pid").asInt();
        boolean force = parameters.has("force") && parameters.get("force").asBoolean(false);
        boolean dryRun = parameters.has("dryRun") && parameters.get("dryRun").asBoolean(false);

        try {
            OperatingSystem os = systemInfo.getOperatingSystem();
            OSProcess process = os.getProcess(pid);

            if (process == null) {
                throw new McpToolException(getName(),
                        "Process not found with PID: " + pid,
                        McpToolException.ERROR_RESOURCE_NOT_FOUND);
            }

            String processName = process.getName().toLowerCase();

            // Check if protected
            if (PROTECTED_PROCESSES.contains(processName)) {
                throw new McpToolException(getName(),
                        "Cannot kill protected system process: " + process.getName(),
                        McpToolException.ERROR_PROTECTED_RESOURCE);
            }

            // Don't kill self
            long selfPid = ProcessHandle.current().pid();
            if (pid == selfPid) {
                throw new McpToolException(getName(),
                        "Cannot kill the current process",
                        McpToolException.ERROR_PROTECTED_RESOURCE);
            }

            ObjectNode result = objectMapper.createObjectNode();
            result.put("pid", pid);
            result.put("processName", process.getName());
            result.put("forceKill", force);
            result.put("dryRun", dryRun);

            if (dryRun) {
                result.put("status", "dry_run");
                result.put("message", "Would terminate process: " + process.getName() + " (PID: " + pid + ")");
                return result;
            }

            // Attempt to terminate
            ProcessHandle processHandle = ProcessHandle.of(pid).orElse(null);
            if (processHandle == null) {
                throw new McpToolException(getName(),
                        "Process handle not found for PID: " + pid,
                        McpToolException.ERROR_RESOURCE_NOT_FOUND);
            }

            boolean terminated;
            if (force) {
                terminated = processHandle.destroyForcibly();
            } else {
                terminated = processHandle.destroy();
            }

            if (terminated) {
                result.put("status", "terminated");
                result.put("message", "Successfully terminated process: " + process.getName());
            } else {
                result.put("status", "pending");
                result.put("message", "Termination signal sent, process may still be running");
            }

            return result;

        } catch (McpToolException e) {
            throw e;
        } catch (Exception e) {
            throw new McpToolException(getName(),
                    "Failed to kill process: " + e.getMessage(), e);
        }
    }

    @Override
    public JsonNode getSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode pid = objectMapper.createObjectNode();
        pid.put("type", "integer");
        pid.put("description", "The process ID to terminate");
        pid.put("minimum", 0);
        properties.set("pid", pid);

        ObjectNode force = objectMapper.createObjectNode();
        force.put("type", "boolean");
        force.put("description", "Force kill the process (default: false for graceful termination)");
        force.put("default", false);
        properties.set("force", force);

        ObjectNode dryRun = objectMapper.createObjectNode();
        dryRun.put("type", "boolean");
        dryRun.put("description", "If true, only simulate the action without actually killing the process");
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
    public boolean requiresElevation() {
        return true;
    }

    @Override
    public SafetyLevel getSafetyLevel() {
        return SafetyLevel.HIGH;
    }
}
