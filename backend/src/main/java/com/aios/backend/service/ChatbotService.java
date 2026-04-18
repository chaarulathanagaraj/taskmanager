package com.aios.backend.service;

import com.aios.ai.agents.ChatbotAiAgent;
import com.aios.backend.model.ChatMessageEntity;
import com.aios.backend.model.IssueEntity;
import com.aios.backend.repository.ChatMessageRepository;
import com.aios.backend.repository.IssueRepository;
import com.aios.shared.dto.RuleExecutionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for chatbot interactions with AI assistance.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatbotService {

    private final ChatMessageRepository chatRepository;
    private final IssueRepository issueRepository;
    private final RuleEngineService ruleEngine;
    private final WebSocketBroadcaster broadcaster;
    private final ChatbotAiAgent chatbotAiAgent;

    /**
     * Process a user message and generate AI response.
     */
    @Transactional
    public ChatMessageEntity processMessage(String sessionId, String userId, String message, Long issueId) {
        log.info("Processing chat message from user {} in session {}", userId, sessionId);

        // Ensure session ID exists
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        // Save user message
        ChatMessageEntity userMessage = ChatMessageEntity.builder()
                .sessionId(sessionId)
                .userId(userId)
                .role("USER")
                .content(message)
                .issueId(issueId)
                .build();

        chatRepository.save(userMessage);

        // Get conversation history for context
        List<ChatMessageEntity> history = chatRepository
                .findTop20BySessionIdOrderByCreatedAtDesc(sessionId);

        // Generate AI response
        Instant startTime = Instant.now();
        String responseContent = generateResponse(message, issueId, history);
        long responseTime = Instant.now().toEpochMilli() - startTime.toEpochMilli();

        // Save assistant message
        ChatMessageEntity assistantMessage = ChatMessageEntity.builder()
                .sessionId(sessionId)
                .userId("system")
                .role("ASSISTANT")
                .content(responseContent)
                .issueId(issueId)
                .model("rule-based")
                .responseTimeMs(responseTime)
                .build();

        chatRepository.save(assistantMessage);

        // Broadcast new message to WebSocket clients
        broadcaster.broadcastSystemMessage("New chat message in session " + sessionId);

        return assistantMessage;
    }

    /**
     * Get chat history for a session.
     */
    public List<ChatMessageEntity> getChatHistory(String sessionId) {
        return chatRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    /**
     * Get all active sessions.
     */
    public List<String> getActiveSessions() {
        return chatRepository.findDistinctSessionIds();
    }

    /**
     * Get chat messages related to an issue.
     */
    public List<ChatMessageEntity> getIssueChats(Long issueId) {
        return chatRepository.findByIssueIdOrderByCreatedAtAsc(issueId);
    }

    /**
     * Generate AI response using LangChain4j and Gemini.
     */
    private String generateResponse(String userMessage, Long issueId, List<ChatMessageEntity> history) {
        String context = "No specific issue context provided.";
        if (issueId != null) {
            context = "Focusing on diagnostic issue ID: " + issueId;
            var issue = issueRepository.findById(issueId);
            if (issue.isPresent()) {
                context += " which is about " + issue.get().getType() + " on process " + issue.get().getProcessName();
            }
        }
        
        return chatbotAiAgent.generateResponse(userMessage, context);
    }

    private String buildHelpMessage() {
        return """
                **AIOS Chatbot Help**

                I can assist you with:

                **Issue Management:**
                - 'list issues' - Show recent diagnostic issues
                - 'show issue <id>' - Get details about a specific issue

                **System Status:**
                - 'status' - Get current system health status
                - 'metrics' - Show key performance metrics

                **Remediation:**
                - 'fix issue <id>' - Execute remediation for an issue
                - 'preview fix <id>' - Preview remediation before executing

                **Approval Workflow:**
                - 'pending approvals' - Show approval requests waiting for review
                - 'approve <id>' - Approve a pending execution

                You can also ask natural language questions about your system!
                """;
    }

    private String handleIssueQuery(String message, Long issueId) {
        IssueEntity issue = issueRepository.findById(issueId).orElse(null);
        if (issue == null) {
            return "Issue #" + issueId + " not found.";
        }

        // Handle fix/execute requests
        if (message.contains("fix") || message.contains("execute") || message.contains("resolve")) {
            return handleFixRequest(issue);
        }

        // Handle preview requests
        if (message.contains("preview") || message.contains("suggest") || message.contains("recommend")) {
            return handlePreviewRequest(issue);
        }

        // Default: provide issue details
        return formatIssueDetails(issue);
    }

    private String handleFixRequest(IssueEntity issue) {
        // Determine appropriate action based on issue type
        String actionType = determineActionType(issue);

        // Create execution request
        RuleExecutionRequest request = new RuleExecutionRequest();
        request.setIssueId(issue.getId());
        request.setActionType(actionType);
        request.setDryRun(false);
        request.setComment("Requested via chatbot");

        try {
            var result = ruleEngine.requestExecution(request);

            if (result.getStatus().toString().equals("PENDING")) {
                return String.format(
                        "✓ Remediation request created for issue #%d\n" +
                                "Action: %s\n" +
                                "Status: Waiting for approval (CRITICAL action)\n" +
                                "You can monitor progress in the Execution History panel.",
                        issue.getId(), actionType);
            } else if (result.getStatus().toString().equals("EXECUTING")) {
                return String.format(
                        "✓ Remediation started for issue #%d\n" +
                                "Action: %s\n" +
                                "Execution ID: %d\n" +
                                "Status updates will appear in the Execution History panel.",
                        issue.getId(), actionType, result.getExecutionId());
            } else {
                return String.format(
                        "✓ Remediation completed for issue #%d\n" +
                                "Action: %s\n" +
                                "Result: %s",
                        issue.getId(), actionType, result.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to execute remediation: {}", e.getMessage(), e);
            return "Failed to execute remediation: " + e.getMessage();
        }
    }

    private String handlePreviewRequest(IssueEntity issue) {
        String actionType = determineActionType(issue);

        return String.format("""
                **Remediation Preview for Issue #%d**

                **Issue Details:**
                - Type: %s
                - Severity: %s
                - Process: %s (PID %d)
                - Description: %s

                **Recommended Action:** %s

                **What will happen:**
                %s

                **Safety Check:** %s

                To execute this action, say: "fix issue %d"
                """,
                issue.getId(),
                issue.getType(),
                issue.getSeverity(),
                issue.getProcessName(),
                issue.getAffectedPid(),
                issue.getDetails(),
                actionType,
                getActionDescription(actionType),
                getSafetyNote(issue, actionType),
                issue.getId());
    }

    private String formatIssueDetails(IssueEntity issue) {
        return String.format("""
                **Issue #%d**

                - **Type:** %s
                - **Severity:** %s
                - **Process:** %s (PID %d)
                - **Description:** %s
                - **Detected:** %s
                - **Status:** %s

                How can I help with this issue?
                - Say 'preview fix' to see recommended actions
                - Say 'fix this' to execute remediation
                """,
                issue.getId(),
                issue.getType(),
                issue.getSeverity(),
                issue.getProcessName(),
                issue.getAffectedPid(),
                issue.getDetails(),
                issue.getDetectedAt(),
                issue.getResolved() ? "RESOLVED" : "ACTIVE");
    }

    private String listRecentIssues() {
        List<IssueEntity> issues = issueRepository.findByResolvedFalseOrderBySeverityDescDetectedAtDesc()
                .stream()
                .limit(10)
                .toList();

        if (issues.isEmpty()) {
            return "No recent issues found. Your system is running smoothly! ✓";
        }

        StringBuilder response = new StringBuilder("**Recent Issues:**\n\n");
        for (IssueEntity issue : issues) {
            response.append(String.format("#%d - %s | %s | %s (PID %d) | %s\n",
                    issue.getId(),
                    issue.getSeverity(),
                    issue.getType(),
                    issue.getProcessName(),
                    issue.getAffectedPid(),
                    issue.getResolved() ? "RESOLVED" : "ACTIVE"));
        }

        response.append("\nClick on an issue to analyze it, or say 'show issue <id>' for details.");
        return response.toString();
    }

    private String getSystemStatus() {
        long totalIssues = issueRepository.count();
        long activeIssues = issueRepository.findByResolvedFalse().size();
        long resolvedIssues = totalIssues - activeIssues;

        return String.format("""
                **System Status**

                🔴 Active Issues: %d
                ✅ Resolved Issues: %d
                📊 Total Issues: %d

                Overall Health: %s
                """,
                activeIssues,
                resolvedIssues,
                totalIssues,
                activeIssues == 0 ? "HEALTHY ✓" : activeIssues < 5 ? "WARNING ⚠️" : "CRITICAL ⚠️");
    }

    private String determineActionType(IssueEntity issue) {
        return switch (issue.getType()) {
            case MEMORY_LEAK -> "KILL_PROCESS";
            case THREAD_EXPLOSION -> "RESTART_PROCESS";
            case HUNG_PROCESS -> "KILL_PROCESS";
            case RESOURCE_HOG -> "REDUCE_PRIORITY";
            case IO_BOTTLENECK -> "MONITOR";
            default -> "MONITOR";
        };
    }

    private String getActionDescription(String actionType) {
        return switch (actionType) {
            case "KILL_PROCESS" ->
                "The process will be terminated immediately. This may result in data loss if the process has unsaved work.";
            case "REDUCE_PRIORITY" -> "The process priority will be lowered, giving other processes more CPU time.";
            case "TRIM_WORKING_SET" ->
                "The process's working set will be trimmed, freeing up memory while keeping the process running.";
            case "RESTART_PROCESS" -> "The process will be stopped and restarted, which may help resolve the issue.";
            default -> "The issue will be monitored without taking action.";
        };
    }

    private String getSafetyNote(IssueEntity issue, String actionType) {
        if ("CRITICAL".equals(issue.getSeverity())) {
            return "⚠️ CRITICAL severity - this action will require approval before execution.";
        }
        if ("KILL_PROCESS".equals(actionType)) {
            return "⚠️ Process termination may affect running applications.";
        }
        return "✓ This action should be safe to execute.";
    }
}
