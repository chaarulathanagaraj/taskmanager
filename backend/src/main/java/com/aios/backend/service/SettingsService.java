package com.aios.backend.service;

import com.aios.shared.dto.AgentSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

/**
 * Service for managing agent settings.
 * Settings are persisted to a JSON file for simplicity.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SettingsService {

    private static final List<String> ESSENTIAL_PROTECTED_PROCESSES = List.of(
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
            "devenv.exe",
            "java.exe",
            "javaw.exe",
            "node.exe",
            "powershell.exe",
            "pwsh.exe",
            "cmd.exe",
            "conhost.exe");

    private static final String SETTINGS_FILE = "aios-settings.json";
    private static final Path SETTINGS_PATH = Paths.get(System.getProperty("user.home"), ".aios", SETTINGS_FILE);

    private final ObjectMapper objectMapper;
    private final WebSocketBroadcaster broadcaster;

    private AgentSettings currentSettings;

    @PostConstruct
    public void init() {
        loadSettings();
    }

    /**
     * Get current settings.
     */
    public AgentSettings getSettings() {
        return currentSettings;
    }

    /**
     * Update settings.
     * 
     * @param settings new settings to apply
     * @return updated settings
     */
    public AgentSettings updateSettings(AgentSettings settings) {
        log.info("Updating settings: dryRunMode={}, autoRemediation={}, confidenceThreshold={}",
                settings.isDryRunMode(), settings.isAutoRemediation(), settings.getConfidenceThreshold());

        // Validate settings
        validateSettings(settings);

        // Update current settings
        this.currentSettings = settings;
        normalizeProtectedProcesses();

        // Persist to file
        saveSettings();

        // Broadcast settings change to connected clients
        broadcaster.broadcastSettingsChange(settings);

        return currentSettings;
    }

    /**
     * Add a protected process pattern.
     */
    public AgentSettings addProtectedProcess(String processPattern) {
        log.info("Adding protected process: {}", processPattern);

        List<String> protectedProcesses = new ArrayList<>(currentSettings.getProtectedProcesses());
        if (!protectedProcesses.contains(processPattern)) {
            protectedProcesses.add(processPattern);
            currentSettings.setProtectedProcesses(protectedProcesses);
            saveSettings();
            broadcaster.broadcastSettingsChange(currentSettings);
        }

        return currentSettings;
    }

    /**
     * Remove a protected process pattern.
     */
    public AgentSettings removeProtectedProcess(String processPattern) {
        log.info("Removing protected process: {}", processPattern);

        // Don't allow removing critical system processes
        if (isNonRemovableProtectedProcess(processPattern)) {
            throw new IllegalArgumentException("Cannot remove critical system process: " + processPattern);
        }

        List<String> protectedProcesses = new ArrayList<>(currentSettings.getProtectedProcesses());
        protectedProcesses.remove(processPattern);
        currentSettings.setProtectedProcesses(protectedProcesses);
        saveSettings();
        broadcaster.broadcastSettingsChange(currentSettings);

        return currentSettings;
    }

    /**
     * Reset settings to defaults.
     */
    public AgentSettings resetToDefaults() {
        log.info("Resetting settings to defaults");
        this.currentSettings = AgentSettings.defaults();
        normalizeProtectedProcesses();
        saveSettings();
        broadcaster.broadcastSettingsChange(currentSettings);
        return currentSettings;
    }

    /**
     * Check if a process is in the protected list.
     */
    public boolean isProcessProtected(String processName) {
        if (processName == null || currentSettings.getProtectedProcesses() == null) {
            return false;
        }

        String lowerName = processName.toLowerCase();
        return currentSettings.getProtectedProcesses().stream()
                .anyMatch(pattern -> {
                    String lowerPattern = pattern.toLowerCase();
                    // Simple wildcard matching
                    if (lowerPattern.contains("*")) {
                        String regex = lowerPattern.replace(".", "\\.").replace("*", ".*");
                        return lowerName.matches(regex);
                    }
                    return lowerName.equals(lowerPattern) || lowerName.endsWith("\\" + lowerPattern);
                });
    }

    private boolean isCriticalSystemProcess(String processPattern) {
        List<String> criticalProcesses = List.of(
                "System", "csrss.exe", "lsass.exe", "winlogon.exe",
                "services.exe", "smss.exe", "wininit.exe",
            "code.exe", "Code.exe", "Code - Insiders.exe", "devenv.exe",
            "java.exe", "javaw.exe", "node.exe", "powershell.exe", "pwsh.exe", "cmd.exe", "conhost.exe");
        return criticalProcesses.stream()
                .anyMatch(p -> p.equalsIgnoreCase(processPattern));
    }

    private boolean isNonRemovableProtectedProcess(String processPattern) {
        return isCriticalSystemProcess(processPattern)
                || ESSENTIAL_PROTECTED_PROCESSES.stream().anyMatch(p -> p.equalsIgnoreCase(processPattern));
    }

    private void normalizeProtectedProcesses() {
        if (currentSettings == null) {
            return;
        }

        List<String> protectedProcesses = currentSettings.getProtectedProcesses();
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        merged.addAll(ESSENTIAL_PROTECTED_PROCESSES);
        if (protectedProcesses != null) {
            merged.addAll(protectedProcesses);
        }
        currentSettings.setProtectedProcesses(new ArrayList<>(merged));
    }

    private void validateSettings(AgentSettings settings) {
        if (settings.getConfidenceThreshold() < 0.0 || settings.getConfidenceThreshold() > 1.0) {
            throw new IllegalArgumentException("Confidence threshold must be between 0.0 and 1.0");
        }
        if (settings.getCollectionIntervalSeconds() < 5) {
            throw new IllegalArgumentException("Collection interval must be at least 5 seconds");
        }
        if (settings.getMaxConcurrentActions() < 1 || settings.getMaxConcurrentActions() > 10) {
            throw new IllegalArgumentException("Max concurrent actions must be between 1 and 10");
        }
    }

    private void loadSettings() {
        try {
            File settingsFile = SETTINGS_PATH.toFile();
            if (settingsFile.exists()) {
                this.currentSettings = objectMapper.readValue(settingsFile, AgentSettings.class);
                normalizeProtectedProcesses();
                saveSettings();
                log.info("Loaded settings from {}", SETTINGS_PATH);
            } else {
                log.info("No settings file found, using defaults");
                this.currentSettings = AgentSettings.defaults();
                normalizeProtectedProcesses();
                saveSettings();
            }
        } catch (IOException e) {
            log.warn("Failed to load settings, using defaults: {}", e.getMessage());
            this.currentSettings = AgentSettings.defaults();
            normalizeProtectedProcesses();
        }
    }

    private void saveSettings() {
        try {
            // Ensure directory exists
            Files.createDirectories(SETTINGS_PATH.getParent());

            // Write settings
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(SETTINGS_PATH.toFile(), currentSettings);
            log.debug("Settings saved to {}", SETTINGS_PATH);
        } catch (IOException e) {
            log.error("Failed to save settings: {}", e.getMessage(), e);
        }
    }

    /**
     * Get list of running processes on the system.
     * Returns unique process names sorted by CPU usage.
     */
    public List<Map<String, Object>> getRunningProcesses() {
        try {
            SystemInfo si = new SystemInfo();
            OperatingSystem os = si.getOperatingSystem();
            List<OSProcess> processes = os.getProcesses();

            // Get unique process names with their info
            Map<String, Map<String, Object>> uniqueProcesses = new HashMap<>();

            for (OSProcess proc : processes) {
                String name = proc.getName();
                if (name == null || name.isEmpty())
                    continue;

                // Keep highest CPU usage instance for each process name
                if (!uniqueProcesses.containsKey(name) ||
                        (double) uniqueProcesses.get(name).get("cpu") < proc.getProcessCpuLoadCumulative() * 100) {
                    Map<String, Object> info = new HashMap<>();
                    info.put("name", name);
                    info.put("pid", proc.getProcessID());
                    info.put("cpu", Math.round(proc.getProcessCpuLoadCumulative() * 100 * 100.0) / 100.0);
                    info.put("memory", proc.getResidentSetSize() / (1024 * 1024)); // MB
                    uniqueProcesses.put(name, info);
                }
            }

            // Sort by name and return
            return uniqueProcesses.values().stream()
                    .sorted(Comparator.comparing(m -> ((String) m.get("name")).toLowerCase()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get running processes: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
