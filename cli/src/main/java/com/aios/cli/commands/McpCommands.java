package com.aios.cli.commands;

import com.aios.ai.service.McpToolService;
import com.aios.shared.dto.ProcessInfo;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.List;

/**
 * Shell commands for testing MCP tools.
 */
@ShellComponent
@RequiredArgsConstructor
public class McpCommands {

    private final McpToolService mcpToolService;

    @ShellMethod(key = "mcp-process", value = "Get information about a process by PID")
    public String getProcessInfo(
            @ShellOption(help = "Process ID") int pid) {
        
        ProcessInfo info = mcpToolService.getProcessInfo(pid);
        if (info == null) {
            return "Process not found: " + pid;
        }
        
        return String.format("""
                Process Information:
                  PID: %d
                  Name: %s
                  CPU: %.2f%%
                  Memory: %d MB
                  Threads: %d
                """,
                info.getPid(),
                info.getName(),
                info.getCpuPercent(),
                info.getMemoryBytes() / (1024 * 1024),
                info.getThreadCount());
    }

    @ShellMethod(key = "mcp-threads", value = "Get threads for a process")
    public String getThreads(
            @ShellOption(help = "Process ID") int pid) {
        
        JsonNode threads = mcpToolService.getThreads(pid);
        if (threads == null) {
            return "Unable to get threads for PID: " + pid;
        }
        
        return formatJson(threads);
    }

    @ShellMethod(key = "mcp-io", value = "Get I/O statistics")
    public String getIOStats() {
        JsonNode stats = mcpToolService.getIOStats();
        if (stats == null) {
            return "Unable to get I/O statistics";
        }
        
        return formatJson(stats);
    }

    @ShellMethod(key = "mcp-counters", value = "Get performance counters")
    public String getPerformanceCounters(
            @ShellOption(help = "Category (Memory, Processor, PhysicalDisk)") String category,
            @ShellOption(help = "Include detailed counters", defaultValue = "false") boolean detailed) {
        
        JsonNode counters = mcpToolService.getPerformanceCounters(category, detailed);
        if (counters == null) {
            return "Unable to get performance counters for: " + category;
        }
        
        return formatJson(counters);
    }

    @ShellMethod(key = "mcp-events", value = "Read Windows Event Log")
    public String readEventLog(
            @ShellOption(help = "Log name (Application, System, Security)", defaultValue = "System") String logName,
            @ShellOption(help = "Maximum entries", defaultValue = "10") int limit,
            @ShellOption(help = "Event type (Error, Warning, Information)", defaultValue = "") String eventType) {
        
        String type = eventType.isEmpty() ? null : eventType;
        JsonNode events = mcpToolService.readEventLog(logName, limit, type);
        if (events == null) {
            return "Unable to read event log: " + logName;
        }
        
        return formatJson(events);
    }

    @ShellMethod(key = "mcp-top", value = "Get top processes by resource usage")
    public String getTopProcesses(
            @ShellOption(help = "Number of processes", defaultValue = "10") int limit,
            @ShellOption(help = "Sort by (cpu, memory)", defaultValue = "cpu") String sortBy) {
        
        List<ProcessInfo> processes = mcpToolService.getTopProcesses(limit, sortBy);
        if (processes.isEmpty()) {
            return "No processes found";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Top %d processes by %s:\n\n", processes.size(), sortBy));
        sb.append(String.format("%-8s %-30s %10s %12s %8s\n", "PID", "NAME", "CPU%", "MEMORY(MB)", "THREADS"));
        sb.append("-".repeat(75)).append("\n");
        
        for (ProcessInfo p : processes) {
            sb.append(String.format("%-8d %-30s %10.2f %12d %8d\n",
                    p.getPid(),
                    truncate(p.getName(), 30),
                    p.getCpuPercent(),
                    p.getMemoryBytes() / (1024 * 1024),
                    p.getThreadCount()));
        }
        
        return sb.toString();
    }

    private String formatJson(JsonNode json) {
        try {
            return json.toPrettyString();
        } catch (Exception e) {
            return json.toString();
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen - 3) + "..." : s;
    }
}
