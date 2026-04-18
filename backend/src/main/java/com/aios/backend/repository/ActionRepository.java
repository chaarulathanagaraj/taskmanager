package com.aios.backend.repository;

import com.aios.backend.model.ActionEntity;
import com.aios.shared.enums.ActionStatus;
import com.aios.shared.enums.ActionType;
import com.aios.shared.enums.SafetyLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository interface for ActionEntity database operations.
 * 
 * <p>
 * Provides CRUD operations and custom queries for remediation action records.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Repository
public interface ActionRepository extends JpaRepository<ActionEntity, Long> {

    /**
     * Find all actions executed after a specific timestamp.
     * Primary query for displaying recent action history.
     * 
     * @param timestamp cutoff timestamp
     * @return list of actions executed after the timestamp, most recent first
     */
    List<ActionEntity> findByExecutedAtAfterOrderByExecutedAtDesc(Instant timestamp);

    /**
     * Find actions within a time range.
     * 
     * @param start start timestamp
     * @param end   end timestamp
     * @return list of actions in the time range
     */
    List<ActionEntity> findByExecutedAtBetweenOrderByExecutedAtDesc(Instant start, Instant end);

    /**
     * Find actions by type.
     * 
     * @param actionType the action type to filter by
     * @return list of actions of the specified type
     */
    List<ActionEntity> findByActionTypeOrderByExecutedAtDesc(ActionType actionType);

    /**
     * Find actions by status.
     * 
     * @param status the action status to filter by
     * @return list of actions with the specified status
     */
    List<ActionEntity> findByStatusOrderByExecutedAtDesc(ActionStatus status);

    /**
     * Find actions for a specific process.
     * 
     * @param pid process ID
     * @return list of actions targeting the process
     */
    List<ActionEntity> findByTargetPidOrderByExecutedAtDesc(Integer pid);

    /**
     * Find non-dry-run (real) actions.
     * 
     * @return list of actual executed actions
     */
    List<ActionEntity> findByDryRunFalseOrderByExecutedAtDesc();

    /**
     * Find failed actions that require manual review.
     * 
     * @return list of failed high-risk actions
     */
    @Query("SELECT a FROM ActionEntity a WHERE a.status = 'FAILED' AND " +
            "(a.safetyLevel = 'HIGH' OR a.safetyLevel = 'CRITICAL') " +
            "ORDER BY a.executedAt DESC")
    List<ActionEntity> findActionsRequiringManualReview();

    /**
     * Find recent successful actions by type.
     * 
     * @param actionType the action type
     * @param since      timestamp to search from
     * @return list of successful actions of this type
     */
    List<ActionEntity> findByActionTypeAndStatusAndExecutedAtAfter(
            ActionType actionType,
            ActionStatus status,
            Instant since);

    /**
     * Count actions by status.
     * 
     * @param status the action status
     * @return count of actions with this status
     */
    long countByStatus(ActionStatus status);

    /**
     * Count actions by type.
     * 
     * @param actionType the action type
     * @return count of actions of this type
     */
    long countByActionType(ActionType actionType);

    /**
     * Count non-dry-run actions within a time range.
     * 
     * @param start start timestamp
     * @param end   end timestamp
     * @return count of real actions in the time range
     */
    long countByDryRunFalseAndExecutedAtBetween(Instant start, Instant end);

    /**
     * Get success rate for a specific action type over a time period.
     * 
     * @param actionType the action type
     * @param since      start timestamp
     * @return success rate as percentage (0-100)
     */
    @Query("SELECT (CAST(COUNT(CASE WHEN a.status = 'SUCCESS' THEN 1 END) AS double) / COUNT(*) * 100) " +
            "FROM ActionEntity a WHERE a.actionType = :actionType AND a.dryRun = false AND a.executedAt >= :since")
    Double getSuccessRateByActionType(
            @Param("actionType") ActionType actionType,
            @Param("since") Instant since);

    /**
     * Get the most recent N actions.
     * 
     * @param limit maximum number of records
     * @return list of most recent actions
     */
    @Query("SELECT a FROM ActionEntity a ORDER BY a.executedAt DESC LIMIT :limit")
    List<ActionEntity> findTopNByOrderByExecutedAtDesc(@Param("limit") int limit);

    /**
     * Find actions related to a specific issue.
     * 
     * @param issueId the issue ID
     * @return list of actions for the issue
     */
    @Query("SELECT a FROM ActionEntity a WHERE a.issue.id = :issueId ORDER BY a.executedAt DESC")
    List<ActionEntity> findByIssueId(@Param("issueId") Long issueId);

    /**
     * Delete old dry-run actions for cleanup.
     * 
     * @param timestamp cutoff timestamp
     * @return number of deleted records
     */
    long deleteByDryRunTrueAndExecutedAtBefore(Instant timestamp);

    /**
     * Count actions by status executed after a specific timestamp.
     * 
     * @param status    the action status
     * @param timestamp cutoff timestamp
     * @return count of matching actions
     */
    long countByStatusAndExecutedAtAfter(ActionStatus status, Instant timestamp);

    /**
     * Count actions executed after a specific timestamp.
     * 
     * @param timestamp cutoff timestamp
     * @return count of actions after timestamp
     */
    long countByExecutedAtAfter(Instant timestamp);
}
