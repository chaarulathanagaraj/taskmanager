package com.aios.agent.service;

import com.aios.shared.dto.DiagnosticIssue;
import com.aios.shared.enums.ActionStatus;
import com.aios.shared.enums.Severity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.awt.TrayIcon;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Notification service for AIOS Monitor.
 * 
 * <p>
 * Listens for application events and triggers appropriate notifications
 * via both system tray balloons and Windows toast notifications.
 * 
 * <p>
 * Supports notification throttling to prevent notification spam.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final SystemTrayService systemTrayService;
    private final WindowsNotificationJNA windowsNotificationJNA;

    @Value("${aios.notifications.enabled:true}")
    private boolean notificationsEnabled;

    @Value("${aios.notifications.throttle.seconds:60}")
    private int throttleSeconds;

    @Value("${aios.notifications.toast.enabled:true}")
    private boolean toastEnabled;

    // Track last notification time per category to prevent spam
    private final Map<String, Instant> lastNotificationTime = new ConcurrentHashMap<>();

    /**
     * Handle issue detection events.
     * 
     * @param event the issue detection event
     */
    @EventListener
    @Async
    public void onIssueDetected(IssueDetectedEvent event) {
        if (!notificationsEnabled) return;

        DiagnosticIssue issue = event.getIssue();
        String throttleKey = "issue:" + issue.getType() + ":" + issue.getAffectedPid();

        // Check throttling
        if (isThrottled(throttleKey)) {
            log.debug("Notification throttled for: {}", throttleKey);
            return;
        }

        // Determine notification severity
        TrayIcon.MessageType messageType;
        WindowsToastNotification.NotificationType toastType;

        switch (issue.getSeverity()) {
            case CRITICAL:
                messageType = TrayIcon.MessageType.ERROR;
                toastType = WindowsToastNotification.NotificationType.CRITICAL;
                break;
            case HIGH:
                messageType = TrayIcon.MessageType.WARNING;
                toastType = WindowsToastNotification.NotificationType.WARNING;
                break;
            default:
                messageType = TrayIcon.MessageType.INFO;
                toastType = WindowsToastNotification.NotificationType.INFO;
        }

        // Build notification message
        String title = String.format("%s Issue Detected", issue.getSeverity());
        String message = String.format("%s in %s (PID: %d)\nConfidence: %.0f%%",
                issue.getType(),
                issue.getProcessName(),
                issue.getAffectedPid(),
                issue.getConfidence() * 100);

        // Show notifications
        showNotification(title, message, messageType, toastType, issue.getSeverity() == Severity.CRITICAL);

        // Update system tray status
        updateTrayStatus();
    }

    /**
     * Handle remediation action events.
     * 
     * @param event the remediation action event
     */
    @EventListener
    @Async
    public void onRemediationAction(RemediationActionEvent event) {
        if (!notificationsEnabled) return;

        String throttleKey = "action:" + event.getActionType() + ":" + event.getTargetPid();

        if (isThrottled(throttleKey)) {
            return;
        }

        String title;
        TrayIcon.MessageType messageType;
        WindowsToastNotification.NotificationType toastType;

        if (event.getStatus() == ActionStatus.SUCCESS) {
            title = "Remediation Successful";
            messageType = TrayIcon.MessageType.INFO;
            toastType = WindowsToastNotification.NotificationType.SUCCESS;
        } else if (event.getStatus() == ActionStatus.FAILED) {
            title = "Remediation Failed";
            messageType = TrayIcon.MessageType.ERROR;
            toastType = WindowsToastNotification.NotificationType.ERROR;
        } else {
            title = "Remediation Blocked";
            messageType = TrayIcon.MessageType.WARNING;
            toastType = WindowsToastNotification.NotificationType.WARNING;
        }

        String message = String.format("%s on %s (PID: %d)\n%s",
                event.getActionType(),
                event.getTargetProcess(),
                event.getTargetPid(),
                event.getMessage());

        showNotification(title, message, messageType, toastType, false);
    }

    /**
     * Handle policy violation events.
     * 
     * @param event the policy violation event
     */
    @EventListener
    @Async
    public void onPolicyViolation(PolicyViolationEvent event) {
        if (!notificationsEnabled) return;

        String throttleKey = "policy:" + event.getPolicyName();

        if (isThrottled(throttleKey)) {
            return;
        }

        String title = "Action Blocked by Policy";
        String message = String.format("Policy: %s\nReason: %s",
                event.getPolicyName(),
                event.getReason());

        showNotification(title, message,
                TrayIcon.MessageType.WARNING,
                WindowsToastNotification.NotificationType.WARNING,
                false);
    }

    /**
     * Handle service status change events.
     * 
     * @param event the status change event
     */
    @EventListener
    @Async
    public void onServiceStatusChange(ServiceStatusEvent event) {
        if (!notificationsEnabled) return;

        String title = "AIOS Monitor";
        String message = event.getMessage();

        TrayIcon.MessageType messageType;
        WindowsToastNotification.NotificationType toastType;

        switch (event.getType()) {
            case STARTED:
                messageType = TrayIcon.MessageType.INFO;
                toastType = WindowsToastNotification.NotificationType.SUCCESS;
                break;
            case STOPPED:
                messageType = TrayIcon.MessageType.INFO;
                toastType = WindowsToastNotification.NotificationType.INFO;
                break;
            case ERROR:
                messageType = TrayIcon.MessageType.ERROR;
                toastType = WindowsToastNotification.NotificationType.ERROR;
                break;
            default:
                messageType = TrayIcon.MessageType.INFO;
                toastType = WindowsToastNotification.NotificationType.INFO;
        }

        showNotification(title, message, messageType, toastType, false);
    }

    // ============================================
    // Helper Methods
    // ============================================

    /**
     * Show notification via both system tray and toast.
     */
    private void showNotification(String title, String message,
                                   TrayIcon.MessageType trayType,
                                   WindowsToastNotification.NotificationType toastType,
                                   boolean isCritical) {
        // System tray balloon
        systemTrayService.showNotification(title, message, trayType);

        // Windows toast notifications
        if (toastEnabled) {
            // Primary: JNA-based notifications (faster, more reliable)
            WindowsNotificationJNA.NotificationLevel jnaLevel = mapToJnaLevel(toastType);
            if (isCritical) {
                windowsNotificationJNA.showCritical(title, message);
            } else {
                windowsNotificationJNA.showNotification(title, message, jnaLevel);
            }
        }

        log.debug("Notification shown: {} - {}", title, message);
    }

    /**
     * Map toast notification type to JNA notification level.
     */
    private WindowsNotificationJNA.NotificationLevel mapToJnaLevel(
            WindowsToastNotification.NotificationType toastType) {
        return switch (toastType) {
            case ERROR, CRITICAL -> WindowsNotificationJNA.NotificationLevel.ERROR;
            case WARNING -> WindowsNotificationJNA.NotificationLevel.WARNING;
            default -> WindowsNotificationJNA.NotificationLevel.INFO;
        };
    }

    /**
     * Check if notification for this category is throttled.
     */
    private boolean isThrottled(String key) {
        Instant lastTime = lastNotificationTime.get(key);
        Instant now = Instant.now();

        if (lastTime == null || lastTime.plus(throttleSeconds, ChronoUnit.SECONDS).isBefore(now)) {
            lastNotificationTime.put(key, now);
            return false;
        }

        return true;
    }

    /**
     * Update system tray icon status based on active issues.
     */
    private void updateTrayStatus() {
        // This would check current issues and update tray icon color
        // Implementation depends on DetectorManager access
    }

    // ============================================
    // Event Classes
    // ============================================

    /**
     * Event for issue detection.
     */
    public static class IssueDetectedEvent {
        private final DiagnosticIssue issue;

        public IssueDetectedEvent(DiagnosticIssue issue) {
            this.issue = issue;
        }

        public DiagnosticIssue getIssue() {
            return issue;
        }
    }

    /**
     * Event for remediation actions.
     */
    public static class RemediationActionEvent {
        private final String actionType;
        private final String targetProcess;
        private final int targetPid;
        private final ActionStatus status;
        private final String message;

        public RemediationActionEvent(String actionType, String targetProcess,
                                       int targetPid, ActionStatus status, String message) {
            this.actionType = actionType;
            this.targetProcess = targetProcess;
            this.targetPid = targetPid;
            this.status = status;
            this.message = message;
        }

        public String getActionType() { return actionType; }
        public String getTargetProcess() { return targetProcess; }
        public int getTargetPid() { return targetPid; }
        public ActionStatus getStatus() { return status; }
        public String getMessage() { return message; }
    }

    /**
     * Event for policy violations.
     */
    public static class PolicyViolationEvent {
        private final String policyName;
        private final String reason;

        public PolicyViolationEvent(String policyName, String reason) {
            this.policyName = policyName;
            this.reason = reason;
        }

        public String getPolicyName() { return policyName; }
        public String getReason() { return reason; }
    }

    /**
     * Event for service status changes.
     */
    public static class ServiceStatusEvent {
        public enum Type { STARTED, STOPPED, PAUSED, RESUMED, ERROR }

        private final Type type;
        private final String message;

        public ServiceStatusEvent(Type type, String message) {
            this.type = type;
            this.message = message;
        }

        public Type getType() { return type; }
        public String getMessage() { return message; }
    }
}
