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
 * MCP tool to restart a process by terminating and relaunching it.
 */
@Component
public class RestartProcessTool implements McpTool {

    private final ObjectMapper objectMapper;
    private final SystemInfo systemInfo;

    private static final Set<String> PROTECTED_PROCESSES = Set.of(
            "system", "smss.exe", "csrss.exe", "wininit.exe", "services.exe",
            "lsass.exe", "winlogon.exe", "svchost.exe", "dwm.exe", "explorer.exe",
            "system idle process", "registry", "memory compression");

    public RestartProcessTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.systemInfo = new SystemInfo();
    }

    @Override
    public String getName() {
        return "restart_process";
    }

    @Override
    public String getDescription() {
        return "Restarts a process by PID using executable path and existing command line where possible.";
    }

    @Override
    public JsonNode execute(JsonNode parameters) throws McpToolException {
        if (!parameters.has("pid")) {
            throw new McpToolException(getName(),
                    McpToolException.ERROR_INVALID_PARAMETERS,
                    "Missing required parameter: pid");
        }

        int pid = parameters.get("pid").asInt();
        boolean force = parameters.has("force") && parameters.get("force").asBoolean(false);
        boolean dryRun = parameters.has("dryRun") && parameters.get("dryRun").asBoolean(false);
        int waitMs = parameters.has("waitMs") ? parameters.get("waitMs").asInt(800) : 800;

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
                    "Cannot restart protected system process: " + processName);
        }

        ObjectNode result = objectMapper.createObjectNode();
        result.put("pid", pid);
        result.put("processName", processName);
        result.put("dryRun", dryRun);
        result.put("force", force);
        result.put("waitMs", waitMs);

        if (dryRun) {
            result.put("status", "dry_run");
            result.put("message", "Would restart process: " + processName + " (PID: " + pid + ")");
            return result;
        }

        String script = "$ErrorActionPreference='Stop';"
                + "$targetPid=" + pid + ";"
                + "$force=$" + (force ? "true" : "false") + ";"
                + "$waitMs=" + waitMs + ";"
                + "$p=Get-CimInstance Win32_Process -Filter \"ProcessId = " + pid + "\";"
                + "if (-not $p) { throw 'Process not found'; }"
                + "$exe=$p.ExecutablePath;"
                + "$cmd=$p.CommandLine;"
                + "if ([string]::IsNullOrWhiteSpace($exe)) { throw 'Executable path unavailable for restart'; }"
                + "$name=$p.Name;"
                + "$args=$null;"
                + "if (-not [string]::IsNullOrWhiteSpace($cmd)) {"
                + "  if ($cmd.StartsWith('\\\"')) {"
                + "    $end=$cmd.IndexOf('\\\"',1);"
                + "    if ($end -gt 0 -and $cmd.Length -gt ($end+1)) { $args=$cmd.Substring($end+1).Trim(); }"
                + "  } else {"
                + "    $space=$cmd.IndexOf(' ');"
                + "    if ($space -gt 0 -and $cmd.Length -gt ($space+1)) { $args=$cmd.Substring($space+1).Trim(); }"
                + "  }"
                + "}"
                + "Stop-Process -Id $targetPid -Force:$force;"
                + "Start-Sleep -Milliseconds $waitMs;"
                + "if ([string]::IsNullOrWhiteSpace($args)) {"
                + "  $newProc=Start-Process -FilePath $exe -PassThru;"
                + "} else {"
                + "  $newProc=Start-Process -FilePath $exe -ArgumentList $args -PassThru;"
                + "}"
                + "[PSCustomObject]@{status='restarted';oldPid=$targetPid;newPid=$newProc.Id;processName=$name;executablePath=$exe;arguments=$args}|ConvertTo-Json -Compress";

        String stdout = runPowerShell(script);
        try {
            JsonNode details = objectMapper.readTree(stdout);
            result.put("status", details.path("status").asText("restarted"));
            result.put("message", "Process restarted successfully");
            result.put("newPid", details.path("newPid").asInt());
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
        pid.put("description", "Process ID to restart");
        properties.set("pid", pid);

        ObjectNode force = objectMapper.createObjectNode();
        force.put("type", "boolean");
        force.put("description", "Force terminate before restart");
        force.put("default", false);
        properties.set("force", force);

        ObjectNode waitMsNode = objectMapper.createObjectNode();
        waitMsNode.put("type", "integer");
        waitMsNode.put("description", "Wait time between stop and start in milliseconds");
        waitMsNode.put("default", 800);
        waitMsNode.put("minimum", 0);
        waitMsNode.put("maximum", 10000);
        properties.set("waitMs", waitMsNode);

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
