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
import oshi.software.os.OSThread;
import oshi.software.os.OperatingSystem;

import java.util.List;

/**
 * MCP Tool for retrieving thread information for a specific process.
 * Provides detailed information about each thread including state and CPU
 * usage.
 */
@Component
public class GetProcessThreadsTool implements McpTool {

    private final ObjectMapper objectMapper;
    private final SystemInfo systemInfo;

    public GetProcessThreadsTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.systemInfo = new SystemInfo();
    }

    @Override
    public String getName() {
        return "get_process_threads";
    }

    @Override
    public String getDescription() {
        return "Retrieves the list of threads for a specific process, " +
                "including thread state, CPU time, and priority information.";
    }

    @Override
    public JsonNode execute(JsonNode parameters) throws McpToolException {
        if (!parameters.has("pid")) {
            throw new McpToolException(getName(),
                    "Missing required parameter: pid",
                    McpToolException.ERROR_INVALID_PARAMETERS);
        }

        int pid = parameters.get("pid").asInt();

        try {
            OperatingSystem os = systemInfo.getOperatingSystem();
            OSProcess process = os.getProcess(pid);

            if (process == null) {
                throw new McpToolException(getName(),
                        "Process not found with PID: " + pid,
                        McpToolException.ERROR_RESOURCE_NOT_FOUND);
            }

            List<OSThread> threads = process.getThreadDetails();

            ArrayNode threadArray = objectMapper.createArrayNode();
            for (OSThread thread : threads) {
                ObjectNode threadNode = objectMapper.createObjectNode();
                threadNode.put("threadId", thread.getThreadId());
                threadNode.put("name", thread.getName());
                threadNode.put("state", thread.getState().name());
                threadNode.put("priority", thread.getPriority());
                threadNode.put("kernelTime", thread.getKernelTime());
                threadNode.put("userTime", thread.getUserTime());
                threadNode.put("startTime", thread.getStartTime());
                threadNode.put("upTime", thread.getUpTime());
                threadNode.put("startAddress", thread.getStartMemoryAddress());
                threadNode.put("contextSwitches", thread.getContextSwitches());
                threadArray.add(threadNode);
            }

            ObjectNode result = objectMapper.createObjectNode();
            result.put("pid", pid);
            result.put("processName", process.getName());
            result.put("threadCount", threads.size());
            result.set("threads", threadArray);

            // Add process summary
            ObjectNode processSummary = objectMapper.createObjectNode();
            processSummary.put("totalThreads", process.getThreadCount());
            processSummary.put("cpuPercent", Math.round(process.getProcessCpuLoadCumulative() * 10000.0) / 100.0);
            processSummary.put("memoryMB", process.getResidentSetSize() / (1024 * 1024));
            processSummary.put("state", process.getState().name());
            result.set("processSummary", processSummary);

            return result;

        } catch (McpToolException e) {
            throw e;
        } catch (Exception e) {
            throw new McpToolException(getName(),
                    "Failed to get thread information: " + e.getMessage(), e);
        }
    }

    @Override
    public JsonNode getSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode pid = objectMapper.createObjectNode();
        pid.put("type", "integer");
        pid.put("description", "The process ID to get threads for");
        pid.put("minimum", 0);
        properties.set("pid", pid);

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
        return SafetyLevel.LOW;
    }
}
