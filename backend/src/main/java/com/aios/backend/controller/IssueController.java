package com.aios.backend.controller;

import com.aios.backend.dto.IssueResolutionSummary;
import com.aios.backend.model.IssueEntity;
import com.aios.backend.service.IssueService;
import com.aios.shared.dto.DiagnosticIssue;
import com.aios.shared.enums.IssueType;
import com.aios.shared.enums.Severity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for diagnostic issues endpoints.
 * 
 * <p>
 * Provides APIs for:
 * <ul>
 * <li>Receiving detected issues from the agent</li>
 * <li>Querying active and historical issues</li>
 * <li>Resolving issues manually</li>
 * <li>Filtering issues by type, severity, or process</li>
 * </ul>
 * 
 * @author AIOS Team
 * @since 1.0
 */
@RestController
@RequestMapping("/api/issues")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class IssueController {

    private final IssueService issueService;

    /**
     * Create a new issue from agent detection.
     * 
     * @param issue the diagnostic issue to save
     * @return saved issue entity with generated ID
     */
    @PostMapping
    public ResponseEntity<IssueEntity> createIssue(@RequestBody DiagnosticIssue issue) {
        log.info("Received new issue: type={}, severity={}, pid={}, process={}",
                issue.getType(), issue.getSeverity(), issue.getAffectedPid(), issue.getProcessName());
        IssueEntity saved = issueService.createIssue(issue);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Get all issues (active and resolved).
     * 
     * @return list of all issues
     */
    @GetMapping
    public ResponseEntity<List<IssueEntity>> getAllIssues() {
        log.debug("Fetching all issues");
        List<IssueEntity> issues = issueService.getAllIssues();
        return ResponseEntity.ok(issues);
    }

    /**
     * Get all active (unresolved) issues.
     * Primary endpoint for dashboard display.
     * 
     * @return list of active issues ordered by severity and detection time
     */
    @GetMapping("/active")
    public ResponseEntity<List<IssueEntity>> getActiveIssues() {
        log.debug("Fetching active issues");
        List<IssueEntity> issues = issueService.getActiveIssues();
        return ResponseEntity.ok(issues);
    }

    /**
     * Get high priority active issues (HIGH/CRITICAL with confidence > 0.7).
     * 
     * @return list of high priority issues requiring immediate attention
     */
    @GetMapping("/high-priority")
    public ResponseEntity<List<IssueEntity>> getHighPriorityIssues() {
        log.debug("Fetching high priority issues");
        List<IssueEntity> issues = issueService.getHighPriorityIssues();
        return ResponseEntity.ok(issues);
    }

    /**
     * Get issue by ID.
     * 
     * @param id the issue ID
     * @return issue entity or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<IssueEntity> getIssueById(@PathVariable Long id) {
        log.debug("Fetching issue with id={}", id);
        return issueService.getIssueById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get active issues by type.
     * 
     * @param type the issue type to filter by
     * @return list of active issues of the specified type
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<List<IssueEntity>> getIssuesByType(@PathVariable IssueType type) {
        log.debug("Fetching active issues of type={}", type);
        List<IssueEntity> issues = issueService.getIssuesByType(type);
        return ResponseEntity.ok(issues);
    }

    /**
     * Get active issues by severity.
     * 
     * @param severity the severity level to filter by
     * @return list of active issues with the specified severity
     */
    @GetMapping("/severity/{severity}")
    public ResponseEntity<List<IssueEntity>> getIssuesBySeverity(@PathVariable Severity severity) {
        log.debug("Fetching active issues with severity={}", severity);
        List<IssueEntity> issues = issueService.getIssuesBySeverity(severity);
        return ResponseEntity.ok(issues);
    }

    /**
     * Get active issues for a specific process.
     * 
     * @param pid process ID
     * @return list of active issues for the process
     */
    @GetMapping("/process/{pid}")
    public ResponseEntity<List<IssueEntity>> getIssuesByProcess(@PathVariable Integer pid) {
        log.debug("Fetching active issues for process pid={}", pid);
        List<IssueEntity> issues = issueService.getIssuesByProcess(pid);
        return ResponseEntity.ok(issues);
    }

    /**
     * Get issues eligible for auto-remediation.
     * 
     * @param minConfidence minimum confidence threshold (default: 0.85)
     * @return list of issues eligible for auto-remediation
     */
    @GetMapping("/eligible-for-remediation")
    public ResponseEntity<List<IssueEntity>> getEligibleForRemediation(
            @RequestParam(defaultValue = "0.85") Double minConfidence) {
        log.debug("Fetching issues eligible for auto-remediation (confidence >= {})", minConfidence);
        List<IssueEntity> issues = issueService.getEligibleForRemediation(minConfidence);
        return ResponseEntity.ok(issues);
    }

    /**
     * Mark an issue as resolved.
     * 
     * @param id the issue ID to resolve
     * @return resolution summary or 404 if not found
     */
    @PutMapping("/{id}/resolve")
    public ResponseEntity<IssueResolutionSummary> resolveIssue(@PathVariable Long id) {
        log.info("Resolving issue with id={}", id);
        IssueResolutionSummary resolved = issueService.resolveIssue(id);
        return resolved != null
                ? ResponseEntity.ok(resolved)
                : ResponseEntity.notFound().build();
    }

    /**
     * Mark an issue as ignored.
     *
     * @param id issue ID to ignore
     * @return 200 OK if ignored, 404 if not found
     */
    @PutMapping("/{id}/ignore")
    public ResponseEntity<Void> ignoreIssue(@PathVariable Long id) {
        log.info("Ignoring issue with id={}", id);
        boolean ignored = issueService.ignoreIssue(id);
        return ignored
                ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }

    /**
     * Get count of active issues.
     * 
     * @return count of unresolved issues
     */
    @GetMapping("/count/active")
    public ResponseEntity<Long> getActiveIssueCount() {
        log.debug("Fetching active issue count");
        long count = issueService.getActiveIssueCount();
        return ResponseEntity.ok(count);
    }

    /**
     * Get count of active issues by severity.
     * 
     * @param severity the severity level
     * @return count of active issues with this severity
     */
    @GetMapping("/count/severity/{severity}")
    public ResponseEntity<Long> getIssueCountBySeverity(@PathVariable Severity severity) {
        log.debug("Fetching issue count for severity={}", severity);
        long count = issueService.getIssueCountBySeverity(severity);
        return ResponseEntity.ok(count);
    }

    /**
     * Delete resolved issues older than specified days.
     * Cleanup endpoint for maintenance.
     * 
     * @param olderThanDays delete resolved issues older than this many days
     * @return number of deleted records
     */
    @DeleteMapping("/cleanup")
    public ResponseEntity<Integer> cleanupResolvedIssues(
            @RequestParam(defaultValue = "30") int olderThanDays) {
        log.info("Cleaning up resolved issues older than {} days", olderThanDays);
        int deleted = issueService.deleteOldResolvedIssues(olderThanDays);
        return ResponseEntity.ok(deleted);
    }

    /**
     * Clean up duplicate issues keeping only one per type/PID combination.
     * Useful when duplicates have accumulated due to rapid repeated detections.
     * 
     * @param daysToCheck check for duplicates within last N days (default: 7)
     * @return number of duplicate issues deleted
     */
    @DeleteMapping("/cleanup-duplicates")
    public ResponseEntity<Integer> cleanupDuplicates(
            @RequestParam(defaultValue = "7") int daysToCheck) {
        log.info("Cleaning up duplicate issues from last {} days", daysToCheck);
        int deleted = issueService.cleanupDuplicateIssues(daysToCheck);
        return ResponseEntity.ok(deleted);
    }
}
