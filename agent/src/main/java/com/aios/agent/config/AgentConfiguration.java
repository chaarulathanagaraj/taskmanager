package com.aios.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the AIOS monitoring agent.
 * Binds properties from application.properties with prefix "agent".
 * 
 * Example application.properties:
 * 
 * <pre>
 * agent.collection-interval-seconds=10
 * agent.retention-minutes=60
 * agent.backend-url=http://localhost:8080
 * agent.dry-run-mode=true
 * agent.protected-processes=System,csrss.exe,lsass.exe
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "agent")
@Data
public class AgentConfiguration {

    /**
     * Interval in seconds between metric collection cycles.
     * Default: 10 seconds
     */
    private int collectionIntervalSeconds = 10;

    /**
     * Number of minutes to retain metrics in memory.
     * Default: 60 minutes (1 hour)
     */
    private int retentionMinutes = 60;

    /**
     * URL of the backend server for syncing data.
     * Default: http://localhost:8080
     */
    private String backendUrl = "http://localhost:8080";

    /**
     * Enable dry-run mode (no actual remediation actions executed).
     * When true, all remediation actions are simulated only.
     * Default: true (safe mode)
     */
    private boolean dryRunMode = true;

    /**
     * List of protected process names that cannot be terminated.
     * These are critical system processes that should never be killed.
     */
    private List<String> protectedProcesses = new ArrayList<>(List.of(
            "System",
            "Registry",
            "csrss.exe",
            "lsass.exe",
            "winlogon.exe",
            "services.exe",
            "smss.exe",
            "svchost.exe",
            "wininit.exe",
            "dwm.exe"));

    /**
     * Maximum number of concurrent remediation actions.
     * Default: 3
     */
    private int maxConcurrentActions = 3;

    /**
     * Enable automatic remediation (vs manual approval).
     * Default: false
     */
    private boolean autoRemediationEnabled = false;

    /**
     * Minimum confidence threshold for automated actions (0.0-1.0).
     * Actions with confidence below this require manual approval.
     * Default: 0.85 (85%)
     */
    private double autoRemediationConfidenceThreshold = 0.85;

    /**
     * Enable backend synchronization.
     * Default: true
     */
    private boolean backendSyncEnabled = true;

    /**
     * Interval in seconds between backend sync attempts.
     * Default: 30 seconds
     */
    private int backendSyncIntervalSeconds = 30;

    /**
     * Maximum number of metrics to send in one batch to backend.
     * Default: 100
     */
    private int backendSyncBatchSize = 100;

    /**
     * Enable issue detection.
     * Default: true
     */
    private boolean detectionEnabled = true;

    /**
     * Interval in seconds between detection cycles.
     * Default: 30 seconds
     */
    private int detectionIntervalSeconds = 30;

    /**
     * Number of top processes to monitor for issues.
     * Default: 50
     */
    private int monitoredProcessLimit = 50;

    /**
     * Check if a process name is protected
     * 
     * @param processName Process name to check
     * @return true if the process is protected
     */
    public boolean isProtectedProcess(String processName) {
        if (processName == null || processName.isEmpty()) {
            return false;
        }

        return protectedProcesses.stream()
                .anyMatch(pattern -> processName.equalsIgnoreCase(pattern) ||
                        processName.toLowerCase().contains(pattern.toLowerCase()));
    }

    /**
     * Get the maximum number of metric snapshots to retain in memory
     * 
     * @return Number of snapshots (based on retention time and collection interval)
     */
    public int getMaxMetricSnapshots() {
        // Calculate: (retention minutes * 60 seconds) / collection interval
        return (retentionMinutes * 60) / collectionIntervalSeconds;
    }

    /**
     * Check if automatic remediation should be applied for a given confidence level
     * 
     * @param confidence Confidence level (0.0-1.0)
     * @return true if confidence meets threshold and auto-remediation is enabled
     */
    public boolean shouldAutoRemediate(double confidence) {
        return autoRemediationEnabled &&
                confidence >= autoRemediationConfidenceThreshold;
    }

    /**
     * Validate configuration on startup
     * 
     * @throws IllegalStateException if configuration is invalid
     */
    public void validate() {
        if (collectionIntervalSeconds <= 0) {
            throw new IllegalStateException("collection-interval-seconds must be positive");
        }
        if (retentionMinutes <= 0) {
            throw new IllegalStateException("retention-minutes must be positive");
        }
        if (backendUrl == null || backendUrl.isEmpty()) {
            throw new IllegalStateException("backend-url must be set");
        }
        if (autoRemediationConfidenceThreshold < 0.0 || autoRemediationConfidenceThreshold > 1.0) {
            throw new IllegalStateException("auto-remediation-confidence-threshold must be between 0.0 and 1.0");
        }
        if (maxConcurrentActions <= 0) {
            throw new IllegalStateException("max-concurrent-actions must be positive");
        }
        if (detectionIntervalSeconds <= 0) {
            throw new IllegalStateException("detection-interval-seconds must be positive");
        }
        if (monitoredProcessLimit <= 0) {
            throw new IllegalStateException("monitored-process-limit must be positive");
        }
    }
}
