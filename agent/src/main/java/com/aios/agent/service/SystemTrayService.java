package com.aios.agent.service;

import com.aios.agent.config.AgentConfiguration;
import com.aios.agent.detector.DetectorManager;
import com.aios.agent.remediation.RemediationEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * System tray integration service for AIOS Monitor.
 * 
 * <p>
 * Provides a system tray icon with context menu for:
 * <ul>
 * <li>Opening the web dashboard</li>
 * <li>Viewing agent status</li>
 * <li>Pausing/resuming monitoring</li>
 * <li>Triggering manual detection</li>
 * <li>Exiting the application</li>
 * </ul>
 * 
 * <p>
 * The tray icon shows different colors based on system status:
 * <ul>
 * <li>Green: System healthy</li>
 * <li>Yellow: Active issues detected</li>
 * <li>Red: Critical issues detected</li>
 * <li>Gray: Monitoring paused</li>
 * </ul>
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Service
@Slf4j
public class SystemTrayService {

    private final AgentConfiguration config;
    private final DetectorManager detectorManager;
    private final RemediationEngine remediationEngine;

    @Value("${aios.dashboard.url:http://localhost:3000}")
    private String dashboardUrl;

    @Value("${aios.systray.enabled:true}")
    private boolean sysTrayEnabled;

    private TrayIcon trayIcon;
    private SystemTray systemTray;
    private final AtomicBoolean monitoringPaused = new AtomicBoolean(false);

    // Status colors
    private static final Color STATUS_HEALTHY = new Color(76, 175, 80); // Green
    private static final Color STATUS_WARNING = new Color(255, 193, 7); // Amber
    private static final Color STATUS_CRITICAL = new Color(244, 67, 54); // Red
    private static final Color STATUS_PAUSED = new Color(158, 158, 158); // Gray

    public SystemTrayService(AgentConfiguration config,
            DetectorManager detectorManager,
            RemediationEngine remediationEngine) {
        this.config = config;
        this.detectorManager = detectorManager;
        this.remediationEngine = remediationEngine;
    }

    /**
     * Initialize system tray icon on application startup.
     */
    @PostConstruct
    public void initSystemTray() {
        if (!sysTrayEnabled) {
            log.info("System tray icon disabled by configuration");
            return;
        }

        if (!SystemTray.isSupported()) {
            log.warn("System tray not supported on this platform");
            return;
        }

        // Must run on AWT event thread
        EventQueue.invokeLater(this::createTrayIcon);
    }

    /**
     * Create and display the system tray icon.
     */
    private void createTrayIcon() {
        try {
            systemTray = SystemTray.getSystemTray();

            // Create popup menu
            PopupMenu popup = createPopupMenu();

            // Create tray icon with initial status
            Image iconImage = createStatusIcon(STATUS_HEALTHY);
            trayIcon = new TrayIcon(iconImage, "AIOS Monitor - Running", popup);
            trayIcon.setImageAutoSize(true);

            // Double-click opens dashboard
            trayIcon.addActionListener(e -> openDashboard());

            // Add to system tray
            systemTray.add(trayIcon);
            log.info("System tray icon initialized");

        } catch (AWTException e) {
            log.error("Could not add system tray icon", e);
        }
    }

    /**
     * Create the popup context menu.
     */
    private PopupMenu createPopupMenu() {
        PopupMenu popup = new PopupMenu();

        // Status header (non-clickable)
        MenuItem statusItem = new MenuItem("● AIOS Monitor");
        statusItem.setEnabled(false);
        popup.add(statusItem);
        popup.addSeparator();

        // Open Dashboard
        MenuItem openDashboard = new MenuItem("Open Dashboard");
        openDashboard.addActionListener(this::handleOpenDashboard);
        popup.add(openDashboard);

        // View Active Issues
        MenuItem viewIssues = new MenuItem("View Active Issues");
        viewIssues.addActionListener(this::handleViewIssues);
        popup.add(viewIssues);

        popup.addSeparator();

        // Pause/Resume Monitoring
        MenuItem pauseMonitoring = new MenuItem("Pause Monitoring");
        pauseMonitoring.addActionListener(e -> handlePauseResume(pauseMonitoring));
        popup.add(pauseMonitoring);

        // Run Detection Now
        MenuItem runDetection = new MenuItem("Run Detection Now");
        runDetection.addActionListener(this::handleRunDetection);
        popup.add(runDetection);

        popup.addSeparator();

        // Settings/Status submenu
        Menu settingsMenu = new Menu("Settings");

        CheckboxMenuItem dryRunMode = new CheckboxMenuItem("Dry-Run Mode", config.isDryRunMode());
        dryRunMode.addItemListener(e -> handleDryRunToggle(dryRunMode));
        settingsMenu.add(dryRunMode);

        CheckboxMenuItem autoRemediation = new CheckboxMenuItem("Auto-Remediation",
                config.isAutoRemediationEnabled());
        autoRemediation.addItemListener(e -> handleAutoRemediationToggle(autoRemediation));
        settingsMenu.add(autoRemediation);

        popup.add(settingsMenu);

        popup.addSeparator();

        // About
        MenuItem about = new MenuItem("About AIOS Monitor");
        about.addActionListener(this::handleAbout);
        popup.add(about);

        popup.addSeparator();

        // Exit
        MenuItem exit = new MenuItem("Exit");
        exit.addActionListener(e -> handleExit());
        popup.add(exit);

        return popup;
    }

    // ============================================
    // Event Handlers
    // ============================================

    private void handleOpenDashboard(ActionEvent e) {
        openDashboard();
    }

    private void openDashboard() {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(dashboardUrl));
                log.info("Opening dashboard: {}", dashboardUrl);
            }
        } catch (Exception ex) {
            log.error("Failed to open dashboard", ex);
            showNotification("Error", "Could not open dashboard: " + ex.getMessage(),
                    TrayIcon.MessageType.ERROR);
        }
    }

    private void handleViewIssues(ActionEvent e) {
        int activeIssues = detectorManager.getActiveIssues().size();
        StringBuilder message = new StringBuilder();
        message.append("Active Issues: ").append(activeIssues).append("\n");

        detectorManager.getActiveIssues().stream()
                .limit(5)
                .forEach(issue -> message.append(String.format("• %s - %s (PID: %d)\n",
                        issue.getSeverity(), issue.getType(), issue.getAffectedPid())));

        if (activeIssues > 5) {
            message.append("... and ").append(activeIssues - 5).append(" more");
        }

        showNotification("AIOS Monitor - Active Issues",
                message.toString(),
                activeIssues > 0 ? TrayIcon.MessageType.WARNING : TrayIcon.MessageType.INFO);
    }

    private void handlePauseResume(MenuItem menuItem) {
        boolean isPaused = monitoringPaused.get();

        if (isPaused) {
            // Resume monitoring
            monitoringPaused.set(false);
            menuItem.setLabel("Pause Monitoring");
            updateTrayIcon(STATUS_HEALTHY, "AIOS Monitor - Running");
            showNotification("AIOS Monitor", "Monitoring resumed", TrayIcon.MessageType.INFO);
            log.info("Monitoring resumed via system tray");
        } else {
            // Pause monitoring
            monitoringPaused.set(true);
            menuItem.setLabel("Resume Monitoring");
            updateTrayIcon(STATUS_PAUSED, "AIOS Monitor - Paused");
            showNotification("AIOS Monitor", "Monitoring paused", TrayIcon.MessageType.INFO);
            log.info("Monitoring paused via system tray");
        }
    }

    private void handleRunDetection(ActionEvent e) {
        log.info("Manual detection triggered via system tray");
        showNotification("AIOS Monitor", "Running detection scan...", TrayIcon.MessageType.INFO);

        // Run detection in background
        new Thread(() -> {
            try {
                detectorManager.runDetection();
                int issues = detectorManager.getActiveIssues().size();
                showNotification("AIOS Monitor",
                        "Detection complete. " + issues + " active issue(s).",
                        issues > 0 ? TrayIcon.MessageType.WARNING : TrayIcon.MessageType.INFO);
            } catch (Exception ex) {
                log.error("Detection failed", ex);
                showNotification("AIOS Monitor", "Detection failed: " + ex.getMessage(),
                        TrayIcon.MessageType.ERROR);
            }
        }, "manual-detection").start();
    }

    private void handleDryRunToggle(CheckboxMenuItem menuItem) {
        // Note: This is a read-only display in this implementation
        // Actual configuration change would require restart
        log.info("Dry-run mode toggle requested (current: {})", config.isDryRunMode());
        showNotification("AIOS Monitor",
                "Configuration changes require restart.\nCurrent dry-run mode: " + config.isDryRunMode(),
                TrayIcon.MessageType.INFO);
        menuItem.setState(config.isDryRunMode());
    }

    private void handleAutoRemediationToggle(CheckboxMenuItem menuItem) {
        // Note: This is a read-only display in this implementation
        log.info("Auto-remediation toggle requested (current: {})", config.isAutoRemediationEnabled());
        showNotification("AIOS Monitor",
                "Configuration changes require restart.\nCurrent auto-remediation: "
                        + config.isAutoRemediationEnabled(),
                TrayIcon.MessageType.INFO);
        menuItem.setState(config.isAutoRemediationEnabled());
    }

    private void handleAbout(ActionEvent e) {
        String message = """
                AIOS Monitor v1.0.0
                AI-Powered Windows System Monitor

                Features:
                • Real-time system monitoring
                • AI-powered issue detection
                • Automatic remediation
                • Safety-first approach

                © 2026 AIOS Team
                """;
        showNotification("About AIOS Monitor", message, TrayIcon.MessageType.INFO);
    }

    private void handleExit() {
        log.info("Exit requested via system tray");
        showNotification("AIOS Monitor", "Shutting down...", TrayIcon.MessageType.INFO);

        // Give notification time to display
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        // Trigger application shutdown
        System.exit(0);
    }

    // ============================================
    // Status Update Methods
    // ============================================

    /**
     * Update the tray icon based on current system status.
     * 
     * @param hasCriticalIssues true if there are critical issues
     * @param hasWarningIssues  true if there are warning-level issues
     */
    public void updateStatus(boolean hasCriticalIssues, boolean hasWarningIssues) {
        if (trayIcon == null)
            return;

        Color statusColor;
        String tooltip;

        if (monitoringPaused.get()) {
            statusColor = STATUS_PAUSED;
            tooltip = "AIOS Monitor - Paused";
        } else if (hasCriticalIssues) {
            statusColor = STATUS_CRITICAL;
            tooltip = "AIOS Monitor - Critical Issues Detected!";
        } else if (hasWarningIssues) {
            statusColor = STATUS_WARNING;
            tooltip = "AIOS Monitor - Issues Detected";
        } else {
            statusColor = STATUS_HEALTHY;
            tooltip = "AIOS Monitor - Running";
        }

        EventQueue.invokeLater(() -> updateTrayIcon(statusColor, tooltip));
    }

    /**
     * Update the tray icon appearance.
     */
    private void updateTrayIcon(Color statusColor, String tooltip) {
        if (trayIcon != null) {
            trayIcon.setImage(createStatusIcon(statusColor));
            trayIcon.setToolTip(tooltip);
        }
    }

    /**
     * Create a status icon with the given color.
     * 
     * @param color the status color
     * @return the icon image
     */
    private Image createStatusIcon(Color color) {
        int size = 16;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        // Enable anti-aliasing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw filled circle
        g2d.setColor(color);
        g2d.fillOval(1, 1, size - 2, size - 2);

        // Draw border
        g2d.setColor(color.darker());
        g2d.drawOval(1, 1, size - 3, size - 3);

        // Draw "A" letter for AIOS
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 10));
        FontMetrics fm = g2d.getFontMetrics();
        String text = "A";
        int x = (size - fm.stringWidth(text)) / 2;
        int y = (size - fm.getHeight()) / 2 + fm.getAscent();
        g2d.drawString(text, x, y);

        g2d.dispose();
        return image;
    }

    /**
     * Show a tray notification balloon.
     */
    public void showNotification(String title, String message, TrayIcon.MessageType type) {
        if (trayIcon != null) {
            EventQueue.invokeLater(() -> trayIcon.displayMessage(title, message, type));
        }
    }

    /**
     * Check if monitoring is currently paused.
     * 
     * @return true if monitoring is paused
     */
    public boolean isMonitoringPaused() {
        return monitoringPaused.get();
    }

    /**
     * Clean up system tray on application shutdown.
     */
    @EventListener(ContextClosedEvent.class)
    public void onApplicationShutdown() {
        if (systemTray != null && trayIcon != null) {
            systemTray.remove(trayIcon);
            log.info("System tray icon removed");
        }
    }
}
