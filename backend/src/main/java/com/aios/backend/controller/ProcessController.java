package com.aios.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST controller for system process information.
 * 
 * <p>
 * Provides endpoints for retrieving detailed information about
 * running system processes, including CPU usage, memory consumption,
 * thread counts, and I/O statistics.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@RestController
@RequestMapping("/api/processes")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Tag(name = "Processes", description = "System process information endpoints")
public class ProcessController {

    /**
     * Get all running processes on the system.
     * 
     * <p>
     * Returns comprehensive information about all processes, including:
     * <ul>
     * <li>Process ID (PID)</li>
     * <li>Process name</li>
     * <li>CPU usage percentage</li>
     * <li>Memory usage in bytes</li>
     * <li>Thread count</li>
     * <li>Handle count (Windows)</li>
     * <li>I/O read/write bytes</li>
     * </ul>
     * 
     * @param sortBy field to sort by: cpu, memory, threads, or name (default: cpu)
     * @param limit  maximum number of processes to return (default: 1000, max:
     *               2000)
     * @return list of process information maps
     */
    @GetMapping
    @Operation(summary = "Get all processes", description = "Retrieve information about all running system processes")
    public ResponseEntity<Map<String, Object>> getAllProcesses(
            @RequestParam(name = "sortBy", defaultValue = "cpu") @Parameter(description = "Sort by: cpu, memory, threads, name, or pid") String sortBy,
            @RequestParam(name = "limit", defaultValue = "1000") @Parameter(description = "Maximum number of processes to return (max: 2000)") int limit) {

        log.debug("Fetching all processes, sortBy={}, limit={}", sortBy, limit);

        // Cap limit to prevent excessive data transfer
        if (limit > 2000) {
            limit = 2000;
        }

        try {
            SystemInfo si = new SystemInfo();
            OperatingSystem os = si.getOperatingSystem();

            // First snapshot
            List<OSProcess> processes1 = os.getProcesses();
            log.debug("First snapshot: {} processes", processes1.size());

            // Wait 1 second for accurate CPU measurement
            Thread.sleep(1000);

            // Second snapshot
            List<OSProcess> processes2 = os.getProcesses();
            log.debug("Second snapshot: {} processes", processes2.size());

            // Create map of old processes by PID for quick lookup
            Map<Integer, OSProcess> oldProcessMap = processes1.stream()
                    .collect(Collectors.toMap(OSProcess::getProcessID, p -> p));

            // Convert to DTO format with real-time CPU calculation
            List<Map<String, Object>> processInfoList = processes2.stream()
                    .filter(proc -> proc.getName() != null && !proc.getName().isEmpty())
                    .filter(proc -> proc.getProcessID() != 0) // Filter out Idle process (PID 0)
                    .map(proc -> {
                        Map<String, Object> info = new HashMap<>();
                        info.put("pid", proc.getProcessID());
                        info.put("name", proc.getName());

                        // Calculate real-time CPU usage
                        // Note: Values >100% are possible on multi-core systems
                        OSProcess oldProc = oldProcessMap.get(proc.getProcessID());
                        double cpuPercent = 0.0;
                        if (oldProc != null) {
                            cpuPercent = proc.getProcessCpuLoadBetweenTicks(oldProc) * 100.0;
                        }
                        info.put("cpuPercent", Math.round(cpuPercent * 100.0) / 100.0);

                        info.put("memoryBytes", proc.getResidentSetSize());
                        info.put("threadCount", proc.getThreadCount());
                        info.put("handleCount", 0); // Not available on all platforms
                        info.put("ioReadBytes", proc.getBytesRead());
                        info.put("ioWriteBytes", proc.getBytesWritten());
                        return info;
                    })
                    .sorted(getSortComparator(sortBy))
                    .limit(limit)
                    .collect(Collectors.toList());

            // Calculate statistics
            double totalCpu = processInfoList.stream()
                    .mapToDouble(p -> (Double) p.get("cpuPercent"))
                    .sum();

            long totalMemory = processInfoList.stream()
                    .mapToLong(p -> (Long) p.get("memoryBytes"))
                    .sum();

            int totalThreads = processInfoList.stream()
                    .mapToInt(p -> (Integer) p.get("threadCount"))
                    .sum();

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("processes", processInfoList);
            response.put("count", processInfoList.size());
            response.put("totalSystemProcesses", os.getProcessCount());
            response.put("statistics", Map.of(
                    "totalCpu", Math.round(totalCpu * 100.0) / 100.0,
                    "totalMemoryBytes", totalMemory,
                    "totalMemoryMB", totalMemory / (1024 * 1024),
                    "totalMemoryGB", Math.round(totalMemory / (1024.0 * 1024.0 * 1024.0) * 100.0) / 100.0,
                    "totalThreads", totalThreads,
                    "averageCpu",
                    processInfoList.isEmpty() ? 0 : Math.round((totalCpu / processInfoList.size()) * 100.0) / 100.0,
                    "averageMemoryMB",
                    processInfoList.isEmpty() ? 0 : (totalMemory / processInfoList.size()) / (1024 * 1024)));

            log.debug("Returning {} processes (total system: {})", processInfoList.size(), os.getProcessCount());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to fetch processes: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("processes", Collections.emptyList());
            errorResponse.put("count", 0);
            errorResponse.put("error", "Failed to fetch processes: " + e.getMessage());
            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * Get process by PID.
     * 
     * @param pid the process ID
     * @return process information or 404 if not found
     */
    @GetMapping("/{pid}")
    @Operation(summary = "Get process by PID", description = "Retrieve detailed information about a specific process")
    public ResponseEntity<Map<String, Object>> getProcessById(
            @PathVariable @Parameter(description = "Process ID") int pid) {

        log.debug("Fetching process with PID: {}", pid);

        try {
            SystemInfo si = new SystemInfo();
            OperatingSystem os = si.getOperatingSystem();

            // First snapshot
            OSProcess proc1 = os.getProcess(pid);
            if (proc1 == null || proc1.getName() == null || proc1.getName().isEmpty()) {
                log.warn("Process not found: PID {}", pid);
                return ResponseEntity.notFound().build();
            }

            // Wait 1 second for accurate CPU measurement
            Thread.sleep(1000);

            // Second snapshot
            OSProcess proc2 = os.getProcess(pid);
            if (proc2 == null || proc2.getName() == null || proc2.getName().isEmpty()) {
                log.warn("Process terminated during measurement: PID {}", pid);
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> info = new HashMap<>();
            info.put("pid", proc2.getProcessID());
            info.put("name", proc2.getName());

            // Calculate real-time CPU usage
            double cpuPercent = proc2.getProcessCpuLoadBetweenTicks(proc1) * 100.0;
            info.put("cpuPercent", Math.round(cpuPercent * 100.0) / 100.0);

            info.put("memoryBytes", proc2.getResidentSetSize());
            info.put("threadCount", proc2.getThreadCount());
            info.put("handleCount", 0); // Not available on all platforms
            info.put("ioReadBytes", proc2.getBytesRead());
            info.put("ioWriteBytes", proc2.getBytesWritten());
            info.put("startTime", proc2.getStartTime());
            info.put("upTime", proc2.getUpTime());
            info.put("path", proc2.getPath());
            info.put("commandLine", proc2.getCommandLine());
            info.put("user", proc2.getUser());
            info.put("userID", proc2.getUserID());
            info.put("group", proc2.getGroup());
            info.put("groupID", proc2.getGroupID());

            return ResponseEntity.ok(info);

        } catch (Exception e) {
            log.error("Failed to fetch process {}: {}", pid, e.getMessage(), e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get sort comparator based on field name.
     */
    private Comparator<Map<String, Object>> getSortComparator(String sortBy) {
        switch (sortBy.toLowerCase()) {
            case "memory":
                return Comparator.comparingLong((Map<String, Object> p) -> (Long) p.get("memoryBytes")).reversed();
            case "threads":
                return Comparator.comparingInt((Map<String, Object> p) -> (Integer) p.get("threadCount")).reversed();
            case "name":
                return Comparator.comparing((Map<String, Object> p) -> ((String) p.get("name")).toLowerCase());
            case "pid":
                return Comparator.comparingInt(p -> (Integer) p.get("pid"));
            case "cpu":
            default:
                return Comparator.comparingDouble((Map<String, Object> p) -> (Double) p.get("cpuPercent")).reversed();
        }
    }
}
