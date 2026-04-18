package com.aios.backend.controller;

import com.aios.backend.service.LogService;
import com.aios.shared.dto.LogEntry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for viewing application logs.
 * 
 * <p>
 * Provides endpoints for fetching and filtering log entries
 * from the application log files.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Logs", description = "Log viewing endpoints")
public class LogController {

    private final LogService logService;

    /**
     * Get recent log entries with optional filtering.
     */
    @GetMapping
    @Operation(summary = "Get logs", description = "Retrieve recent log entries with optional filtering")
    public ResponseEntity<List<LogEntry>> getLogs(
            @RequestParam(name = "level", required = false, defaultValue = "ALL") @Parameter(description = "Log level filter: ALL, ERROR, WARN, INFO, DEBUG, TRACE") String level,

            @RequestParam(name = "search", required = false, defaultValue = "") @Parameter(description = "Search text to filter logs") String search,

            @RequestParam(name = "limit", required = false, defaultValue = "100") @Parameter(description = "Maximum number of entries to return") int limit) {

        log.debug("Fetching logs: level={}, search={}, limit={}", level, search, limit);
        List<LogEntry> logs = logService.getLogs(level, search, limit);
        return ResponseEntity.ok(logs);
    }

    /**
     * Get available log levels.
     */
    @GetMapping("/levels")
    @Operation(summary = "Get log levels", description = "Get list of available log levels for filtering")
    public ResponseEntity<List<String>> getLogLevels() {
        return ResponseEntity.ok(logService.getLogLevels());
    }

    /**
     * Get information about available log files.
     */
    @GetMapping("/files")
    @Operation(summary = "Get log files", description = "Get list of available log files with metadata")
    public ResponseEntity<List<Map<String, Object>>> getLogFiles() {
        return ResponseEntity.ok(logService.getLogFiles());
    }
}
