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
 * MCP tool to trim a process working set.
 */
@Component
public class TrimWorkingSetTool implements McpTool {

    private final ObjectMapper objectMapper;
    private final SystemInfo systemInfo;

    public TrimWorkingSetTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.systemInfo = new SystemInfo();
    }

    @Override
    public String getName() {
        return "trim_working_set";
    }

    @Override
    public String getDescription() {
        return "Trims memory working set for a process using Windows EmptyWorkingSet API.";
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
        result.put("memoryBytesBefore", process.getResidentSetSize());

        if (dryRun) {
            result.put("status", "dry_run");
            result.put("message", "Would trim working set for PID " + pid);
            return result;
        }

        String script = "$ErrorActionPreference='Stop';"
                + "Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices;"
                + "public static class NativeMethods {"
                + "[DllImport(\"psapi.dll\", SetLastError=true)] public static extern bool EmptyWorkingSet(IntPtr hProcess);"
                + "}';"
                + "$p=Get-Process -Id " + pid + ";"
                + "$before=$p.WorkingSet64;"
                + "$ok=[NativeMethods]::EmptyWorkingSet($p.Handle);"
                + "$p.Refresh();"
                + "$after=$p.WorkingSet64;"
                + "[PSCustomObject]@{success=$ok;memoryBytesBefore=$before;memoryBytesAfter=$after;memoryBytesFreed=($before-$after)}|ConvertTo-Json -Compress";

        String stdout = runPowerShell(script);
        try {
            JsonNode details = objectMapper.readTree(stdout);
            if (!details.path("success").asBoolean(false)) {
                throw new McpToolException(getName(),
                        "EmptyWorkingSet returned false",
                        McpToolException.ERROR_OPERATION_FAILED);
            }

            result.put("status", "trimmed");
            result.put("message", "Working set trimmed successfully");
            result.set("details", details);
            result.put("memoryBytesAfter", details.path("memoryBytesAfter").asLong());
            result.put("memoryBytesFreed", details.path("memoryBytesFreed").asLong());
        } catch (McpToolException e) {
            throw e;
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
        pid.put("description", "Process ID to trim");
        properties.set("pid", pid);

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
    public boolean requiresElevation() {
        return true;
    }

    @Override
    public SafetyLevel getSafetyLevel() {
        return SafetyLevel.MEDIUM;
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