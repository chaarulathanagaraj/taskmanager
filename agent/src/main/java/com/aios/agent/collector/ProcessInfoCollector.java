package com.aios.agent.collector;

import com.aios.shared.dto.ProcessInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for collecting process-level information using OSHI.
 * Provides methods to retrieve process details including CPU, memory, threads, handles, and I/O statistics.
 */
@Service
@Slf4j
public class ProcessInfoCollector {

    private final SystemInfo systemInfo;
    private final OperatingSystem os;
    
    // Cache previous CPU measurements for accurate CPU % calculation
    private final Map<Integer, Long> previousCpuTime = new ConcurrentHashMap<>();
    private final Map<Integer, Long> previousTimestamp = new ConcurrentHashMap<>();
    
    // Comparators for different sorting options
    private static final Comparator<OSProcess> CPU_DESC = 
        Comparator.comparingDouble((OSProcess p) -> p.getProcessCpuLoadCumulative()).reversed();
    private static final Comparator<OSProcess> MEMORY_DESC = 
        Comparator.comparingLong(OSProcess::getResidentSetSize).reversed();
    private static final Comparator<OSProcess> NAME_ASC = 
        Comparator.comparing(OSProcess::getName, String.CASE_INSENSITIVE_ORDER);

    public ProcessInfoCollector() {
        this.systemInfo = new SystemInfo();
        this.os = systemInfo.getOperatingSystem();
        
        log.info("ProcessInfoCollector initialized. OS: {} {}", 
            os.getFamily(), os.getVersionInfo());
    }

    /**
     * Get top N processes sorted by CPU usage
     * @param limit Maximum number of processes to return
     * @return List of ProcessInfo DTOs
     */
    public List<ProcessInfo> getTopProcesses(int limit) {
        try {
            // Get all processes and sort by CPU
            List<OSProcess> osProcesses = os.getProcesses(null, CPU_DESC, limit);

            return osProcesses.stream()
                .map(this::toProcessInfo)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Error getting top processes", e);
            return List.of();
        }
    }

    /**
     * Get top N processes sorted by memory usage
     * @param limit Maximum number of processes to return
     * @return List of ProcessInfo DTOs
     */
    public List<ProcessInfo> getTopProcessesByMemory(int limit) {
        try {
            // Get processes sorted by memory usage
            List<OSProcess> osProcesses = os.getProcesses(null, MEMORY_DESC, limit);

            return osProcesses.stream()
                .map(this::toProcessInfo)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Error getting top processes by memory", e);
            return List.of();
        }
    }

    /**
     * Get information for a specific process by PID
     * @param pid Process ID
     * @return Optional containing ProcessInfo if process exists
     */
    public Optional<ProcessInfo> getProcessInfo(int pid) {
        try {
            OSProcess process = os.getProcess(pid);
            
            if (process == null || !process.updateAttributes()) {
                log.debug("Process with PID {} not found or cannot update", pid);
                return Optional.empty();
            }
            
            return Optional.of(toProcessInfo(process));
            
        } catch (Exception e) {
            log.error("Error getting process info for PID {}", pid, e);
            return Optional.empty();
        }
    }

    /**
     * Get all running processes (use with caution - can be resource intensive)
     * @return List of all processes
     */
    public List<ProcessInfo> getAllProcesses() {
        try {
            List<OSProcess> osProcesses = os.getProcesses(null, CPU_DESC, 0);

            return osProcesses.stream()
                .map(this::toProcessInfo)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Error getting all processes", e);
            return List.of();
        }
    }

    /**
     * Search for processes by name pattern (case-insensitive)
     * @param namePattern Process name pattern to search for
     * @return List of matching processes
     */
    public List<ProcessInfo> findProcessesByName(String namePattern) {
        try {
            String lowerPattern = namePattern.toLowerCase();
            
            List<OSProcess> allProcesses = os.getProcesses(null, NAME_ASC, 0);

            return allProcesses.stream()
                .filter(p -> p.getName().toLowerCase().contains(lowerPattern))
                .map(this::toProcessInfo)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Error searching processes by name: {}", namePattern, e);
            return List.of();
        }
    }

    /**
     * Get process count
     * @return Total number of running processes
     */
    public int getProcessCount() {
        try {
            return os.getProcessCount();
        } catch (Exception e) {
            log.error("Error getting process count", e);
            return 0;
        }
    }

    /**
     * Get thread count across all processes
     * @return Total number of threads in the system
     */
    public int getThreadCount() {
        try {
            return os.getThreadCount();
        } catch (Exception e) {
            log.error("Error getting thread count", e);
            return 0;
        }
    }

    /**
     * Convert OSHI OSProcess to ProcessInfo DTO
     * @param process OSHI process object
     * @return ProcessInfo DTO
     */
    private ProcessInfo toProcessInfo(OSProcess process) {
        try {
            int pid = process.getProcessID();
            
            // Calculate CPU percentage
            double cpuPercent = calculateCpuPercent(process);
            
            return ProcessInfo.builder()
                .pid(pid)
                .name(process.getName())
                .cpuPercent(cpuPercent)
                .memoryBytes(process.getResidentSetSize())
                .threadCount(process.getThreadCount())
                .handleCount(getHandleCountSafe(process))
                .ioReadBytes(process.getBytesRead())
                .ioWriteBytes(process.getBytesWritten())
                .build();
                
        } catch (Exception e) {
            log.error("Error converting process to DTO: {}", process.getName(), e);
            
            // Return minimal info on error
            return ProcessInfo.builder()
                .pid(process.getProcessID())
                .name(process.getName())
                .cpuPercent(0.0)
                .memoryBytes(0L)
                .threadCount(0)
                .handleCount(0)
                .ioReadBytes(0L)
                .ioWriteBytes(0L)
                .build();
        }
    }

    /**
     * Calculate CPU percentage for a process
     * Uses cached previous measurements for more accurate results
     * @param process OSHI process object
     * @return CPU usage percentage (0-100)
     */
    private double calculateCpuPercent(OSProcess process) {
        try {
            int pid = process.getProcessID();
            long currentTime = System.currentTimeMillis();
            long currentCpuTime = process.getKernelTime() + process.getUserTime();
            
            // Get cached values
            Long prevCpuTime = previousCpuTime.get(pid);
            Long prevTime = previousTimestamp.get(pid);
            
            double cpuPercent = 0.0;
            
            if (prevCpuTime != null && prevTime != null) {
                long cpuDelta = currentCpuTime - prevCpuTime;
                long timeDelta = currentTime - prevTime;
                
                if (timeDelta > 0) {
                    // Calculate percentage
                    cpuPercent = (cpuDelta * 100.0) / (timeDelta * 1_000_000.0);
                    
                    // Cap at 100% per core (multiply by number of processors for total)
                    cpuPercent = Math.min(cpuPercent, 100.0);
                }
            } else {
                // First measurement, use cumulative load
                cpuPercent = process.getProcessCpuLoadCumulative() * 100.0;
            }
            
            // Update cache
            previousCpuTime.put(pid, currentCpuTime);
            previousTimestamp.put(pid, currentTime);
            
            // Round to 2 decimal places
            return Math.round(cpuPercent * 100.0) / 100.0;
            
        } catch (Exception e) {
            log.debug("Error calculating CPU percent for PID {}", process.getProcessID(), e);
            return 0.0;
        }
    }

    /**
     * Get handle count safely - OSHI 6.x removed getHandleCount(),
     * so we use openFiles as a proxy or return 0
     * @param process OSHI process object
     * @return Handle count or 0 if not available
     */
    private int getHandleCountSafe(OSProcess process) {
        try {
            // OSHI 6.x uses getOpenFiles() instead of getHandleCount()
            // Cast to int since the count should fit in int range
            return (int) process.getOpenFiles();
        } catch (Exception e) {
            // Fallback if method doesn't exist or fails
            return 0;
        }
    }

    /**
     * Clear cached CPU measurements (useful for cleanup)
     */
    public void clearCache() {
        previousCpuTime.clear();
        previousTimestamp.clear();
        log.debug("Process CPU cache cleared");
    }

    /**
     * Check if a process exists
     * @param pid Process ID
     * @return true if process exists
     */
    public boolean processExists(int pid) {
        try {
            OSProcess process = os.getProcess(pid);
            return process != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get parent process ID for a given process
     * @param pid Child process ID
     * @return Parent process ID, or 0 if not found
     */
    public int getParentPid(int pid) {
        try {
            OSProcess process = os.getProcess(pid);
            if (process != null) {
                return process.getParentProcessID();
            }
            return 0;
        } catch (Exception e) {
            log.error("Error getting parent PID for {}", pid, e);
            return 0;
        }
    }

    /**
     * Get command line for a process
     * @param pid Process ID
     * @return Command line string, or empty string if not available
     */
    public String getCommandLine(int pid) {
        try {
            OSProcess process = os.getProcess(pid);
            if (process != null) {
                return process.getCommandLine();
            }
            return "";
        } catch (Exception e) {
            log.error("Error getting command line for PID {}", pid, e);
            return "";
        }
    }
}
