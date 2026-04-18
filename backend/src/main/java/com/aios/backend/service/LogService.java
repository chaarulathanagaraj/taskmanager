package com.aios.backend.service;

import com.aios.shared.dto.LogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for reading and parsing log files.
 * 
 * <p>
 * Reads log entries from the configured log directory and
 * provides filtering by level and search text.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Service
@Slf4j
public class LogService {

    @Value("${logging.file.name:logs/aios-backend.log}")
    private String logFilePath;

    private static final int MAX_ENTRIES = 1000;
    private static final int TAIL_LINES = 500;

    // Pattern for standard logback format: 2024-01-15 10:30:45.123 [thread] LEVEL
    // logger - message
    private static final Pattern LOG_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+\\[([^\\]]+)\\]\\s+(\\w+)\\s+([\\w.]+)\\s+-\\s+(.*)$");

    // Alternative pattern: 2024-01-15 10:30:45.123 LEVEL [thread] logger - message
    private static final Pattern ALT_LOG_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(\\w+)\\s+\\[([^\\]]+)\\]\\s+([\\w.]+)\\s+-\\s+(.*)$");

    private static final DateTimeFormatter LOG_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * Get recent log entries with optional filtering.
     */
    public List<LogEntry> getLogs(String level, String search, int limit) {
        Path path = Paths.get(logFilePath);

        if (!Files.exists(path)) {
            log.warn("Log file not found: {}", logFilePath);
            return generateSampleLogs(level, search, limit);
        }

        try {
            List<String> lines = tailFile(path, TAIL_LINES);
            List<LogEntry> entries = parseLogLines(lines);

            return entries.stream()
                    .filter(entry -> filterByLevel(entry, level))
                    .filter(entry -> filterBySearch(entry, search))
                    .limit(limit > 0 ? limit : MAX_ENTRIES)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to read log file: {}", e.getMessage());
            return generateSampleLogs(level, search, limit);
        }
    }

    /**
     * Get available log levels.
     */
    public List<String> getLogLevels() {
        return Arrays.asList("ALL", "ERROR", "WARN", "INFO", "DEBUG", "TRACE");
    }

    /**
     * Get log files available.
     */
    public List<Map<String, Object>> getLogFiles() {
        Path logDir = Paths.get(logFilePath).getParent();
        if (logDir == null || !Files.exists(logDir)) {
            return Collections.emptyList();
        }

        try {
            return Files.list(logDir)
                    .filter(p -> p.toString().endsWith(".log"))
                    .map(p -> {
                        Map<String, Object> info = new LinkedHashMap<>();
                        try {
                            info.put("name", p.getFileName().toString());
                            info.put("size", Files.size(p));
                            info.put("lastModified", Files.getLastModifiedTime(p).toInstant());
                        } catch (IOException e) {
                            info.put("error", e.getMessage());
                        }
                        return info;
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to list log files: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Tail the last N lines of a file.
     */
    private List<String> tailFile(Path path, int lines) throws IOException {
        List<String> result = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.add(line);
                if (result.size() > lines) {
                    result.remove(0);
                }
            }
        }
        return result;
    }

    /**
     * Parse log lines into LogEntry objects.
     */
    private List<LogEntry> parseLogLines(List<String> lines) {
        List<LogEntry> entries = new ArrayList<>();
        LogEntry currentEntry = null;
        StringBuilder stackTrace = new StringBuilder();

        for (String line : lines) {
            LogEntry parsed = parseLine(line);
            if (parsed != null) {
                // Save previous entry with accumulated stack trace
                if (currentEntry != null) {
                    if (stackTrace.length() > 0) {
                        currentEntry.setStackTrace(stackTrace.toString());
                        stackTrace.setLength(0);
                    }
                    entries.add(currentEntry);
                }
                currentEntry = parsed;
            } else if (currentEntry != null) {
                // This is a continuation line (stack trace)
                if (stackTrace.length() > 0) {
                    stackTrace.append("\n");
                }
                stackTrace.append(line);
            }
        }

        // Don't forget the last entry
        if (currentEntry != null) {
            if (stackTrace.length() > 0) {
                currentEntry.setStackTrace(stackTrace.toString());
            }
            entries.add(currentEntry);
        }

        // Reverse to show newest first
        Collections.reverse(entries);
        return entries;
    }

    /**
     * Parse a single log line.
     */
    private LogEntry parseLine(String line) {
        Matcher matcher = LOG_PATTERN.matcher(line);
        if (matcher.matches()) {
            return createEntry(
                    matcher.group(1), // timestamp
                    matcher.group(2), // thread
                    matcher.group(3), // level
                    matcher.group(4), // logger
                    matcher.group(5) // message
            );
        }

        matcher = ALT_LOG_PATTERN.matcher(line);
        if (matcher.matches()) {
            return createEntry(
                    matcher.group(1), // timestamp
                    matcher.group(3), // thread
                    matcher.group(2), // level
                    matcher.group(4), // logger
                    matcher.group(5) // message
            );
        }

        return null;
    }

    /**
     * Create a LogEntry from parsed components.
     */
    private LogEntry createEntry(String timestamp, String thread, String level, String logger, String message) {
        Instant instant;
        try {
            LocalDateTime ldt = LocalDateTime.parse(timestamp, LOG_DATE_FORMAT);
            instant = ldt.atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException e) {
            instant = Instant.now();
        }

        return LogEntry.builder()
                .timestamp(instant)
                .thread(thread)
                .level(level.toUpperCase())
                .logger(logger)
                .loggerShort(LogEntry.shortenLogger(logger))
                .message(message)
                .source("BACKEND")
                .build();
    }

    /**
     * Filter by log level.
     */
    private boolean filterByLevel(LogEntry entry, String level) {
        if (level == null || level.isEmpty() || "ALL".equalsIgnoreCase(level)) {
            return true;
        }
        return level.equalsIgnoreCase(entry.getLevel());
    }

    /**
     * Filter by search text.
     */
    private boolean filterBySearch(LogEntry entry, String search) {
        if (search == null || search.isEmpty()) {
            return true;
        }
        String searchLower = search.toLowerCase();
        return (entry.getMessage() != null && entry.getMessage().toLowerCase().contains(searchLower))
                || (entry.getLogger() != null && entry.getLogger().toLowerCase().contains(searchLower))
                || (entry.getStackTrace() != null && entry.getStackTrace().toLowerCase().contains(searchLower));
    }

    /**
     * Generate sample logs when file is not available.
     */
    private List<LogEntry> generateSampleLogs(String level, String search, int limit) {
        List<LogEntry> samples = new ArrayList<>();
        Instant now = Instant.now();

        samples.add(LogEntry.builder()
                .timestamp(now.minusSeconds(5))
                .level("INFO")
                .logger("com.aios.backend.BackendApplication")
                .loggerShort("BackendApplication")
                .thread("main")
                .message("AIOS Backend started successfully")
                .source("BACKEND")
                .build());

        samples.add(LogEntry.builder()
                .timestamp(now.minusSeconds(10))
                .level("DEBUG")
                .logger("com.aios.backend.service.MetricService")
                .loggerShort("MetricService")
                .thread("scheduling-1")
                .message("Processing incoming metrics from agent")
                .source("BACKEND")
                .build());

        samples.add(LogEntry.builder()
                .timestamp(now.minusSeconds(30))
                .level("WARN")
                .logger("com.aios.backend.service.IssueService")
                .loggerShort("IssueService")
                .thread("http-nio-8080-exec-1")
                .message("High memory usage detected for process: chrome.exe (PID: 12345)")
                .source("BACKEND")
                .build());

        samples.add(LogEntry.builder()
                .timestamp(now.minusSeconds(60))
                .level("ERROR")
                .logger("com.aios.backend.controller.DiagnosisController")
                .loggerShort("DiagnosisController")
                .thread("http-nio-8080-exec-2")
                .message("AI diagnosis failed: Connection timeout")
                .stackTrace(
                        "java.net.SocketTimeoutException: connect timed out\n\tat java.net.Socket.connect(Socket.java:618)")
                .source("BACKEND")
                .build());

        samples.add(LogEntry.builder()
                .timestamp(now.minusSeconds(120))
                .level("INFO")
                .logger("com.aios.backend.service.ActionService")
                .loggerShort("ActionService")
                .thread("scheduling-1")
                .message("Remediation action completed: REDUCE_PRIORITY for notepad.exe")
                .source("BACKEND")
                .build());

        return samples.stream()
                .filter(entry -> filterByLevel(entry, level))
                .filter(entry -> filterBySearch(entry, search))
                .limit(limit > 0 ? limit : MAX_ENTRIES)
                .collect(Collectors.toList());
    }
}
