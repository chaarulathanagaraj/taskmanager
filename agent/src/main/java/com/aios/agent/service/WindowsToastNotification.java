package com.aios.agent.service;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Windows toast notification service using PowerShell.
 * 
 * <p>
 * Displays native Windows 10/11 toast notifications for important events.
 * Uses the Windows.UI.Notifications API via PowerShell scripts.
 * 
 * <p>
 * Toast notifications are shown for:
 * <ul>
 * <li>Critical issue detection</li>
 * <li>Remediation action execution</li>
 * <li>Service status changes</li>
 * <li>Policy violations</li>
 * </ul>
 * 
 * <p>
 * Notifications support:
 * <ul>
 * <li>Title and message text</li>
 * <li>App icon</li>
 * <li>Action buttons (optional)</li>
 * <li>Duration (short/long)</li>
 * </ul>
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Slf4j
public class WindowsToastNotification {

    private static final String APP_ID = "AIOS.Monitor";
    private static final int NOTIFICATION_TIMEOUT_SECONDS = 5;

    /**
     * Show a simple toast notification with title and message.
     * 
     * @param title   notification title
     * @param message notification body text
     */
    public static void showNotification(String title, String message) {
        showNotification(title, message, NotificationType.INFO);
    }

    /**
     * Show a toast notification with title, message, and type.
     * 
     * @param title   notification title
     * @param message notification body text
     * @param type    notification type (determines icon)
     */
    public static void showNotification(String title, String message, NotificationType type) {
        if (!isWindows()) {
            log.debug("Toast notifications only supported on Windows");
            return;
        }

        // Run notification asynchronously to avoid blocking
        CompletableFuture.runAsync(() -> {
            try {
                showToastViaPoweShell(title, message, type);
            } catch (Exception e) {
                log.warn("Failed to show toast notification: {}", e.getMessage());
            }
        });
    }

    /**
     * Show toast notification using PowerShell and Windows Runtime APIs.
     */
    private static void showToastViaPoweShell(String title, String message, NotificationType type)
            throws IOException, InterruptedException {

        // Escape special characters for PowerShell
        String escapedTitle = escapeForPowerShell(title);
        String escapedMessage = escapeForPowerShell(message);

        // Get icon based on notification type
        String icon = type.getIcon();

        // PowerShell script for Windows toast notification
        String script = String.format(
                """
                        [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
                        [Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom.XmlDocument, ContentType = WindowsRuntime] | Out-Null

                        $template = @"
                        <toast duration="short">
                            <visual>
                                <binding template="ToastGeneric">
                                    <text>%s %s</text>
                                    <text>%s</text>
                                </binding>
                            </visual>
                            <audio src="ms-winsoundevent:Notification.Default"/>
                        </toast>
                        "@

                        $xml = New-Object Windows.Data.Xml.Dom.XmlDocument
                        $xml.LoadXml($template)

                        $toast = New-Object Windows.UI.Notifications.ToastNotification $xml
                        $notifier = [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('%s')
                        $notifier.Show($toast)
                        """,
                icon, escapedTitle, escapedMessage, APP_ID);

        // Execute PowerShell script
        ProcessBuilder pb = new ProcessBuilder(
                "powershell",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy", "Bypass",
                "-Command", script);

        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Wait for completion with timeout
        boolean completed = process.waitFor(NOTIFICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!completed) {
            process.destroyForcibly();
            log.warn("Toast notification timed out");
        } else if (process.exitValue() != 0) {
            // Read error output
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                StringBuilder output = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                log.debug("PowerShell output: {}", output);
            }
        }
    }

    /**
     * Show a toast notification with action buttons.
     * 
     * @param title       notification title
     * @param message     notification body text
     * @param actionLabel button label
     * @param actionUrl   URL to open when button is clicked
     */
    public static void showNotificationWithAction(String title, String message,
            String actionLabel, String actionUrl) {
        if (!isWindows()) {
            log.debug("Toast notifications only supported on Windows");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                showToastWithActionViaPoweShell(title, message, actionLabel, actionUrl);
            } catch (Exception e) {
                log.warn("Failed to show toast notification with action: {}", e.getMessage());
            }
        });
    }

    /**
     * Show toast with clickable action button.
     */
    private static void showToastWithActionViaPoweShell(String title, String message,
            String actionLabel, String actionUrl)
            throws IOException, InterruptedException {

        String escapedTitle = escapeForPowerShell(title);
        String escapedMessage = escapeForPowerShell(message);
        String escapedLabel = escapeForPowerShell(actionLabel);
        String escapedUrl = escapeForPowerShell(actionUrl);

        String script = String.format(
                """
                        [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
                        [Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom.XmlDocument, ContentType = WindowsRuntime] | Out-Null

                        $template = @"
                        <toast duration="short" activationType="protocol">
                            <visual>
                                <binding template="ToastGeneric">
                                    <text>%s</text>
                                    <text>%s</text>
                                </binding>
                            </visual>
                            <actions>
                                <action content="%s" arguments="%s" activationType="protocol"/>
                            </actions>
                            <audio src="ms-winsoundevent:Notification.Default"/>
                        </toast>
                        "@

                        $xml = New-Object Windows.Data.Xml.Dom.XmlDocument
                        $xml.LoadXml($template)

                        $toast = New-Object Windows.UI.Notifications.ToastNotification $xml
                        $notifier = [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('%s')
                        $notifier.Show($toast)
                        """,
                escapedTitle, escapedMessage, escapedLabel, escapedUrl, APP_ID);

        ProcessBuilder pb = new ProcessBuilder(
                "powershell",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy", "Bypass",
                "-Command", script);

        pb.redirectErrorStream(true);
        Process process = pb.start();
        process.waitFor(NOTIFICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Show a critical/urgent toast notification (stays longer).
     */
    public static void showCriticalNotification(String title, String message) {
        if (!isWindows()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String script = String.format(
                        """
                                [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
                                [Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom.XmlDocument, ContentType = WindowsRuntime] | Out-Null

                                $template = @"
                                <toast duration="long" scenario="urgent">
                                    <visual>
                                        <binding template="ToastGeneric">
                                            <text>🚨 %s</text>
                                            <text>%s</text>
                                        </binding>
                                    </visual>
                                    <audio src="ms-winsoundevent:Notification.Looping.Alarm" loop="false"/>
                                </toast>
                                "@

                                $xml = New-Object Windows.Data.Xml.Dom.XmlDocument
                                $xml.LoadXml($template)

                                $toast = New-Object Windows.UI.Notifications.ToastNotification $xml
                                $notifier = [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('%s')
                                $notifier.Show($toast)
                                """,
                        escapeForPowerShell(title), escapeForPowerShell(message), APP_ID);

                ProcessBuilder pb = new ProcessBuilder(
                        "powershell",
                        "-NoProfile",
                        "-NonInteractive",
                        "-ExecutionPolicy", "Bypass",
                        "-Command", script);

                pb.start().waitFor(NOTIFICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Failed to show critical notification: {}", e.getMessage());
            }
        });
    }

    /**
     * Check if running on Windows.
     */
    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("windows");
    }

    /**
     * Escape special characters for PowerShell string.
     */
    private static String escapeForPowerShell(String input) {
        if (input == null)
            return "";
        return input
                .replace("'", "''")
                .replace("`", "``")
                .replace("\"", "`\"")
                .replace("$", "`$")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * Notification type enum with icons.
     */
    public enum NotificationType {
        INFO("ℹ️"),
        SUCCESS("✅"),
        WARNING("⚠️"),
        ERROR("❌"),
        CRITICAL("🚨");

        private final String icon;

        NotificationType(String icon) {
            this.icon = icon;
        }

        public String getIcon() {
            return icon;
        }
    }
}
