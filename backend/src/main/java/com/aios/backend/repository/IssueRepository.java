package com.aios.backend.repository;

import com.aios.backend.model.IssueEntity;
import com.aios.shared.enums.IssueType;
import com.aios.shared.enums.IssueStatus;
import com.aios.shared.enums.Severity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for IssueEntity database operations.
 * 
 * <p>
 * Provides CRUD operations and custom queries for diagnostic issues.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Repository
public interface IssueRepository extends JpaRepository<IssueEntity, Long> {

       /**
        * Find all unresolved (active) issues.
        * This is the primary query for displaying current system problems.
        * 
        * @return list of active issues ordered by severity (descending) and detected
        *         time
        */
       List<IssueEntity> findByResolvedFalseOrderBySeverityDescDetectedAtDesc();

       /**
        * Find all issues detected after a specific timestamp.
        * Useful for showing recent issues or syncing with frontend.
        * 
        * @param timestamp cutoff timestamp
        * @return list of issues detected after the timestamp
        */
       List<IssueEntity> findByDetectedAtAfterOrderByDetectedAtDesc(Instant timestamp);

       /**
        * Find active issues by type.
        * 
        * @param type the issue type to filter by
        * @return list of active issues of the specified type
        */
       List<IssueEntity> findByTypeAndResolvedFalseOrderByDetectedAtDesc(IssueType type);

       /**
        * Find active issues by type and affected PID.
        * Useful for collapsing duplicate active rows for same underlying issue.
        *
        * @param type issue type
        * @param pid  affected process ID
        * @return active issues for same key, latest first
        */
       List<IssueEntity> findByTypeAndAffectedPidAndResolvedFalseOrderByDetectedAtDesc(IssueType type, Integer pid);

       /**
        * Find active issues by severity.
        * 
        * @param severity the severity level to filter by
        * @return list of active issues with the specified severity
        */
       List<IssueEntity> findBySeverityAndResolvedFalseOrderByDetectedAtDesc(Severity severity);

       /**
        * Find active issues for a specific process.
        * 
        * @param pid process ID
        * @return list of active issues for the process
        */
       List<IssueEntity> findByAffectedPidAndResolvedFalseOrderByDetectedAtDesc(Integer pid);

       /**
        * Find active issues above a confidence threshold.
        * 
        * @param minConfidence minimum confidence level (0.0 - 1.0)
        * @return list of high-confidence active issues
        */
       List<IssueEntity> findByResolvedFalseAndConfidenceGreaterThanEqualOrderByConfidenceDesc(Double minConfidence);

       /**
        * Find all issues within a time range.
        * 
        * @param start start timestamp
        * @param end   end timestamp
        * @return list of issues in the time range
        */
       List<IssueEntity> findByDetectedAtBetweenOrderByDetectedAtDesc(Instant start, Instant end);

       /**
        * Count active issues.
        * 
        * @return count of unresolved issues
        */
       long countByResolvedFalse();

       /**
        * Count active issues by severity.
        * 
        * @param severity the severity level
        * @return count of active issues with this severity
        */
       long countBySeverityAndResolvedFalse(Severity severity);

       /**
        * Count active issues by type.
        * 
        * @param type the issue type
        * @return count of active issues of this type
        */
       long countByTypeAndResolvedFalse(IssueType type);

       /**
        * Find high priority active issues (HIGH or CRITICAL severity with confidence >
        * 0.7).
        * 
        * @return list of high priority issues
        */
       @Query("SELECT i FROM IssueEntity i WHERE i.resolved = false AND " +
                     "(i.severity = 'HIGH' OR i.severity = 'CRITICAL') AND i.confidence > 0.7 " +
                     "ORDER BY i.severity DESC, i.confidence DESC, i.detectedAt DESC")
       List<IssueEntity> findHighPriorityActiveIssues();

       /**
        * Find eligible issues for auto-remediation.
        * 
        * @param minConfidence minimum confidence threshold
        * @return list of issues eligible for auto-remediation
        */
       @Query("SELECT i FROM IssueEntity i WHERE i.resolved = false AND i.confidence >= :minConfidence " +
                     "ORDER BY i.severity DESC, i.confidence DESC")
       List<IssueEntity> findEligibleForAutoRemediation(@Param("minConfidence") Double minConfidence);

       /**
        * Delete resolved issues older than a specific timestamp.
        * Used for periodic cleanup.
        * 
        * @param timestamp cutoff timestamp
        * @return number of deleted records
        */
       long deleteByResolvedTrueAndResolvedAtBefore(Instant timestamp);

       /**
        * Count all resolved issues.
        * 
        * @return count of resolved issues
        */
       long countByResolvedTrue();

       /**
        * Count issues resolved after a specific timestamp.
        * 
        * @param timestamp cutoff timestamp
        * @return count of issues resolved after timestamp
        */
       long countByResolvedTrueAndResolvedAtAfter(Instant timestamp);

       /**
        * Count issues detected after a specific timestamp.
        * 
        * @param timestamp cutoff timestamp
        * @return count of issues detected after timestamp
        */
       long countByDetectedAtAfter(Instant timestamp);

       /**
        * Find unresolved issues.
        * 
        * @return list of active issues
        */
       List<IssueEntity> findByResolvedFalse();

       /**
        * Find recent active issues with same type and PID to detect duplicates.
        * Searches within a time window to avoid creating duplicate issues.
        * 
        * @param type  issue type
        * @param pid   affected process ID
        * @param since timestamp to search from
        * @return list of matching issues
        */
       @Query("SELECT i FROM IssueEntity i WHERE i.type = :type AND i.affectedPid = :pid " +
                     "AND i.resolved = false AND i.detectedAt >= :since ORDER BY i.detectedAt DESC")
       List<IssueEntity> findRecentSimilarIssues(@Param("type") IssueType type,
                     @Param("pid") Integer pid,
                     @Param("since") Instant since);

       /**
        * Find the latest unresolved issue with same type + PID.
        * Used for upsert behavior to prevent duplicate active issues.
        *
        * @param type issue type
        * @param pid  affected process ID
        * @return most recent active issue if present
        */
       Optional<IssueEntity> findFirstByTypeAndAffectedPidAndResolvedFalseOrderByDetectedAtDesc(
                     IssueType type,
                     Integer pid);

       /**
        * Find latest active issue for a fingerprint key.
        */
       Optional<IssueEntity> findFirstByIssueKeyAndResolvedFalseOrderByDetectedAtDesc(String issueKey);

       /**
        * Find all unresolved issues by lifecycle status.
        */
       List<IssueEntity> findByStatusInAndResolvedFalseOrderBySeverityDescDetectedAtDesc(List<IssueStatus> statuses);

       /**
        * Find all duplicate issues to clean up.
        * Groups by type and PID, keeping only the most recent issue per group.
        * 
        * @param since minimum age to consider for deduplication
        * @return list of IDs to delete
        */
       @Query("SELECT i.id FROM IssueEntity i WHERE i.resolved = false " +
                     "AND i.id NOT IN " +
                     "(SELECT MAX(i2.id) FROM IssueEntity i2 " +
                     "WHERE i2.resolved = false " +
                     "GROUP BY i2.type, i2.affectedPid, i2.processName)")
       List<Long> findDuplicateIssueIds(@Param("since") Instant since);
}
