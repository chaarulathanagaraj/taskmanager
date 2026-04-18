package com.aios.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity for storing chat messages.
 */
@Entity
@Table(name = "chat_messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Chat session ID to group related messages.
     */
    @Column(nullable = false)
    private String sessionId;

    /**
     * User ID or name who sent the message.
     */
    @Column(nullable = false)
    private String userId;

    /**
     * Message role: USER, ASSISTANT, SYSTEM.
     */
    @Column(nullable = false)
    private String role;

    /**
     * Message content.
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * Related issue ID (optional - for context).
     */
    @Column
    private Long issueId;

    /**
     * AI model used for response generation (optional).
     */
    @Column
    private String model;

    /**
     * Response time in milliseconds (for ASSISTANT messages).
     */
    @Column
    private Long responseTimeMs;

    /**
     * Number of tokens used (if available from AI provider).
     */
    @Column
    private Integer tokensUsed;

    /**
     * Timestamp when message was created.
     */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Set createdAt before persisting.
     */
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
