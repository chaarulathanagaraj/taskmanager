package com.aios.backend.service;

import com.aios.backend.model.ApprovalRequestEntity;
import com.aios.backend.model.RuleExecutionEntity;
import com.aios.backend.repository.ApprovalRequestRepository;
import com.aios.backend.repository.RuleExecutionRepository;
import com.aios.shared.dto.ExecutionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Service for managing approval workflow for rule executions.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ApprovalWorkflowService {

    private final ApprovalRequestRepository approvalRepository;
    private final RuleExecutionRepository executionRepository;
    private final RuleEngineService ruleEngine;
    private final WebSocketBroadcaster broadcaster;

    /**
     * Get all pending approval requests.
     */
    public List<ApprovalRequestEntity> getPendingApprovals() {
        return approvalRepository.findByStatusOrderByRequestedAtDesc(ExecutionStatus.PENDING);
    }

    /**
     * Get approval request by ID.
     */
    public ApprovalRequestEntity getApprovalRequest(Long id) {
        return approvalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Approval request not found: " + id));
    }

    /**
     * Approve an execution request.
     */
    @Transactional
    public ApprovalRequestEntity approve(Long approvalId, String approvedBy, String comment) {
        log.info("Approving request {} by {}", approvalId, approvedBy);

        ApprovalRequestEntity approval = getApprovalRequest(approvalId);

        if (approval.getStatus() != ExecutionStatus.PENDING) {
            throw new IllegalStateException("Approval request is not pending: " + approval.getStatus());
        }

        approval.setStatus(ExecutionStatus.APPROVED);
        approval.setApprovedBy(approvedBy);
        approval.setApprovedAt(Instant.now());
        if (comment != null) {
            approval.setComment(approval.getComment() + "\n[Approval] " + comment);
        }

        approvalRepository.save(approval);

        // Update execution record
        RuleExecutionEntity execution = executionRepository.findById(approval.getExecutionId())
                .orElseThrow(() -> new IllegalStateException("Execution not found: " + approval.getExecutionId()));

        execution.setStatus(ExecutionStatus.APPROVED);
        execution.setApprovedBy(approvedBy);
        executionRepository.save(execution);

        // Broadcast approval event
        broadcaster.broadcastSystemMessage("Execution approved for issue " + approval.getIssueId());

        // Execute the action
        try {
            ruleEngine.executeAction(execution);
            log.info("Execution {} completed after approval", execution.getId());
        } catch (Exception e) {
            log.error("Failed to execute approved action: {}", e.getMessage(), e);
        }

        return approval;
    }

    /**
     * Reject an execution request.
     */
    @Transactional
    public ApprovalRequestEntity reject(Long approvalId, String rejectedBy, String reason) {
        log.info("Rejecting request {} by {}: {}", approvalId, rejectedBy, reason);

        ApprovalRequestEntity approval = getApprovalRequest(approvalId);

        if (approval.getStatus() != ExecutionStatus.PENDING) {
            throw new IllegalStateException("Approval request is not pending: " + approval.getStatus());
        }

        approval.setStatus(ExecutionStatus.CANCELLED);
        approval.setRejectedBy(rejectedBy);
        approval.setRejectedAt(Instant.now());
        approval.setRejectionReason(reason);

        approvalRepository.save(approval);

        // Update execution record
        RuleExecutionEntity execution = executionRepository.findById(approval.getExecutionId())
                .orElseThrow(() -> new IllegalStateException("Execution not found: " + approval.getExecutionId()));

        execution.setStatus(ExecutionStatus.CANCELLED);
        execution.setMessage("Rejected: " + reason);
        executionRepository.save(execution);

        // Broadcast rejection event
        broadcaster.broadcastSystemMessage("Execution rejected for issue " + approval.getIssueId());

        return approval;
    }

    /**
     * Get approval requests for a specific issue.
     */
    public List<ApprovalRequestEntity> getApprovalsByIssue(Long issueId) {
        return approvalRepository.findByIssueId(issueId);
    }

    /**
     * Cancel a pending approval request.
     */
    @Transactional
    public void cancelApproval(Long approvalId) {
        ApprovalRequestEntity approval = getApprovalRequest(approvalId);

        if (approval.getStatus() != ExecutionStatus.PENDING) {
            throw new IllegalStateException("Can only cancel pending approvals");
        }

        approval.setStatus(ExecutionStatus.CANCELLED);
        approvalRepository.save(approval);

        // Update execution record
        RuleExecutionEntity execution = executionRepository.findById(approval.getExecutionId())
                .orElse(null);

        if (execution != null) {
            execution.setStatus(ExecutionStatus.CANCELLED);
            executionRepository.save(execution);
        }

        log.info("Approval request {} cancelled", approvalId);
    }
}
