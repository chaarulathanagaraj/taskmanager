package com.aios.agent.remediation;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import lombok.extern.slf4j.Slf4j;

/**
 * Windows-specific native utilities using JNA (Java Native Access).
 * 
 * Provides access to Windows API functions for:
 * - Process priority manipulation
 * - Working set management (memory trimming)
 * - Process affinity settings
 * - I/O priority control
 * 
 * All methods are static and thread-safe.
 * Operations require appropriate Windows privileges.
 * 
 * Example usage:
 * <pre>
 * // Reduce process priority
 * WindowsNativeUtils.setPriority(1234, ProcessPriority.BELOW_NORMAL);
 * 
 * // Trim process memory
 * boolean success = WindowsNativeUtils.trimWorkingSet(1234);
 * 
 * // Suspend process
 * WindowsNativeUtils.suspendProcess(1234);
 * </pre>
 */
@Slf4j
public class WindowsNativeUtils {

    private static final Kernel32 KERNEL32 = Kernel32.INSTANCE;

    // Windows access rights constants
    private static final int PROCESS_SET_INFORMATION = 0x0200;
    private static final int PROCESS_SET_QUOTA = 0x0100;
    private static final int PROCESS_SUSPEND_RESUME = 0x0800;
    private static final int PROCESS_TERMINATE = 0x0001;
    private static final int PROCESS_QUERY_INFORMATION = 0x0400;

    /**
     * Set the priority class of a process.
     * 
     * Priority classes affect scheduling and resource allocation.
     * Lower priority processes get less CPU time.
     * 
     * @param pid Process ID
     * @param priority Desired priority level
     * @return true if successful, false otherwise
     * @throws RemediationException if operation fails critically
     */
    public static boolean setPriority(int pid, ProcessPriority priority) {
        log.debug("Setting priority for PID {} to {}", pid, priority);

        HANDLE hProcess = KERNEL32.OpenProcess(
            PROCESS_SET_INFORMATION,
            false,
            pid
        );

        if (hProcess == null || WinNT.INVALID_HANDLE_VALUE.equals(hProcess)) {
            log.error("Failed to open process {} for priority change: {}",
                pid, Native.getLastError());
            return false;
        }

        try {
            boolean result = KERNEL32.SetPriorityClass(hProcess, new DWORD(priority.getValue()));
            
            if (result) {
                log.info("Successfully set priority for PID {} to {}", pid, priority);
            } else {
                log.error("Failed to set priority for PID {}: {}",
                    pid, Native.getLastError());
            }
            
            return result;
        } finally {
            KERNEL32.CloseHandle(hProcess);
        }
    }

    /**
     * Trim the working set of a process.
     * 
     * This forces Windows to page out as much memory as possible,
     * reducing the process's RAM footprint. The process will page
     * memory back in as needed.
     * 
     * Useful for processes with memory leaks or high memory usage.
     * 
     * @param pid Process ID
     * @return true if successful, false otherwise
     */
    public static boolean trimWorkingSet(int pid) {
        log.debug("Trimming working set for PID {}", pid);

        // Note: Working set trimming requires SetProcessWorkingSetSize or K32EmptyWorkingSet
        // which are not available in the standard JNA Kernel32 interface.
        // This would require a custom JNA interface definition.
        log.warn("Working set trim not implemented - requires custom JNA interface for SetProcessWorkingSetSize");
        return false;
    }

    /**
     * Suspend all threads in a process.
     * 
     * This pauses the process completely until resumed.
     * Use with caution - suspended processes may cause deadlocks.
     * 
     * @param pid Process ID
     * @return true if successful, false otherwise
     */
    public static boolean suspendProcess(int pid) {
        log.debug("Suspending process PID {}", pid);

        HANDLE hProcess = KERNEL32.OpenProcess(
            PROCESS_SUSPEND_RESUME,
            false,
            pid
        );

        if (hProcess == null || WinNT.INVALID_HANDLE_VALUE.equals(hProcess)) {
            log.error("Failed to open process {} for suspend: {}",
                pid, Native.getLastError());
            return false;
        }

        try {
            // Note: NtSuspendProcess is not directly available in standard JNA
            // This is a simplified version - real implementation would need
            // to call ntdll.NtSuspendProcess via JNA Direct Mapping
            log.warn("Process suspension not fully implemented - requires ntdll access");
            return false;
        } finally {
            KERNEL32.CloseHandle(hProcess);
        }
    }

    /**
     * Resume a suspended process.
     * 
     * @param pid Process ID
     * @return true if successful, false otherwise
     */
    public static boolean resumeProcess(int pid) {
        log.debug("Resuming process PID {}", pid);

        HANDLE hProcess = KERNEL32.OpenProcess(
            PROCESS_SUSPEND_RESUME,
            false,
            pid
        );

        if (hProcess == null || WinNT.INVALID_HANDLE_VALUE.equals(hProcess)) {
            log.error("Failed to open process {} for resume: {}",
                pid, Native.getLastError());
            return false;
        }

        try {
            // Note: NtResumeProcess is not directly available in standard JNA
            log.warn("Process resume not fully implemented - requires ntdll access");
            return false;
        } finally {
            KERNEL32.CloseHandle(hProcess);
        }
    }

    /**
     * Terminate a process forcefully.
     * 
     * This is a last resort - the process has no chance to clean up.
     * Prefer ProcessHandle.destroy() for cleaner termination.
     * 
     * @param pid Process ID
     * @param exitCode Exit code to set
     * @return true if successful, false otherwise
     */
    public static boolean terminateProcess(int pid, int exitCode) {
        log.debug("Terminating process PID {} with exit code {}", pid, exitCode);

        HANDLE hProcess = KERNEL32.OpenProcess(
            PROCESS_TERMINATE,
            false,
            pid
        );

        if (hProcess == null || WinNT.INVALID_HANDLE_VALUE.equals(hProcess)) {
            log.error("Failed to open process {} for termination: {}",
                pid, Native.getLastError());
            return false;
        }

        try {
            boolean result = KERNEL32.TerminateProcess(hProcess, exitCode);
            
            if (result) {
                log.warn("Forcefully terminated process PID {}", pid);
            } else {
                log.error("Failed to terminate process PID {}: {}",
                    pid, Native.getLastError());
            }
            
            return result;
        } finally {
            KERNEL32.CloseHandle(hProcess);
        }
    }

    /**
     * Get the priority class of a process.
     * 
     * @param pid Process ID
     * @return ProcessPriority, or null if unable to determine
     */
    public static ProcessPriority getPriority(int pid) {
        HANDLE hProcess = KERNEL32.OpenProcess(
            PROCESS_QUERY_INFORMATION,
            false,
            pid
        );

        if (hProcess == null || WinNT.INVALID_HANDLE_VALUE.equals(hProcess)) {
            log.error("Failed to open process {} to query priority: {}",
                pid, Native.getLastError());
            return null;
        }

        try {
            DWORD priorityClass = KERNEL32.GetPriorityClass(hProcess);
            
            if (priorityClass == null || priorityClass.intValue() == 0) {
                log.error("Failed to get priority class for PID {}: {}",
                    pid, Native.getLastError());
                return null;
            }
            
            return ProcessPriority.fromValue(priorityClass.intValue());
        } finally {
            KERNEL32.CloseHandle(hProcess);
        }
    }

    /**
     * Check if a process exists and is accessible.
     * 
     * @param pid Process ID
     * @return true if process exists and can be opened
     */
    public static boolean isProcessAccessible(int pid) {
        HANDLE hProcess = KERNEL32.OpenProcess(
            PROCESS_QUERY_INFORMATION,
            false,
            pid
        );

        if (hProcess == null || WinNT.INVALID_HANDLE_VALUE.equals(hProcess)) {
            return false;
        }

        KERNEL32.CloseHandle(hProcess);
        return true;
    }

    /**
     * Get the last Windows error code.
     * 
     * @return Windows error code
     */
    public static int getLastError() {
        return Native.getLastError();
    }
}
