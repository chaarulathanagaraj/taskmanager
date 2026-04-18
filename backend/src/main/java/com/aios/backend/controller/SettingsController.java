package com.aios.backend.controller;

import com.aios.backend.service.SettingsService;
import com.aios.shared.dto.AgentSettings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST controller for managing agent settings.
 * 
 * <p>
 * Provides endpoints for reading and updating agent configuration,
 * managing protected processes, and resetting to defaults.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@RestController
@RequestMapping("/api/settings")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Settings", description = "Agent settings management endpoints")
public class SettingsController {

    private final SettingsService settingsService;

    /**
     * Get current agent settings.
     */
    @GetMapping
    @Operation(summary = "Get settings", description = "Retrieve current agent configuration settings")
    public ResponseEntity<AgentSettings> getSettings() {
        return ResponseEntity.ok(settingsService.getSettings());
    }

    /**
     * Update agent settings.
     */
    @PostMapping
    @Operation(summary = "Update settings", description = "Update agent configuration settings")
    public ResponseEntity<AgentSettings> updateSettings(
            @RequestBody @Parameter(description = "Updated settings") AgentSettings settings) {
        log.info("Updating agent settings");
        AgentSettings updated = settingsService.updateSettings(settings);
        return ResponseEntity.ok(updated);
    }

    /**
     * Reset settings to defaults.
     */
    @PostMapping("/reset")
    @Operation(summary = "Reset settings", description = "Reset all settings to default values")
    public ResponseEntity<AgentSettings> resetSettings() {
        log.info("Resetting agent settings to defaults");
        AgentSettings defaults = settingsService.resetToDefaults();
        return ResponseEntity.ok(defaults);
    }

    /**
     * Get list of protected processes.
     */
    @GetMapping("/protected-processes")
    @Operation(summary = "Get protected processes", description = "Retrieve list of protected process names")
    public ResponseEntity<List<String>> getProtectedProcesses() {
        return ResponseEntity.ok(new ArrayList<>(settingsService.getSettings().getProtectedProcesses()));
    }

    /**
     * Add a process to the protected list.
     */
    @PostMapping("/protected-processes")
    @Operation(summary = "Add protected process", description = "Add a process name to the protected list")
    public ResponseEntity<AgentSettings> addProtectedProcess(
            @RequestBody @Parameter(description = "Process name to protect") Map<String, String> request) {
        String processName = request.get("processName");
        if (processName == null || processName.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        log.info("Adding protected process: {}", processName);
        AgentSettings updated = settingsService.addProtectedProcess(processName);
        return ResponseEntity.ok(updated);
    }

    /**
     * Remove a process from the protected list.
     */
    @DeleteMapping("/protected-processes/{processName}")
    @Operation(summary = "Remove protected process", description = "Remove a process name from the protected list")
    public ResponseEntity<AgentSettings> removeProtectedProcess(
            @PathVariable @Parameter(description = "Process name to remove") String processName) {
        log.info("Removing protected process: {}", processName);
        AgentSettings updated = settingsService.removeProtectedProcess(processName);
        return ResponseEntity.ok(updated);
    }

    /**
     * Check if a process is protected.
     */
    @GetMapping("/protected-processes/check/{processName}")
    @Operation(summary = "Check if protected", description = "Check if a process is in the protected list")
    public ResponseEntity<Map<String, Boolean>> isProcessProtected(
            @PathVariable @Parameter(description = "Process name to check") String processName) {
        boolean isProtected = settingsService.isProcessProtected(processName);
        return ResponseEntity.ok(Map.of("protected", isProtected));
    }

    /**
     * Get list of running system processes for selection.
     */
    @GetMapping("/running-processes")
    @Operation(summary = "Get running processes", description = "Get list of currently running processes on the system")
    public ResponseEntity<List<Map<String, Object>>> getRunningProcesses() {
        log.debug("Fetching running processes");
        List<Map<String, Object>> processes = settingsService.getRunningProcesses();
        return ResponseEntity.ok(processes);
    }
}
