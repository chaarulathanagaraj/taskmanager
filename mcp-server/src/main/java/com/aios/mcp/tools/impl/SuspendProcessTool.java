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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

/**
 * MCP tool to suspend a process by PID.
 */
@Component
public class SuspendProcessTool implements McpTool {

    private final ObjectMapper objectMapper;
    private final SystemInfo systemInfo;

    private static final Set<String> PROTECTED_PROCESSES = Set.of(
            "system", "smss.exe", "csrss.exe", "wininit.exe", "services.exe",
            "lsass.exe", "winlogon.exe", "svchost.exe", "dwm.exe", "explorer.exe",
            "system idle process", "registry", "memory compression");

    public SuspendProcessTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.systemInfo = new SystemInfo();
    }

    @Override
    public String getName() {
        return "suspend_process";
    }

    @Override
    public String getDescription() {
        return "Suspends a process by PID.";
    }

    @Override
    public JsonNode execute(JsonNode parameters) throws McpToolException {
        if (!parameters.has("pid")) {
            throw new McpToolException(getName(),
                    McpToolException.ERROR_INVALID_PARAMETERS,
                    "Missing required parameter: pid");
        }

        int pid = parameters.get("pid").asInt();
        boolean dryRun = parameters.has("dryRun") && parameters.get("dryRun").asBoolean(false);

        OSProcess process = systemInfo.getOperatingSystem().getProcess(pid);
        if (process == null) {
            throw new McpToolException(getName(),
                    McpToolException.ERROR_RESOURCE_NOT_FOUND,
                    "Process not found with PID: " + pid);
        }

        String processName = process.getName();
        if (PROTECTED_PROCESSES.contains(processName.toLowerCase())) {
            throw new McpToolException(getName(),
                    McpToolException.ERROR_PROTECTED_RESOURCE,
                    "Cannot suspend protected system process: " + processName);
        }

        ObjectNode result = objectMapper.createObjectNode();
        result.put("pid", pid);
        result.put("processName", processName);
        result.put("dryRun", dryRun);

        if (dryRun) {
            result.put("status", "dry_run");
            result.put("message", "Would suspend process: " + processName + " (PID: " + pid + ")");
            return result;
        }

        String script = "$ErrorActionPreference='Stop';"
                + "$targetPid=" + pid + ";"
                + "$p=Get-Process -Id $targetPid -ErrorAction Stop;"
                + "$csharp=@\""
                + "using System;"
                + "using System.Runtime.InteropServices;"
                + "public static class NativeMethods {"
                + "[DllImport(\\\"kernel32.dll\\\", SetLastError=true)]public static extern IntPtr OpenProcess(uint dwDesiredAccess,bool bInheritHandle,int dwProcessId);"
                + "[DllImport(\\\"ntdll.dll\\\")]public static extern int NtSuspendProcess(IntPtr processHandle);"
                + "[DllImport(\\\"kernel32.dll\\\", SetLastError=true)]public static extern bool CloseHandle(IntPtr hObject);"
                + "}"
                + "\"@;"
                + "Add-Type -TypeDefinition $csharp;"
                + "$PROCESS_SUSPEND_RESUME=0x0800;"
                + "$PROCESS_QUERY_LIMITED_INFORMATION=0x1000;"
                + "$access=$PROCESS_SUSPEND_RESUME -bor $PROCESS_QUERY_LIMITED_INFORMATION;"
                + "$h=[NativeMethods]::OpenProcess($access,$false,$targetPid);"
                + "if($h -eq [IntPtr]::Zero){throw ('OpenProcess failed: '+[Runtime.InteropServices.Marshal]::GetLastWin32Error())}"
                + "$status=[NativeMethods]::NtSuspendProcess($h);"
                + "[NativeMethods]::CloseHandle($h)|Out-Null;"
                + "if($status -ne 0){throw ('NtSuspendProcess failed with status '+$status)}"
                + "[PSCustomObject]@{status='suspended';pid=$targetPid;processName=$p.ProcessName}|ConvertTo-Json -Compress";

        String stdout = runPowerShell(script);
        try {
            JsonNode details = objectMapper.readTree(stdout);
            result.put("status", details.path("status").asText("suspended"));
            result.put("message", "Process suspended successfully");
            result.set("details", details);
        } catch (Exception e) {
            throw new McpToolException(getName(),
                    McpToolException.ERROR_OPERATION_FAILED,
                    "Failed to parse PowerShell output: " + e.getMessage(),
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
        pid.put("description", "Process ID to suspend");
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
        return SafetyLevel.HIGH;
    }

    private String runPowerShell(String script) throws McpToolException {
        String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-EncodedCommand", encoded);
        pb.redirectErrorStream(false);

        try {
            Process process = pb.start();
            int exit = process.waitFor();
            String stdout = new String(process.getInputStream().readAllBytes());
            String stderr = new String(process.getErrorStream().readAllBytes());

            if (exit != 0) {
                throw new McpToolException(getName(),
                        McpToolException.ERROR_OPERATION_FAILED,
                        "PowerShell failed: " + (stderr.isBlank() ? stdout : stderr));
            }

            return stdout;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new McpToolException(getName(),
                    McpToolException.ERROR_TIMEOUT,
                    "Interrupted while executing PowerShell command",
                    e);
        } catch (Exception e) {
            throw new McpToolException(getName(),
                    McpToolException.ERROR_OPERATION_FAILED,
                    "Failed to execute PowerShell command: " + e.getMessage(),
                    e);
        }
    }
}
