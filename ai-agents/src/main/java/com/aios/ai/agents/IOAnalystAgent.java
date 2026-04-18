package com.aios.ai.agents;

import com.aios.ai.dto.AiAnalysisResult;
import com.aios.ai.service.McpToolService;
import com.aios.shared.dto.DiagnosticIssue;
import com.aios.shared.dto.ProcessInfo;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI Agent specialized in I/O bottleneck analysis.
 * Analyzes disk and network I/O patterns to identify bottlenecks.
 */
@Service
@Slf4j
public class IOAnalystAgent {

    private final ChatLanguageModel model;
    private final McpToolService mcpTools;

    public IOAnalystAgent(
            @Value("${gemini.api.key:}") String apiKey,
            McpToolService mcpTools) {

        this.mcpTools = mcpTools;

        if (apiKey != null && !apiKey.isEmpty()) {
            this.model = GoogleAiGeminiChatModel.builder()
                    .apiKey(apiKey)
                    .modelName("gemini-1.5-pro")
                    .temperature(0.3)
                    .maxOutputTokens(2000)
                    .timeout(java.time.Duration.ofSeconds(30))
                    .build();
        } else {
            this.model = null;
            log.warn("Gemini API key not configured - IOAnalystAgent will use rule-based analysis");
        }
    }

    /**
     * Analyze I/O bottleneck issues.
     */
    public AiAnalysisResult analyzeIO(DiagnosticIssue issue) {
        log.info("IOAnalystAgent analyzing I/O for PID {}", issue.getAffectedPid());

        // Collect evidence from MCP tools
        ProcessInfo process = mcpTools.getProcessInfo(issue.getAffectedPid());
        JsonNode ioStats = mcpTools.getIOStats();
        JsonNode diskCounters = mcpTools.getPerformanceCounters("disk", true);
        JsonNode networkCounters = mcpTools.getPerformanceCounters("network", true);

        if (process == null) {
            return AiAnalysisResult.builder()
                    .confidence(0.0)
                    .rootCause("Process not found")
                    .agentName("IOAnalystAgent")
                    .build();
        }

        // Build evidence map
        Map<String, Object> evidence = new HashMap<>();
        evidence.put("processName", process.getName());
        evidence.put("cpuPercent", process.getCpuPercent());
        if (ioStats != null) {
            evidence.put("ioStats", ioStats.toString());
        }

        // Use AI model if available, otherwise rule-based
        if (model != null) {
            return analyzeWithAI(process, ioStats, diskCounters, networkCounters, issue, evidence);
        } else {
            return analyzeWithRules(process, ioStats, issue, evidence);
        }
    }

    private AiAnalysisResult analyzeWithAI(ProcessInfo process, JsonNode ioStats,
            JsonNode diskCounters, JsonNode networkCounters,
            DiagnosticIssue issue, Map<String, Object> evidence) {
        String prompt = String.format("""
                You are an I/O performance expert analyzing system I/O bottlenecks.

                ## Process Information:
                - Name: %s
                - PID: %d
                - CPU Usage: %.2f%%

                ## Detection Evidence:
                %s

                ## Disk I/O Statistics:
                %s

                ## Disk Performance Counters:
                %s

                ## Network Statistics:
                %s

                ## Analysis Request:
                Analyze for:
                1. Disk I/O bottlenecks (high queue length, slow transfers)
                2. Network I/O bottlenecks (high latency, packet loss)
                3. Excessive disk writes (potential logging issue)
                4. Storage capacity issues

                Provide:
                - ROOT_CAUSE: <specific I/O issue identified>
                - CONFIDENCE: <0.0-1.0>
                - RECOMMENDED_ACTION: <REDUCE_PRIORITY, THROTTLE_IO, or MONITOR>
                - REASONING: <detailed explanation>
                - ALTERNATIVES: <other possible causes>
                - RISK: <risk assessment>
                """,
                process.getName(),
                process.getPid(),
                process.getCpuPercent(),
                issue.getDetails(),
                ioStats != null ? ioStats.toString() : "N/A",
                diskCounters != null ? diskCounters.toString() : "N/A",
                networkCounters != null ? networkCounters.toString() : "N/A");

        try {
            String response = model.generate(prompt);
            return parseAiResponse(response, evidence);
        } catch (Exception e) {
            log.error("AI analysis failed: {}", e.getMessage());
            return analyzeWithRules(process, ioStats, issue, evidence);
        }
    }

    private AiAnalysisResult parseAiResponse(String response, Map<String, Object> evidence) {
        try {
            String rootCause = extractField(response, "ROOT_CAUSE:");
            double confidence = Double.parseDouble(extractField(response, "CONFIDENCE:").trim());
            String recommendedAction = extractField(response, "RECOMMENDED_ACTION:");
            String reasoning = extractField(response, "REASONING:");
            String alternatives = extractField(response, "ALTERNATIVES:");
            String risk = extractField(response, "RISK:");

            return AiAnalysisResult.builder()
                    .rootCause(rootCause)
                    .confidence(Math.min(1.0, Math.max(0.0, confidence)))
                    .recommendedAction(recommendedAction)
                    .reasoning(reasoning)
                    .alternativeCauses(List.of(alternatives.split(",")))
                    .riskAssessment(risk)
                    .evidence(evidence)
                    .agentName("IOAnalystAgent")
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse AI response: {}", e.getMessage());
            return AiAnalysisResult.builder()
                    .rootCause("Analysis parsing failed")
                    .confidence(0.3)
                    .reasoning(response)
                    .evidence(evidence)
                    .agentName("IOAnalystAgent")
                    .build();
        }
    }

    private String extractField(String response, String fieldName) {
        int startIdx = response.indexOf(fieldName);
        if (startIdx == -1)
            return "";
        startIdx += fieldName.length();
        int endIdx = response.indexOf("\n", startIdx);
        if (endIdx == -1)
            endIdx = response.length();
        return response.substring(startIdx, endIdx).trim();
    }

    private AiAnalysisResult analyzeWithRules(ProcessInfo process, JsonNode ioStats,
            DiagnosticIssue issue, Map<String, Object> evidence) {
        // Rule-based fallback analysis
        double confidence = issue.getConfidence();
        String rootCause;
        String recommendation;

        // Extract disk queue length if available
        long queueLength = 0;
        if (ioStats != null && ioStats.has("disks")) {
            for (JsonNode disk : ioStats.get("disks")) {
                if (disk.has("queueLength")) {
                    queueLength = Math.max(queueLength, disk.get("queueLength").asLong());
                }
            }
        }

        if (queueLength > 10) {
            rootCause = "High disk queue length - severe I/O bottleneck";
            recommendation = "REDUCE_PRIORITY";
            confidence = Math.max(confidence, 0.8);
        } else if (queueLength > 5) {
            rootCause = "Elevated disk queue - moderate I/O contention";
            recommendation = "REDUCE_PRIORITY";
            confidence = Math.max(confidence, 0.6);
        } else if (process.getCpuPercent() < 5) {
            rootCause = "Low CPU with potential I/O wait";
            recommendation = "MONITOR";
            confidence = Math.max(confidence, 0.5);
        } else {
            rootCause = "I/O metrics within normal range";
            recommendation = "MONITOR";
            confidence = Math.max(confidence, 0.4);
        }

        return AiAnalysisResult.builder()
                .rootCause(rootCause)
                .confidence(confidence)
                .recommendedAction(recommendation)
                .reasoning("Rule-based analysis based on disk queue length and I/O patterns")
                .riskAssessment("Low - I/O throttling is generally safe")
                .evidence(evidence)
                .agentName("IOAnalystAgent (Rule-Based)")
                .build();
    }
}
