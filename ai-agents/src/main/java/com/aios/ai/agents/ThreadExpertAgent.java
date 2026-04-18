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
 * AI Agent specialized in thread behavior analysis.
 * Detects thread explosions, deadlocks, and thread pool exhaustion.
 */
@Service
@Slf4j
public class ThreadExpertAgent {

    private final ChatLanguageModel model;
    private final McpToolService mcpTools;

    public ThreadExpertAgent(
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
            log.warn("Gemini API key not configured - ThreadExpertAgent will use rule-based analysis");
        }
    }

    /**
     * Analyze thread-related issues like explosions, deadlocks, pool exhaustion.
     */
    public AiAnalysisResult analyzeThreadBehavior(DiagnosticIssue issue) {
        log.info("ThreadExpertAgent analyzing thread behavior for PID {}", issue.getAffectedPid());

        // Collect evidence from MCP tools
        ProcessInfo process = mcpTools.getProcessInfo(issue.getAffectedPid());
        JsonNode threads = mcpTools.getThreads(issue.getAffectedPid());
        JsonNode perfCounters = mcpTools.getPerformanceCounters("cpu", true);

        if (process == null) {
            return AiAnalysisResult.builder()
                    .confidence(0.0)
                    .rootCause("Process not found")
                    .agentName("ThreadExpertAgent")
                    .build();
        }

        // Build evidence map
        Map<String, Object> evidence = new HashMap<>();
        evidence.put("threadCount", process.getThreadCount());
        evidence.put("cpuPercent", process.getCpuPercent());
        evidence.put("memoryBytes", process.getMemoryBytes());
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
                You are a thread behavior expert analyzing a Windows process for potential issues.

                ## Process Information:
                - Name: %s
                - PID: %d
                - Thread Count: %d
                - CPU Usage: %.2f%%
                - Memory: %d MB

                ## Detection Evidence:
                %s

                ## Thread Details:
                %s

                ## CPU Counters:
                %s

                ## Analysis Request:
                Analyze for:
                1. Thread explosion (rapid uncontrolled thread creation)
                2. Deadlock patterns (threads waiting on each other)
                3. Thread pool exhaustion (all threads blocked)
                4. Spin-waiting or busy-loop threads

                Provide:
                - ROOT_CAUSE: <specific issue identified>
                - CONFIDENCE: <0.0-1.0>
                - RECOMMENDED_ACTION: <KILL_PROCESS, SUSPEND_PROCESS, REDUCE_PRIORITY, or MONITOR>
                - REASONING: <detailed explanation>
                - ALTERNATIVES: <other possible causes>
                - RISK: <risk assessment of remediation>
                """,
                process.getName(),
                process.getPid(),
                process.getThreadCount(),
                process.getCpuPercent(),
                process.getMemoryBytes() / (1024 * 1024),
                issue.getDetails(),
                threads != null ? threads.toString() : "N/A",
                perfCounters != null ? perfCounters.toString() : "N/A");

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
                    .agentName("ThreadExpertAgent")
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse AI response: {}", e.getMessage());
            return AiAnalysisResult.builder()
                    .rootCause("Analysis parsing failed")
                    .confidence(0.3)
                    .reasoning(response)
                    .evidence(evidence)
                    .agentName("ThreadExpertAgent")
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

        int threadCount = process.getThreadCount();
        double cpuPercent = process.getCpuPercent();

        if (threadCount > 500) {
            rootCause = "Thread explosion detected - excessive thread creation";
            recommendation = "KILL_PROCESS";
            confidence = Math.max(confidence, 0.8);
        } else if (threadCount > 200 && cpuPercent < 5) {
            rootCause = "Potential deadlock - many threads with low CPU usage";
            recommendation = "SUSPEND_PROCESS";
            confidence = Math.max(confidence, 0.6);
        } else if (threadCount > 100) {
            rootCause = "High thread count - possible thread pool exhaustion";
            recommendation = "REDUCE_PRIORITY";
            confidence = Math.max(confidence, 0.5);
        } else {
            rootCause = "Thread count within normal range";
            recommendation = "MONITOR";
            confidence = Math.max(confidence, 0.4);
        }

        return AiAnalysisResult.builder()
                .rootCause(rootCause)
                .confidence(confidence)
                .recommendedAction(recommendation)
                .reasoning("Rule-based analysis based on thread count and CPU usage patterns")
                .riskAssessment("Medium - thread issues may cause system instability")
                .evidence(evidence)
                .agentName("ThreadExpertAgent (Rule-Based)")
                .build();
    }
}
