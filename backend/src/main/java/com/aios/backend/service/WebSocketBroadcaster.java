package com.aios.backend.service;

import com.aios.ai.dto.CompleteDiagnosisReport;
import com.aios.backend.dto.IssueResolutionSummary;
import com.aios.backend.model.ActionEntity;
import com.aios.backend.model.IssueEntity;
import com.aios.shared.dto.MetricSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for broadcasting real-time updates via WebSocket.
 * 
 * <p>
 * Provides methods to push updates to connected frontend clients
 * through various topic channels.
 * 
 * <p>
 * Available channels:
 * <ul>
 * <li>/topic/metrics - System metrics updates</li>
 * <li>/topic/issues - New issues and updates</li>
 * <li>/topic/actions - Action execution results</li>
 * <li>/topic/alerts - Critical system alerts</li>
 * </ul>
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Broadcast a metric snapshot to all connected clients.
     * 
     * @param metric the metric snapshot to broadcast
     */
    public void broadcastMetric(MetricSnapshot metric) {
        try {
            messagingTemplate.convertAndSend("/topic/metrics", metric);
            log.debug("Broadcasted metric snapshot to /topic/metrics");
        } catch (Exception e) {
            log.error("Failed to broadcast metric", e);
        }
    }

    /**
     * Broadcast a new issue to all connected clients.
     * 
     * @param issue the diagnostic issue to broadcast
     */
    public void broadcastNewIssue(IssueEntity issue) {
        try {
            messagingTemplate.convertAndSend("/topic/issues", issue);
            log.info("Broadcasted new issue: type={}, severity={}, pid={}",
                    issue.getType(), issue.getSeverity(), issue.getAffectedPid());
        } catch (Exception e) {
            log.error("Failed to broadcast issue", e);
        }
    }

    /**
     * Broadcast an issue resolution to all connected clients.
     * 
     * @param summary the resolved issue summary
     */
    public void broadcastIssueResolved(IssueResolutionSummary summary) {
        try {
            messagingTemplate.convertAndSend("/topic/issues/resolved", summary);
            log.info("Broadcasted resolved issue: id={}, source={}, message={}",
                    summary.getIssueId(), summary.getSource(), summary.getMessage());
        } catch (Exception e) {
            log.error("Failed to broadcast issue resolution", e);
        }
    }

    /**
     * Broadcast an issue resolution using a raw issue entity.
     * 
     * @param issue the resolved issue
     */
    public void broadcastIssueResolved(IssueEntity issue) {
        broadcastIssueResolved(IssueResolutionSummary.builder()
                .issueId(issue.getId())
                .processName(issue.getProcessName())
                .affectedPid(issue.getAffectedPid())
                .issueType(issue.getType())
                .severity(issue.getSeverity())
                .status(issue.getStatus())
                .resolved(issue.getResolved())
                .remediationTaken(issue.getRemediationTaken())
                .source("MANUAL")
                .message("Issue resolved")
                .resolvedAt(issue.getResolvedAt())
                .actionsTaken(List.of(
                        "Marked the issue as resolved",
                        "Updated lifecycle status",
                        "Broadcast the resolution event"))
                .build());
    }

    /**
     * Broadcast an action execution result to all connected clients.
     * 
     * @param action the action entity to broadcast
     */
    public void broadcastAction(ActionEntity action) {
        try {
            messagingTemplate.convertAndSend("/topic/actions", action);
            log.info("Broadcasted action: type={}, status={}, pid={}",
                    action.getActionType(), action.getStatus(), action.getTargetPid());
        } catch (Exception e) {
            log.error("Failed to broadcast action", e);
        }
    }

    /**
     * Broadcast a critical alert to all connected clients.
     * 
     * @param alertMessage the alert message
     * @param severity     the alert severity
     */
    public void broadcastAlert(String alertMessage, String severity) {
        try {
            AlertMessage alert = new AlertMessage(alertMessage, severity, System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/alerts", alert);
            log.warn("Broadcasted alert: severity={}, message={}", severity, alertMessage);
        } catch (Exception e) {
            log.error("Failed to broadcast alert", e);
        }
    }

    /**
     * Broadcast a system health status update.
     * 
     * @param healthStatus the health status (HEALTHY, WARNING, CRITICAL)
     */
    public void broadcastHealthStatus(String healthStatus) {
        try {
            messagingTemplate.convertAndSend("/topic/health", healthStatus);
            log.debug("Broadcasted health status: {}", healthStatus);
        } catch (Exception e) {
            log.error("Failed to broadcast health status", e);
        }
    }

    /**
     * Broadcast an AI diagnosis completion to all connected clients.
     *
     * @param issueId the issue that was diagnosed
     * @param report  the diagnosis report
     */
    public void broadcastDiagnosisComplete(Long issueId, CompleteDiagnosisReport report) {
        try {
            DiagnosisMessage message = new DiagnosisMessage(
                    issueId,
                    report.isSuccess(),
                    report.getConfidence(),
                    report.getProcessingTimeMs(),
                    System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/diagnosis", message);
            log.info("Broadcasted diagnosis complete: issueId={}, success={}, confidence={}",
                    issueId, report.isSuccess(), report.getConfidence());
        } catch (Exception e) {
            log.error("Failed to broadcast diagnosis", e);
        }
    }

    /**
     * Broadcast settings change to all connected clients.
     *
     * @param settings the updated settings
     */
    public void broadcastSettingsChange(Object settings) {
        try {
            messagingTemplate.convertAndSend("/topic/settings", settings);
            log.info("Broadcasted settings change");
        } catch (Exception e) {
            log.error("Failed to broadcast settings change", e);
        }
    }

    /**
     * Simple alert message DTO for WebSocket transmission.
     */
    public record AlertMessage(String message, String severity, long timestamp) {
    }

    /**
     * Diagnosis completion message DTO for WebSocket transmission.
     */
    public record DiagnosisMessage(Long issueId, boolean success, double confidence,
            long processingTimeMs, long timestamp) {
    }

    /**
     * Broadcast a general system message to all connected clients.
     * 
     * @param message the system message
     */
    public void broadcastSystemMessage(String message) {
        try {
            SystemMessage msg = new SystemMessage(message, System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/system", msg);
            log.debug("Broadcasted system message: {}", message);
        } catch (Exception e) {
            log.error("Failed to broadcast system message", e);
        }
    }

    /**
     * Broadcast an execution update to all connected clients.
     * 
     * @param executionId the execution ID
     * @param status      the execution status
     * @param message     the update message
     */
    public void broadcastExecutionUpdate(Long executionId, String status, String message) {
        broadcastExecutionUpdate(executionId, status, message, List.of(), null, null, null);
    }

    /**
     * Broadcast an execution update with step progress.
     *
     * @param executionId         the execution ID
     * @param status              the execution status
     * @param message             the update message
     * @param steps               planned or completed steps for the remediation
     * @param stepIndex           current step index, if available
     * @param totalSteps          total number of steps, if available
     * @param verificationMessage optional verification summary
     */
    public void broadcastExecutionUpdate(Long executionId, String status, String message,
            List<String> steps, Integer stepIndex, Integer totalSteps, String verificationMessage) {
        try {
            ExecutionMessage msg = new ExecutionMessage(executionId, status, message, steps, stepIndex,
                    totalSteps, verificationMessage, System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/executions/" + executionId, msg);
            messagingTemplate.convertAndSend("/topic/executions", msg);
            log.info("Broadcasted execution update: executionId={}, status={}", executionId, status);
        } catch (Exception e) {
            log.error("Failed to broadcast execution update", e);
        }
    }

    /**
     * Broadcast a new approval request to all connected clients.
     * 
     * @param approvalId the approval request ID
     * @param issueId    the related issue ID
     * @param actionType the action type requiring approval
     */
    public void broadcastApprovalRequest(Long approvalId, Long issueId, String actionType) {
        try {
            ApprovalMessage msg = new ApprovalMessage(approvalId, issueId, actionType, System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/approvals", msg);
            log.info("Broadcasted approval request: approvalId={}, issueId={}, actionType={}",
                    approvalId, issueId, actionType);
        } catch (Exception e) {
            log.error("Failed to broadcast approval request", e);
        }
    }

    /**
     * System message DTO for WebSocket transmission.
     */
    public record SystemMessage(String message, long timestamp) {
    }

    /**
     * Execution message DTO for WebSocket transmission.
     */
    public record ExecutionMessage(Long executionId, String status, String message, List<String> steps,
            Integer stepIndex, Integer totalSteps, String verificationMessage, long timestamp) {
    }

    /**
     * Approval message DTO for WebSocket transmission.
     */
    public record ApprovalMessage(Long approvalId, Long issueId, String actionType, long timestamp) {
    }
}
