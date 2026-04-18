package com.aios.backend.controller;

import com.aios.backend.model.ChatMessageEntity;
import com.aios.backend.service.ChatbotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for chatbot interactions.
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Chatbot", description = "AI-powered chat interface for system diagnostics")
public class ChatController {

    private final ChatbotService chatbotService;

    /**
     * Send a message to the chatbot.
     */
    @PostMapping("/message")
    @Operation(summary = "Send chat message", description = "Send a message to the AI chatbot and get a response")
    public ResponseEntity<ChatMessageEntity> sendMessage(@RequestBody ChatRequest request) {
        log.info("Received chat message from user {} in session {}",
                request.getUserId(), request.getSessionId());

        try {
            ChatMessageEntity response = chatbotService.processMessage(
                    request.getSessionId(),
                    request.getUserId(),
                    request.getMessage(),
                    request.getIssueId());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to process chat message", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get chat history for a session.
     */
    @GetMapping("/history/{sessionId}")
    @Operation(summary = "Get chat history", description = "Retrieve all messages in a chat session")
    public ResponseEntity<List<ChatMessageEntity>> getChatHistory(@PathVariable String sessionId) {
        log.debug("Fetching chat history for session {}", sessionId);

        try {
            List<ChatMessageEntity> history = chatbotService.getChatHistory(sessionId);
            return ResponseEntity.ok(history);

        } catch (Exception e) {
            log.error("Failed to fetch chat history", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get all active chat sessions.
     */
    @GetMapping("/sessions")
    @Operation(summary = "Get active sessions", description = "Get list of all active chat sessions")
    public ResponseEntity<List<String>> getActiveSessions() {
        log.debug("Fetching active chat sessions");

        try {
            List<String> sessions = chatbotService.getActiveSessions();
            return ResponseEntity.ok(sessions);

        } catch (Exception e) {
            log.error("Failed to fetch active sessions", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get chat messages related to a specific issue.
     */
    @GetMapping("/issue/{issueId}")
    @Operation(summary = "Get issue chat history", description = "Get all chat messages related to a specific issue")
    public ResponseEntity<List<ChatMessageEntity>> getIssueChats(@PathVariable Long issueId) {
        log.debug("Fetching chat messages for issue {}", issueId);

        try {
            List<ChatMessageEntity> messages = chatbotService.getIssueChats(issueId);
            return ResponseEntity.ok(messages);

        } catch (Exception e) {
            log.error("Failed to fetch issue chats", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Request DTO for sending chat messages.
     */
    @Data
    public static class ChatRequest {
        private String sessionId;
        private String userId;
        private String message;
        private Long issueId;
    }
}
