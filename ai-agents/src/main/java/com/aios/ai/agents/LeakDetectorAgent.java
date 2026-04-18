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
 * AI Agent specialized in memory leak detection and analysis.
 * Uses GPT-4 to analyze process memory patterns and identify leaks.
 */
@Service
@Slf4j
public class LeakDetectorAgent {

    private final ChatLanguageModel model;
    private final McpToolService mcpTools;

    public LeakDetectorAgent(
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
            log.warn("Gemini API key not configured - LeakDetectorAgent will use rule-based analysis");
        }
    }

    /**
     * Analyze a potential memory leak issue.
     */
    public AiAnalysisResult analyzeMemoryLeak(DiagnosticIssue issue) {
        log.info("LeakDetectorAgent analyzing memory leak for PID {}", issue.getAffectedPid());

        // Collect evidence from MCP tools
        ProcessInfo process = mcpTools.getProcessInfo(issue.getAffectedPid());

        if (process == null) {
            log.warn("Process {} not found - likely terminated. Issue detected at: {}",
                    issue.getAffectedPid(), issue.getDetectedAt());
            return AiAnalysisResult.builder()
                    .confidence(0.0)
                    .rootCause(String.format("Process %s (PID %d) has terminated and cannot be analyzed",
                            issue.getProcessName(), issue.getAffectedPid()))
                    .recommendedAction("NONE")
                    .reasoning(
                            "The process no longer exists in the system. Consider resolving this issue or analyzing it sooner after detection.")
                    .riskAssessment("No action possible - process terminated")
                    .agentName("LeakDetectorAgent")
                    .build();
        }

        JsonNode threads = mcpTools.getThreads(issue.getAffectedPid());
        JsonNode perfCounters = mcpTools.getPerformanceCounters("memory", true);

        // Build evidence map
        Map<String, Object> evidence = new HashMap<>();
        evidence.put("memoryBytes", process.getMemoryBytes());
        evidence.put("threadCount", process.getThreadCount());
        evidence.put("cpuPercent", process.getCpuPercent());
        if (threads != null) {
            evidence.put("threadDetails", threads.toString());
        }

        // Use AI model if available, otherwise rule-based
        if (model != null) {
            return analyzeWithAI(process, threads, perfCounters, issue, evidence);
        } else {
            return analyzeWithRules(process, issue, evidence);
        }
    }

    private AiAnalysisResult analyzeWithAI(ProcessInfo process, JsonNode threads,
            JsonNode perfCounters, DiagnosticIssue issue,
            Map<String, Object> evidence) {
        String prompt = String.format("""
                You are a memory leak detection expert analyzing a Windows process.

                ## Process Information:
                - Name: %s
                - PID: %d
                - Memory Usage: %d MB
                - Thread Count: %d
                - CPU Usage: %.2f%%

                ## Detection Evidence:
                %s

                ## Memory Counters:
                %s

                ## Thread Information:
                %s

                ## Analysis Request:
                1. Identify the most likely root cause of the memory issue
                2. Estimate your confidence level (0.0 to 1.0)
                3. Recommend a specific action (KILL_PROCESS, REDUCE_PRIORITY, TRIM_WORKING_SET, or MONITOR)
                4. Explain your reasoning
                5. List any alternative causes to consider
                6. Assess the risk of remediation

                Respond in this exact format:
                ROOT_CAUSE: <brief description>
                CONFIDENCE: <0.0-1.0>
                RECOMMENDED_ACTION: <action>
                REASONING: <explanation>
                ALTERNATIVES: <comma-separated list>
                RISK: <low/medium/high and explanation>
                """,
                process.getName(),
                process.getPid(),
                process.getMemoryBytes() / (1024 * 1024),
                process.getThreadCount(),
                process.getCpuPercent(),
                issue.getDetails(),
                perfCounters != null ? perfCounters.toString() : "N/A",
                threads != null ? threads.toString() : "N/A");

        try {
            String response = model.generate(prompt);
            return parseAiResponse(response, evidence);
        } catch (Exception e) {
            log.error("AI analysis failed: {}", e.getMessage());
            return analyzeWithRules(process, issue, evidence);
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
                    .agentName("LeakDetectorAgent")
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse AI response: {}", e.getMessage());
            return AiAnalysisResult.builder()
                    .rootCause("Analysis parsing failed")
                    .confidence(0.3)
                    .reasoning(response)
                    .evidence(evidence)
                    .agentName("LeakDetectorAgent")
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

    private AiAnalysisResult analyzeWithRules(ProcessInfo process, DiagnosticIssue issue,
            Map<String, Object> evidence) {
        // Rule-based fallback analysis
        double confidence = issue.getConfidence();
        String rootCause;
        String recommendation;

        long memoryMB = process.getMemoryBytes() / (1024 * 1024);

        if (memoryMB > 2000) {
            rootCause = "Excessive memory consumption detected - likely memory leak";
            recommendation = "TRIM_WORKING_SET";
            confidence = Math.max(confidence, 0.7);
        } else if (memoryMB > 1000) {
            rootCause = "High memory usage - possible memory leak or large working set";
            recommendation = "REDUCE_PRIORITY";
            confidence = Math.max(confidence, 0.5);
        } else {
            rootCause = "Moderate memory usage - monitoring recommended";
            recommendation = "MONITOR";
            confidence = Math.max(confidence, 0.4);
        }

        return AiAnalysisResult.builder()
                .rootCause(rootCause)
                .confidence(confidence)
                .recommendedAction(recommendation)
                .reasoning("Rule-based analysis based on memory thresholds")
                .riskAssessment("Low - non-destructive initial action recommended")
                .evidence(evidence)
                .agentName("LeakDetectorAgent (Rule-Based)")
                .build();
    }
}
