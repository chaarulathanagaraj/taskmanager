package com.aios.backend.repository;

import com.aios.backend.model.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for chat message entities.
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

    /**
     * Find all messages in a session, ordered by creation time.
     */
    List<ChatMessageEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    /**
     * Find recent messages in a session (for context window).
     */
    List<ChatMessageEntity> findTop20BySessionIdOrderByCreatedAtDesc(String sessionId);

    /**
     * Find messages by user.
     */
    List<ChatMessageEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Find messages related to an issue.
     */
    List<ChatMessageEntity> findByIssueIdOrderByCreatedAtAsc(Long issueId);

    /**
     * Find messages created after a specific time.
     */
    List<ChatMessageEntity> findByCreatedAtAfter(Instant after);

    /**
     * Count messages in a session.
     */
    long countBySessionId(String sessionId);

    /**
     * Get all unique session IDs.
     */
    @Query("SELECT DISTINCT c.sessionId FROM ChatMessageEntity c ORDER BY c.createdAt DESC")
    List<String> findDistinctSessionIds();

    /**
     * Delete old messages from a session (optional cleanup).
     */
    void deleteBySessionIdAndCreatedAtBefore(String sessionId, Instant before);
}
