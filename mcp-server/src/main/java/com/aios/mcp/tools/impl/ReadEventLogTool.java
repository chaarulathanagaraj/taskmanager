package com.aios.mcp.tools.impl;

import com.aios.mcp.tools.McpTool;
import com.aios.mcp.tools.McpToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinNT;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * MCP Tool for reading Windows Event Log entries.
 * Provides access to Application, System, and Security logs.
 */
@Component
public class ReadEventLogTool implements McpTool {

    private final ObjectMapper objectMapper;

    // Valid event log names
    private static final Set<String> VALID_LOG_NAMES = Set.of(
            "Application", "System", "Security", "Setup");

    public ReadEventLogTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "read_event_log";
    }

    @Override
    public String getDescription() {
        return "Reads Windows Event Log entries. Supports Application, System, " +
                "and Security logs with filtering by event type and time range.";
    }

    @Override
    public JsonNode execute(JsonNode parameters) throws McpToolException {
        String logName = parameters.has("logName")
                ? parameters.get("logName").asText("Application")
                : "Application";
        int limit = parameters.has("limit")
                ? parameters.get("limit").asInt(100)
                : 100;
        String eventTypeFilter = parameters.has("eventType")
                ? parameters.get("eventType").asText(null)
                : null;

        // Validate log name
        if (!VALID_LOG_NAMES.contains(logName)) {
            throw new McpToolException(getName(),
                    "Invalid log name: " + logName + ". Valid options: " + VALID_LOG_NAMES,
                    McpToolException.ERROR_INVALID_PARAMETERS);
        }

        try {
            ArrayNode eventsArray = objectMapper.createArrayNode();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

            // Read events using JNA
            Advapi32Util.EventLogIterator iterator = new Advapi32Util.EventLogIterator(null, logName,
                    WinNT.EVENTLOG_BACKWARDS_READ);

            int count = 0;
            while (iterator.hasNext() && count < limit) {
                Advapi32Util.EventLogRecord record = iterator.next();

                // Filter by event type if specified
                String eventType = getEventTypeName(record.getType());
                if (eventTypeFilter != null && !eventTypeFilter.equalsIgnoreCase(eventType)) {
                    continue;
                }

                ObjectNode eventNode = objectMapper.createObjectNode();
                eventNode.put("recordNumber", record.getRecordNumber());
                eventNode.put("eventId", record.getEventId());
                eventNode.put("eventType", eventType);
                eventNode.put("source", record.getSource());

                // Format timestamp
                Instant timestamp = record.getRecord().TimeGenerated != null
                        ? Instant.ofEpochSecond(record.getRecord().TimeGenerated.intValue())
                        : Instant.now();
                eventNode.put("timestamp", formatter.format(timestamp));
                eventNode.put("timestampEpoch", timestamp.getEpochSecond());

                // Get strings if available
                String[] strings = record.getStrings();
                if (strings != null && strings.length > 0) {
                    ArrayNode stringsArray = objectMapper.createArrayNode();
                    for (String s : strings) {
                        if (s != null) {
                            stringsArray.add(s);
                        }
                    }
                    eventNode.set("messages", stringsArray);
                    // Join first few strings as a message preview
                    StringBuilder messagePreview = new StringBuilder();
                    for (int i = 0; i < Math.min(3, strings.length); i++) {
                        if (strings[i] != null) {
                            if (messagePreview.length() > 0)
                                messagePreview.append(" ");
                            messagePreview.append(strings[i]);
                        }
                    }
                    if (messagePreview.length() > 500) {
                        eventNode.put("messagePreview", messagePreview.substring(0, 500) + "...");
                    } else {
                        eventNode.put("messagePreview", messagePreview.toString());
                    }
                }

                eventsArray.add(eventNode);
                count++;
            }

            ObjectNode result = objectMapper.createObjectNode();
            result.put("logName", logName);
            result.put("count", eventsArray.size());
            result.put("requestedLimit", limit);
            result.set("events", eventsArray);

            return result;

        } catch (Exception e) {
            throw new McpToolException(getName(),
                    "Failed to read event log: " + e.getMessage(), e);
        }
    }

    private String getEventTypeName(Advapi32Util.EventLogType type) {
        if (type == null)
            return "Unknown";
        return switch (type) {
            case Error -> "Error";
            case Warning -> "Warning";
            case Informational -> "Information";
            case AuditSuccess -> "AuditSuccess";
            case AuditFailure -> "AuditFailure";
        };
    }

    @Override
    public JsonNode getSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode logName = objectMapper.createObjectNode();
        logName.put("type", "string");
        logName.put("description", "The event log to read (Application, System, Security, Setup)");
        logName.put("default", "Application");
        ArrayNode enumValues = objectMapper.createArrayNode();
        enumValues.add("Application");
        enumValues.add("System");
        enumValues.add("Security");
        enumValues.add("Setup");
        logName.set("enum", enumValues);
        properties.set("logName", logName);

        ObjectNode limit = objectMapper.createObjectNode();
        limit.put("type", "integer");
        limit.put("description", "Maximum number of events to return");
        limit.put("default", 100);
        limit.put("minimum", 1);
        limit.put("maximum", 1000);
        properties.set("limit", limit);

        ObjectNode eventType = objectMapper.createObjectNode();
        eventType.put("type", "string");
        eventType.put("description", "Filter by event type: Error, Warning, Information, AuditSuccess, AuditFailure");
        ArrayNode typeEnum = objectMapper.createArrayNode();
        typeEnum.add("Error");
        typeEnum.add("Warning");
        typeEnum.add("Information");
        typeEnum.add("AuditSuccess");
        typeEnum.add("AuditFailure");
        eventType.set("enum", typeEnum);
        properties.set("eventType", eventType);

        schema.set("properties", properties);
        return schema;
    }

    @Override
    public String getCategory() {
        return "system";
    }

    @Override
    public boolean requiresElevation() {
        return true; // Security log requires elevation
    }

    @Override
    public SafetyLevel getSafetyLevel() {
        return SafetyLevel.LOW;
    }
}
