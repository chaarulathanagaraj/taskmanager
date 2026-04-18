package com.aios.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for agent configuration settings.
 * These settings control agent behavior including remediation policies.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSettings {

    /**
     * Whether actions are simulated without execution
     */
    @Builder.Default
    private boolean dryRunMode = true;

    /**
     * Whether auto-remediation is enabled for high-confidence issues
     */
    @Builder.Default
    private boolean autoRemediation = false;

    /**
     * Minimum confidence threshold for auto-remediation (0.0-1.0)
     */
    @Builder.Default
    private double confidenceThreshold = 0.85;

    /**
     * Maximum concurrent remediation actions
     */
    @Builder.Default
    private int maxConcurrentActions = 3;

    /**
     * Metrics collection interval in seconds
     */
    @Builder.Default
    private int collectionIntervalSeconds = 10;

    /**
     * Detection interval in seconds
     */
    @Builder.Default
    private int detectionIntervalSeconds = 30;

    /**
     * List of protected process name patterns
     */
    private List<String> protectedProcesses;

    /**
     * Whether to notify on critical issues
     */
    @Builder.Default
    private boolean notifyOnCritical = true;

    /**
     * Whether to notify on high-severity issues
     */
    @Builder.Default
    private boolean notifyOnHigh = true;

    /**
     * Backend server URL for agent communication
     */
    @Builder.Default
    private String backendUrl = "http://localhost:8080";

    /**
     * MCP server URL
     */
    @Builder.Default
    private String mcpServerUrl = "http://localhost:8081";

    /**
     * Whether AI diagnosis is enabled
     */
    @Builder.Default
    private boolean aiDiagnosisEnabled = true;

    /**
     * Auto-trigger AI for issues with confidence below this threshold
     */
    @Builder.Default
    private double aiAutoTriggerThreshold = 0.6;

    /**
     * Default settings
     */
    public static AgentSettings defaults() {
        return AgentSettings.builder()
                .dryRunMode(true)
                .autoRemediation(false)
                .confidenceThreshold(0.85)
                .maxConcurrentActions(3)
                .collectionIntervalSeconds(10)
                .detectionIntervalSeconds(30)
                .protectedProcesses(List.of(
                        "System",
                        "csrss.exe",
                        "lsass.exe",
                        "winlogon.exe",
                        "services.exe",
                        "smss.exe",
                        "svchost.exe",
                        "wininit.exe",
                        "dwm.exe",
                        "code.exe",
                        "Code.exe",
                        "Code - Insiders.exe",
                        "devenv.exe"))
                .notifyOnCritical(true)
                .notifyOnHigh(true)
                .backendUrl("http://localhost:8080")
                .mcpServerUrl("http://localhost:8081")
                .aiDiagnosisEnabled(true)
                .aiAutoTriggerThreshold(0.6)
                .build();
    }
}
