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

import java.util.Comparator;
import java.util.List;

/**
 * MCP Tool for retrieving the list of running processes.
 * Provides detailed information about each process including CPU and memory
 * usage.
 */
@Component
public class GetProcessListTool implements McpTool {

    private final ObjectMapper objectMapper;
    private final SystemInfo systemInfo;

    public GetProcessListTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.systemInfo = new SystemInfo();
    }

    @Override
    public String getName() {
        return "get_process_list";
    }

    @Override
    public String getDescription() {
        return "Retrieves a list of running processes with their resource usage. " +
                "Can be filtered by name and sorted by CPU or memory usage.";
    }

    @Override
    public JsonNode execute(JsonNode parameters) throws McpToolException {
        try {
            // Parse parameters
            int limit = parameters.has("limit") ? parameters.get("limit").asInt(50) : 50;
            String sortBy = parameters.has("sortBy") ? parameters.get("sortBy").asText("cpu") : "cpu";
            String nameFilter = parameters.has("nameFilter") ? parameters.get("nameFilter").asText(null) : null;
            Integer pidFilter = parameters.has("pid") ? parameters.get("pid").asInt() : null;

            OperatingSystem os = systemInfo.getOperatingSystem();
            List<OSProcess> processes;
            
            if (pidFilter != null) {
                OSProcess p = os.getProcess(pidFilter);
                processes = p != null ? List.of(p) : List.of();
            } else {
                processes = os.getProcesses();
            }

            // Filter by name if specified
            if (nameFilter != null && !nameFilter.isEmpty()) {
                String filter = nameFilter.toLowerCase();
                processes = processes.stream()
                        .filter(p -> p.getName().toLowerCase().contains(filter))
                        .toList();
            }

            // Sort processes
            Comparator<OSProcess> comparator = switch (sortBy.toLowerCase()) {
                case "memory", "mem" -> Comparator.comparingLong(OSProcess::getResidentSetSize).reversed();
                case "pid" -> Comparator.comparingInt(OSProcess::getProcessID);
                case "name" -> Comparator.comparing(OSProcess::getName, String.CASE_INSENSITIVE_ORDER);
                default -> Comparator.<OSProcess>comparingDouble(p -> p.getProcessCpuLoadCumulative() * 100).reversed();
            };

            processes = processes.stream()
                    .sorted(comparator)
                    .limit(limit)
                    .toList();

            // Build result
            ArrayNode processArray = objectMapper.createArrayNode();
            for (OSProcess process : processes) {
                ObjectNode processNode = objectMapper.createObjectNode();
                processNode.put("pid", process.getProcessID());
                processNode.put("name", process.getName());
                processNode.put("path", process.getPath());
                processNode.put("state", process.getState().name());
                processNode.put("cpuPercent", Math.round(process.getProcessCpuLoadCumulative() * 10000.0) / 100.0);
                processNode.put("memoryBytes", process.getResidentSetSize());
                processNode.put("memoryMB", process.getResidentSetSize() / (1024 * 1024));
                processNode.put("virtualMemoryBytes", process.getVirtualSize());
                processNode.put("threadCount", process.getThreadCount());
                processNode.put("handleCount", process.getOpenFiles());
                processNode.put("user", process.getUser());
                processNode.put("startTime", process.getStartTime());
                processNode.put("upTimeMs", process.getUpTime());
                processNode.put("priority", process.getPriority());
                processNode.put("commandLine", process.getCommandLine());
                processArray.add(processNode);
            }

            ObjectNode result = objectMapper.createObjectNode();
            result.put("count", processArray.size());
            result.put("totalProcesses", os.getProcessCount());
            result.set("processes", processArray);

            return result;

        } catch (Exception e) {
            throw new McpToolException(getName(), "Failed to get process list: " + e.getMessage(), e);
        }
    }

    @Override
    public JsonNode getSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode limit = objectMapper.createObjectNode();
        limit.put("type", "integer");
        limit.put("description", "Maximum number of processes to return (default: 50)");
        limit.put("default", 50);
        limit.put("minimum", 1);
        limit.put("maximum", 1000);
        properties.set("limit", limit);

        ObjectNode sortBy = objectMapper.createObjectNode();
        sortBy.put("type", "string");
        sortBy.put("description", "Sort processes by: cpu, memory, pid, or name");
        sortBy.put("default", "cpu");
        ArrayNode enumValues = objectMapper.createArrayNode();
        enumValues.add("cpu");
        enumValues.add("memory");
        enumValues.add("pid");
        enumValues.add("name");
        sortBy.set("enum", enumValues);
        properties.set("sortBy", sortBy);

        ObjectNode nameFilter = objectMapper.createObjectNode();
        nameFilter.put("type", "string");
        nameFilter.put("description", "Filter processes by name (case-insensitive, partial match)");
        properties.set("nameFilter", nameFilter);

        ObjectNode pidFilter = objectMapper.createObjectNode();
        pidFilter.put("type", "integer");
        pidFilter.put("description", "Filter by specific process ID");
        properties.set("pid", pidFilter);

        schema.set("properties", properties);
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
