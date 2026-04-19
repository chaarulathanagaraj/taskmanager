package com.aios.backend.service;

import com.aios.backend.model.IssueEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Classifies processes into categories based on process name and
 * characteristics.
 * Used to determine appropriate actions and safety levels.
 */
@Service
@Slf4j
public class ProcessClassifier {

    /**
     * Process classification categories.
     */
    public enum ProcessClass {
        SYSTEM_CRITICAL, // Core OS processes - never touch
        SECURITY_PROCESS, // Security/antivirus - never restart
        WINDOWS_SERVICE, // Service-managed processes - use SC restart
        USER_PROCESS, // Normal user applications - all actions available
        TEMP_PROCESS, // Ephemeral/temporary processes - ignore
        UNKNOWN // Unable to classify - conservative approach
    }

    /**
     * System-critical processes that should never be touched.
     */
    private static final String[] SYSTEM_CRITICAL_PROCESSES = {
            "csrss.exe",
            "winlogon.exe",
            "services.exe",
            "lsass.exe",
            "System",
            "smss.exe",
            "svchost.exe",
            "wininit.exe",
            "dwm.exe",
            "WmiPrvSE",
            "WmiApSrv",
            "SearchIndexer",
            "mscorsvw"
    };

    /**
     * Security/antivirus processes - never restart.
     */
    private static final String[] SECURITY_PROCESSES = {
            "MsMpEng", // Windows Defender
            "WinDefend",
            "wscsvc", // Windows Security Center Service
            "WinSecuritySystem"
    };

    /**
     * Windows service-managed processes - use service restart instead of process
     * kill.
     */
    private static final String[] SERVICE_PROCESSES = {
            "OneDrive",
            "PcConnectionService",
            "InstantTransfer",
            "WmiPrvSE",
            "WmiApSrv",
            "SearchIndexer",
            "spoolsv.exe", // Print Spooler
            "w3wp.exe", // IIS
            "sqlserver.exe",
            "MsMpEng"
    };

    /**
     * Classify a process based on its name.
     *
     * @param processName the process name to classify
     * @return the ProcessClass category
     */
    public ProcessClass classify(String processName) {
        if (processName == null || processName.isBlank()) {
            return ProcessClass.UNKNOWN;
        }

        String normalized = processName.toLowerCase().trim();

        // Check for ephemeral/temp processes first
        if (isEphemeralProcess(normalized)) {
            return ProcessClass.TEMP_PROCESS;
        }

        // Check security processes
        if (matchesProcessSet(normalized, SECURITY_PROCESSES)) {
            return ProcessClass.SECURITY_PROCESS;
        }

        // Check system-critical processes
        if (matchesProcessSet(normalized, SYSTEM_CRITICAL_PROCESSES)) {
            return ProcessClass.SYSTEM_CRITICAL;
        }

        // Check service-managed processes
        if (matchesProcessSet(normalized, SERVICE_PROCESSES)) {
            return ProcessClass.WINDOWS_SERVICE;
        }

        // Check for dev/workspace processes
        if (isDevProcess(normalized)) {
            return ProcessClass.SYSTEM_CRITICAL; // Treat dev processes as system-critical
        }

        return ProcessClass.USER_PROCESS;
    }

    /**
     * Classify a process from an issue entity.
     *
     * @param issue the issue entity
     * @return the ProcessClass category
     */
    public ProcessClass classify(IssueEntity issue) {
        if (issue == null) {
            return ProcessClass.UNKNOWN;
        }
        return classify(issue.getProcessName());
    }

    /**
     * Check if a process is ephemeral (temporary files, temp processes).
     */
    private boolean isEphemeralProcess(String normalized) {
        return normalized.contains(".tmp")
                || normalized.contains("antigravity")
                || normalized.contains("temp")
                || normalized.endsWith(".tmp");
    }

    /**
     * Check if a process is a development/workspace process.
     */
    private boolean isDevProcess(String normalized) {
        return normalized.contains("java")
                || normalized.contains("javaw")
                || normalized.contains("node")
                || normalized.contains("npm")
                || normalized.contains("powershell")
                || normalized.contains("pwsh")
                || normalized.contains("code")
                || normalized.contains("devenv")
                || normalized.contains("maven")
                || normalized.contains("gradle");
    }

    /**
     * Check if process name matches any in the given set.
     */
    private boolean matchesProcessSet(String normalized, String[] processSet) {
        for (String process : processSet) {
            if (process == null)
                continue;
            String processLower = process.toLowerCase();

            // Match exact name or name without .exe
            if (normalized.equals(processLower)
                    || normalized.equals(processLower.replace(".exe", ""))
                    || (processLower + ".exe").equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get human-readable description of a process class.
     */
    public String getDescription(ProcessClass classification) {
        return switch (classification) {
            case SYSTEM_CRITICAL -> "System-critical process (core OS function)";
            case SECURITY_PROCESS -> "Security/antivirus process";
            case WINDOWS_SERVICE -> "Windows service (requires service manager)";
            case USER_PROCESS -> "User application";
            case TEMP_PROCESS -> "Temporary/ephemeral process";
            case UNKNOWN -> "Unknown classification";
        };
    }
}
