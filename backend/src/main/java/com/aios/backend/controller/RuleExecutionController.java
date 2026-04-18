package com.aios.backend.controller;

import com.aios.backend.dto.BulkAutomationResult;
import com.aios.backend.model.ApprovalRequestEntity;
import com.aios.backend.service.ApprovalWorkflowService;
import com.aios.backend.service.RuleEngineService;
import com.aios.shared.dto.RuleExecutionRequest;
import com.aios.shared.dto.RuleExecutionResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for rule execution and approval workflow.
 */
@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Rule Execution", description = "Automated rule execution and approval workflow")
public class RuleExecutionController {

    private final RuleEngineService ruleEngine;
    private final ApprovalWorkflowService approvalWorkflow;

    /**
     * Request execution of a remediation rule.
     */
    @PostMapping("/execute")
    @Operation(summary = "Request rule execution", description = "Request execution of a remediation rule. CRITICAL actions will require approval.")
    public ResponseEntity<RuleExecutionResult> requestExecution(@RequestBody RuleExecutionRequest request) {
        log.info("Received execution request for issue {} with action {}",
                request.getIssueId(), request.getActionType());

        try {
            RuleExecutionResult result = ruleEngine.requestExecution(request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.error("Invalid execution request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Failed to process execution request", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get execution history for an issue.
     */
    @GetMapping("/executions/{issueId}")
    @Operation(summary = "Get execution history", description = "Get execution history for a specific issue")
    public ResponseEntity<List<RuleExecutionResult>> getExecutionHistory(@PathVariable Long issueId) {
        log.debug("Fetching execution history for issue {}", issueId);

        try {
            List<RuleExecutionResult> history = ruleEngine.getExecutionHistory(issueId);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            log.error("Failed to fetch execution history", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get recent executions.
     */
    @GetMapping("/executions")
    @Operation(summary = "Get recent executions", description = "Get the 100 most recent rule executions")
    public ResponseEntity<List<RuleExecutionResult>> getRecentExecutions() {
        log.debug("Fetching recent executions");

        try {
            List<RuleExecutionResult> executions = ruleEngine.getRecentExecutions();
            return ResponseEntity.ok(executions);
        } catch (Exception e) {
            log.error("Failed to fetch recent executions", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get pending approval requests.
     */
    @GetMapping("/approvals/pending")
    @Operation(summary = "Get pending approvals", description = "Get all pending approval requests")
    public ResponseEntity<List<ApprovalRequestEntity>> getPendingApprovals() {
        log.debug("Fetching pending approvals");

        try {
            List<ApprovalRequestEntity> approvals = approvalWorkflow.getPendingApprovals();
            return ResponseEntity.ok(approvals);
        } catch (Exception e) {
            log.error("Failed to fetch pending approvals", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Approve an execution request.
     */
    @PostMapping("/approvals/{approvalId}/approve")
    @Operation(summary = "Approve execution", description = "Approve a pending execution request")
    public ResponseEntity<ApprovalRequestEntity> approveExecution(
            @PathVariable Long approvalId,
            @RequestBody Map<String, String> body) {

        String approvedBy = body.getOrDefault("approvedBy", "admin");
        String comment = body.get("comment");

        log.info("Approving execution {} by {}", approvalId, approvedBy);

        try {
            ApprovalRequestEntity approval = approvalWorkflow.approve(approvalId, approvedBy, comment);
            return ResponseEntity.ok(approval);
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Invalid approval request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Failed to approve execution", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Reject an execution request.
     */
    @PostMapping("/approvals/{approvalId}/reject")
    @Operation(summary = "Reject execution", description = "Reject a pending execution request")
    public ResponseEntity<ApprovalRequestEntity> rejectExecution(
            @PathVariable Long approvalId,
            @RequestBody Map<String, String> body) {

        String rejectedBy = body.getOrDefault("rejectedBy", "admin");
        String reason = body.getOrDefault("reason", "No reason provided");

        log.info("Rejecting execution {} by {}: {}", approvalId, rejectedBy, reason);

        try {
            ApprovalRequestEntity approval = approvalWorkflow.reject(approvalId, rejectedBy, reason);
            return ResponseEntity.ok(approval);
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Invalid rejection request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Failed to reject execution", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get approval requests for an issue.
     */
    @GetMapping("/approvals/issue/{issueId}")
    @Operation(summary = "Get issue approvals", description = "Get all approval requests for a specific issue")
    public ResponseEntity<List<ApprovalRequestEntity>> getIssueApprovals(@PathVariable Long issueId) {
        log.debug("Fetching approvals for issue {}", issueId);

        try {
            List<ApprovalRequestEntity> approvals = approvalWorkflow.getApprovalsByIssue(issueId);
            return ResponseEntity.ok(approvals);
        } catch (Exception e) {
            log.error("Failed to fetch issue approvals", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Cancel a pending approval request.
     */
    @PostMapping("/approvals/{approvalId}/cancel")
    @Operation(summary = "Cancel approval request", description = "Cancel a pending approval request")
    public ResponseEntity<Void> cancelApproval(@PathVariable Long approvalId) {
        log.info("Cancelling approval request {}", approvalId);

        try {
            approvalWorkflow.cancelApproval(approvalId);
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            log.error("Invalid cancel request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Failed to cancel approval", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Automate all active issues except protected processes.
     */
    @PostMapping("/automate-all")
    @Operation(summary = "Automate all safe issues", description = "Runs automation for all active issues, skipping protected processes for manual handling")
    public ResponseEntity<BulkAutomationResult> automateAllSafeIssues() {
        log.info("Running bulk automation for all active safe issues");
        try {
            BulkAutomationResult result = ruleEngine.automateAllSafeActiveIssues();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to run bulk automation", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
