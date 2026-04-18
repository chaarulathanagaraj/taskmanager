package com.aios.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Log entry DTO for frontend display.
 * 
 * <p>
 * Represents a single log entry with timestamp, level, logger,
 * and message information.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEntry {

    /**
     * Timestamp when the log entry was created.
     */
    private Instant timestamp;

    /**
     * Log level: TRACE, DEBUG, INFO, WARN, ERROR.
     */
    private String level;

    /**
     * Logger name (typically the class name).
     */
    private String logger;

    /**
     * Shortened logger name for display.
     */
    private String loggerShort;

    /**
     * Thread name where the log was generated.
     */
    private String thread;

    /**
     * The log message.
     */
    private String message;

    /**
     * Stack trace if this is an error with exception.
     */
    private String stackTrace;

    /**
     * Source component: BACKEND, AGENT, MCP_SERVER, AI_AGENTS.
     */
    private String source;

    /**
     * Create a shortened logger name.
     */
    public static String shortenLogger(String fullLogger) {
        if (fullLogger == null)
            return "";
        int lastDot = fullLogger.lastIndexOf('.');
        return lastDot >= 0 ? fullLogger.substring(lastDot + 1) : fullLogger;
    }
}
