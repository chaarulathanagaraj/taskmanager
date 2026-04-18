package com.aios.cli.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Shell commands for testing backend API.
 */
@ShellComponent
@Slf4j
public class BackendCommands {

    private final WebClient backendClient;
    private final ObjectMapper objectMapper;

    public BackendCommands(
            @Value("${backend.server.url:http://localhost:8080}") String backendUrl,
            ObjectMapper objectMapper) {
        this.backendClient = WebClient.builder()
                .baseUrl(backendUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
        this.objectMapper = objectMapper;
    }

    @ShellMethod(key = "issues", value = "List active issues from backend")
    public String listIssues(
            @ShellOption(help = "Max results", defaultValue = "10") int limit) {
        try {
            String response = backendClient.get()
                    .uri("/api/issues/active")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode issues = objectMapper.readTree(response);
            return formatIssueList(issues, limit);
        } catch (Exception e) {
            return "Error fetching issues: " + e.getMessage();
        }
    }

    @ShellMethod(key = "issue", value = "Get details for a specific issue")
    public String getIssue(
            @ShellOption(help = "Issue ID") long issueId) {
        try {
            String response = backendClient.get()
                    .uri("/api/issues/{id}", issueId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode issue = objectMapper.readTree(response);
            return formatIssueDetails(issue);
        } catch (Exception e) {
            return "Error fetching issue: " + e.getMessage();
        }
    }

    @ShellMethod(key = "run-diagnosis", value = "Trigger AI diagnosis via backend API")
    public String runDiagnosis(
            @ShellOption(help = "Issue ID") long issueId) {
        try {
            String response = backendClient.post()
                    .uri("/api/diagnose/{id}", issueId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode report = objectMapper.readTree(response);
            return formatDiagnosisResponse(report);
        } catch (Exception e) {
            return "Error running diagnosis: " + e.getMessage();
        }
    }

    @ShellMethod(key = "dashboard", value = "Get dashboard summary from backend")
    public String getDashboard() {
        try {
            String response = backendClient.get()
                    .uri("/api/dashboard/summary")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode summary = objectMapper.readTree(response);
            return formatDashboardSummary(summary);
        } catch (Exception e) {
            return "Error fetching dashboard: " + e.getMessage();
        }
    }

    @ShellMethod(key = "metrics", value = "Get current system metrics from backend")
    public String getMetrics() {
        try {
            String response = backendClient.get()
                    .uri("/api/metrics/current")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode metrics = objectMapper.readTree(response);
            return formatMetrics(metrics);
        } catch (Exception e) {
            return "Error fetching metrics: " + e.getMessage();
        }
    }

    private String formatIssueList(JsonNode issues, int limit) {
        if (!issues.isArray() || issues.isEmpty()) {
            return "No active issues found.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-6s %-18s %-10s %-8s %-20s %s\n",
                "ID", "TYPE", "SEVERITY", "CONF%", "PROCESS", "DETECTED"));
        sb.append("-".repeat(85)).append("\n");

        int count = 0;
        for (JsonNode issue : issues) {
            if (count >= limit) break;
            sb.append(String.format("%-6s %-18s %-10s %-8.1f %-20s %s\n",
                    issue.path("id").asText(),
                    truncate(issue.path("type").asText(), 18),
                    issue.path("severity").asText(),
                    issue.path("confidence").asDouble() * 100,
                    truncate(issue.path("processName").asText(), 20),
                    formatTimestamp(issue.path("detectedAt").asText())));
            count++;
        }

        if (issues.size() > limit) {
            sb.append(String.format("\n... and %d more issues\n", issues.size() - limit));
        }

        return sb.toString();
    }

    private String formatIssueDetails(JsonNode issue) {
        StringBuilder sb = new StringBuilder();
        sb.append("═══ Issue Details ═══\n\n");
        sb.append(String.format("ID: %s\n", issue.path("id").asText()));
        sb.append(String.format("Type: %s\n", issue.path("type").asText()));
        sb.append(String.format("Severity: %s\n", issue.path("severity").asText()));
        sb.append(String.format("Confidence: %.1f%%\n", issue.path("confidence").asDouble() * 100));
        sb.append(String.format("Process: %s (PID: %s)\n",
                issue.path("processName").asText(),
                issue.path("affectedPid").asText()));
        sb.append(String.format("Detected: %s\n", formatTimestamp(issue.path("detectedAt").asText())));
        sb.append(String.format("Resolved: %s\n", issue.path("resolved").asBoolean() ? "YES" : "NO"));
        sb.append(String.format("\nDetails:\n%s\n", issue.path("details").asText("N/A")));
        return sb.toString();
    }

    private String formatDiagnosisResponse(JsonNode report) {
        StringBuilder sb = new StringBuilder();
        sb.append("═══ Diagnosis Response ═══\n\n");
        sb.append(String.format("Success: %s\n", report.path("success").asBoolean() ? "YES" : "NO"));
        sb.append(String.format("Message: %s\n", report.path("message").asText()));
        sb.append(String.format("Confidence: %.1f%%\n", report.path("confidence").asDouble() * 100));
        sb.append(String.format("Processing Time: %d ms\n", report.path("processingTimeMs").asLong()));

        JsonNode analysis = report.path("analysis");
        if (!analysis.isMissingNode()) {
            sb.append("\n─── Analysis ───\n");
            sb.append(String.format("Root Cause: %s\n", analysis.path("rootCause").asText()));
            sb.append(String.format("Recommended Action: %s\n", analysis.path("recommendedAction").asText()));
        }

        JsonNode plan = report.path("remediationPlan");
        if (!plan.isMissingNode()) {
            sb.append("\n─── Remediation Plan ───\n");
            sb.append(String.format("Action: %s\n", plan.path("primaryAction").asText()));
            sb.append(String.format("Risk Level: %s\n", plan.path("riskLevel").asText()));
            sb.append(String.format("Approval Required: %s\n",
                    plan.path("approvalRequired").asBoolean() ? "YES" : "NO"));
        }

        JsonNode safety = report.path("safetyValidation");
        if (!safety.isMissingNode()) {
            sb.append("\n─── Safety ───\n");
            sb.append(String.format("Safe: %s\n", safety.path("safe").asBoolean() ? "YES" : "NO"));
            sb.append(String.format("Safety Score: %.1f%%\n", safety.path("safetyScore").asDouble() * 100));
        }

        return sb.toString();
    }

    private String formatDashboardSummary(JsonNode summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("═══ Dashboard Summary ═══\n\n");
        sb.append(String.format("Health Status: %s\n", summary.path("healthStatus").asText()));
        sb.append(String.format("Active Issues: %d\n", summary.path("activeIssueCount").asInt()));
        sb.append(String.format("Critical Issues: %d\n", summary.path("criticalIssueCount").asInt()));
        sb.append(String.format("High Priority Issues: %d\n", summary.path("highPriorityIssueCount").asInt()));

        JsonNode metrics = summary.path("currentMetrics");
        if (!metrics.isMissingNode()) {
            sb.append("\n─── Current Metrics ───\n");
            sb.append(String.format("CPU: %.1f%%\n", metrics.path("cpuPercent").asDouble()));
            sb.append(String.format("Memory: %.1f%%\n", metrics.path("memoryPercent").asDouble()));
            sb.append(String.format("Disk I/O: %.1f MB/s\n", metrics.path("diskIoMBps").asDouble()));
        }

        return sb.toString();
    }

    private String formatMetrics(JsonNode metrics) {
        StringBuilder sb = new StringBuilder();
        sb.append("═══ System Metrics ═══\n\n");
        sb.append(String.format("Timestamp: %s\n\n", formatTimestamp(metrics.path("timestamp").asText())));
        sb.append(String.format("CPU Usage: %.1f%%\n", metrics.path("cpuPercent").asDouble()));
        sb.append(String.format("Memory Used: %d MB / %d MB (%.1f%%)\n",
                metrics.path("memoryUsedBytes").asLong() / (1024 * 1024),
                metrics.path("memoryTotalBytes").asLong() / (1024 * 1024),
                metrics.path("memoryPercent").asDouble()));
        sb.append(String.format("Disk Read: %.2f MB/s\n", metrics.path("diskReadBytesPerSec").asLong() / (1024.0 * 1024)));
        sb.append(String.format("Disk Write: %.2f MB/s\n", metrics.path("diskWriteBytesPerSec").asLong() / (1024.0 * 1024)));
        sb.append(String.format("Network Sent: %.2f MB/s\n", metrics.path("networkSentBytesPerSec").asLong() / (1024.0 * 1024)));
        sb.append(String.format("Network Recv: %.2f MB/s\n", metrics.path("networkRecvBytesPerSec").asLong() / (1024.0 * 1024)));
        sb.append(String.format("Process Count: %d\n", metrics.path("processCount").asInt()));
        sb.append(String.format("Thread Count: %d\n", metrics.path("threadCount").asInt()));
        return sb.toString();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen - 3) + "..." : s;
    }

    private String formatTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) return "N/A";
        try {
            // Simple extraction of date-time part
            if (timestamp.contains("T")) {
                return timestamp.replace("T", " ").substring(0, 19);
            }
            return timestamp;
        } catch (Exception e) {
            return timestamp;
        }
    }
}
