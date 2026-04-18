package com.aios.ai.tools;

import com.aios.ai.service.McpToolService;
import com.aios.shared.dto.ProcessInfo;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * LangChain4j tool wrappers for MCP tools.
 * Enables AI agents to call system diagnostic tools directly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class McpToolProvider {

    private final McpToolService mcpToolService;

    @Tool("Get detailed information about a specific process by PID including CPU, memory, and thread count")
    public String getProcessInfo(
            @P("Process ID to look up") int pid) {
        log.debug("AI agent requesting process info for PID: {}", pid);
        ProcessInfo info = mcpToolService.getProcessInfo(pid);
        if (info == null) {
            return "Process not found with PID: " + pid;
        }
        return String.format(
                "Process: %s (PID: %d)\n" +
                        "CPU: %.2f%%\n" +
                        "Memory: %d MB\n" +
                        "Threads: %d",
                info.getName(), info.getPid(),
                info.getCpuPercent(),
                info.getMemoryBytes() / (1024 * 1024),
                info.getThreadCount());
    }

    @Tool("Get thread information for a process including thread states and CPU usage per thread")
    public String getProcessThreads(
            @P("Process ID to get threads for") int pid) {
        log.debug("AI agent requesting threads for PID: {}", pid);
        JsonNode threads = mcpToolService.getThreads(pid);
        if (threads == null) {
            return "Unable to retrieve thread information for PID: " + pid;
        }
        return formatJsonForAI(threads, "threads");
    }

    @Tool("Get system I/O statistics including disk read/write rates and partition information")
    public String getIOStats() {
        log.debug("AI agent requesting I/O statistics");
        JsonNode stats = mcpToolService.getIOStats();
        if (stats == null) {
            return "Unable to retrieve I/O statistics";
        }
        return formatJsonForAI(stats, "io_stats");
    }

    @Tool("Get Windows performance counter data for a specific category like Memory, Processor, or PhysicalDisk")
    public String getPerformanceCounters(
            @P("Performance counter category (e.g., Memory, Processor, PhysicalDisk, LogicalDisk)") String category,
            @P("Whether to include detailed counter values") boolean detailed) {
        log.debug("AI agent requesting performance counters: category={}, detailed={}", category, detailed);
        JsonNode counters = mcpToolService.getPerformanceCounters(category, detailed);
        if (counters == null) {
            return "Unable to retrieve performance counters for category: " + category;
        }
        return formatJsonForAI(counters, "counters");
    }

    @Tool("Read Windows Event Log entries for system diagnostics")
    public String readEventLog(
            @P("Log name: Application, System, or Security") String logName,
            @P("Maximum number of entries to retrieve (1-100)") int limit,
            @P("Event type filter: Error, Warning, Information, or null for all") String eventType) {
        log.debug("AI agent reading event log: log={}, limit={}, type={}", logName, limit, eventType);

        // Validate and constrain parameters
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        JsonNode events = mcpToolService.readEventLog(logName, safeLimit, eventType);
        if (events == null) {
            return "Unable to read event log: " + logName;
        }
        return formatJsonForAI(events, "events");
    }

    @Tool("Get list of top processes sorted by resource usage (CPU or memory)")
    public String getTopProcesses(
            @P("Number of processes to return (1-50)") int limit,
            @P("Sort by: cpu or memory") String sortBy) {
        log.debug("AI agent requesting top processes: limit={}, sortBy={}", limit, sortBy);

        int safeLimit = Math.min(Math.max(limit, 1), 50);
        String safeSortBy = "memory".equalsIgnoreCase(sortBy) ? "memory" : "cpu";

        List<ProcessInfo> processes = mcpToolService.getTopProcesses(safeLimit, safeSortBy);
        if (processes.isEmpty()) {
            return "No processes found";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Top %d processes by %s:\n\n", processes.size(), safeSortBy));

        for (int i = 0; i < processes.size(); i++) {
            ProcessInfo p = processes.get(i);
            sb.append(String.format("%d. %s (PID: %d) - CPU: %.1f%%, Memory: %d MB, Threads: %d\n",
                    i + 1, p.getName(), p.getPid(),
                    p.getCpuPercent(),
                    p.getMemoryBytes() / (1024 * 1024),
                    p.getThreadCount()));
        }

        return sb.toString();
    }

    @Tool("Get memory-specific performance metrics including available memory, page faults, and pool sizes")
    public String getMemoryMetrics() {
        log.debug("AI agent requesting memory metrics");
        JsonNode counters = mcpToolService.getPerformanceCounters("Memory", true);
        if (counters == null) {
            return "Unable to retrieve memory metrics";
        }
        return formatJsonForAI(counters, "memory_metrics");
    }

    @Tool("Get disk-specific performance metrics including queue length, read/write rates, and disk time")
    public String getDiskMetrics() {
        log.debug("AI agent requesting disk metrics");
        JsonNode counters = mcpToolService.getPerformanceCounters("PhysicalDisk", true);
        if (counters == null) {
            return "Unable to retrieve disk metrics";
        }
        return formatJsonForAI(counters, "disk_metrics");
    }

    @Tool("Get processor-specific performance metrics including CPU time, interrupts, and queue length")
    public String getProcessorMetrics() {
        log.debug("AI agent requesting processor metrics");
        JsonNode counters = mcpToolService.getPerformanceCounters("Processor", true);
        if (counters == null) {
            return "Unable to retrieve processor metrics";
        }
        return formatJsonForAI(counters, "processor_metrics");
    }

    @Tool("Get recent system errors from the Windows Event Log for troubleshooting")
    public String getRecentSystemErrors(
            @P("Number of recent errors to retrieve (1-50)") int limit) {
        log.debug("AI agent requesting recent system errors");
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        JsonNode events = mcpToolService.readEventLog("System", safeLimit, "Error");
        if (events == null) {
            return "Unable to retrieve system errors";
        }
        return formatJsonForAI(events, "system_errors");
    }

    @Tool("Get recent application errors from the Windows Event Log")
    public String getRecentApplicationErrors(
            @P("Number of recent errors to retrieve (1-50)") int limit) {
        log.debug("AI agent requesting recent application errors");
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        JsonNode events = mcpToolService.readEventLog("Application", safeLimit, "Error");
        if (events == null) {
            return "Unable to retrieve application errors";
        }
        return formatJsonForAI(events, "application_errors");
    }

    /**
     * Format JSON response for AI consumption.
     * Converts JSON to a readable string format.
     */
    private String formatJsonForAI(JsonNode json, String label) {
        try {
            if (json.isArray()) {
                StringBuilder sb = new StringBuilder();
                sb.append(label).append(" (").append(json.size()).append(" items):\n\n");

                int count = 0;
                for (JsonNode item : json) {
                    if (count >= 20) {
                        sb.append("\n... and ").append(json.size() - 20).append(" more items");
                        break;
                    }
                    sb.append(formatNodeCompact(item)).append("\n");
                    count++;
                }
                return sb.toString();
            } else {
                return label + ":\n" + json.toPrettyString();
            }
        } catch (Exception e) {
            return label + ": " + json.toString();
        }
    }

    /**
     * Format a JSON node in a compact, readable format.
     */
    private String formatNodeCompact(JsonNode node) {
        if (node.isObject()) {
            StringBuilder sb = new StringBuilder();
            node.fields().forEachRemaining(field -> {
                if (sb.length() > 0)
                    sb.append(", ");
                sb.append(field.getKey()).append("=").append(formatValue(field.getValue()));
            });
            return "{ " + sb + " }";
        }
        return node.toString();
    }

    private String formatValue(JsonNode value) {
        if (value.isTextual()) {
            String text = value.asText();
            return text.length() > 50 ? text.substring(0, 47) + "..." : text;
        }
        return value.toString();
    }
}
