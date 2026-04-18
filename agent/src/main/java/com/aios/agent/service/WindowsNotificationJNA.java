package com.aios.agent.service;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinUser.*;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JNA-based Windows toast/balloon notification service.
 * 
 * <p>
 * Uses native Windows Shell_NotifyIcon API for displaying notifications.
 * Supports balloon tips on Windows 7+ and integrates with Action Center on Windows 10+.
 * 
 * <p>
 * This provides a pure JNA implementation without PowerShell dependencies,
 * offering better performance and reliability for high-frequency notifications.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Slf4j
@Component
public class WindowsNotificationJNA {

    private static final int NIF_MESSAGE = 0x00000001;
    private static final int NIF_ICON = 0x00000002;
    private static final int NIF_TIP = 0x00000004;
    private static final int NIF_INFO = 0x00000010;
    private static final int NIF_GUID = 0x00000020;
    private static final int NIF_SHOWTIP = 0x00000080;

    private static final int NIM_ADD = 0x00000000;
    private static final int NIM_MODIFY = 0x00000001;
    private static final int NIM_DELETE = 0x00000002;
    private static final int NIM_SETVERSION = 0x00000004;

    private static final int NOTIFYICON_VERSION_4 = 4;

    // Balloon icon types
    private static final int NIIF_NONE = 0x00000000;
    private static final int NIIF_INFO = 0x00000001;
    private static final int NIIF_WARNING = 0x00000002;
    private static final int NIIF_ERROR = 0x00000003;
    private static final int NIIF_USER = 0x00000004;
    private static final int NIIF_NOSOUND = 0x00000010;
    private static final int NIIF_LARGE_ICON = 0x00000020;
    private static final int NIIF_RESPECT_QUIET_TIME = 0x00000080;

    private static final int WM_USER = 0x0400;
    private static final int WM_TRAYICON = WM_USER + 1;

    private HWND messageWindow;
    private NOTIFYICONDATA notifyIconData;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Extended Shell32 interface with Shell_NotifyIconW.
     */
    public interface Shell32Ex extends StdCallLibrary {
        Shell32Ex INSTANCE = Native.load("shell32", Shell32Ex.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean Shell_NotifyIconW(int dwMessage, NOTIFYICONDATA lpData);
    }

    /**
     * NOTIFYICONDATA structure for Shell_NotifyIcon.
     */
    @Structure.FieldOrder({"cbSize", "hWnd", "uID", "uFlags", "uCallbackMessage", "hIcon",
            "szTip", "dwState", "dwStateMask", "szInfo", "uTimeoutOrVersion",
            "szInfoTitle", "dwInfoFlags", "guidItem", "hBalloonIcon"})
    public static class NOTIFYICONDATA extends Structure {
        public int cbSize;
        public HWND hWnd;
        public int uID;
        public int uFlags;
        public int uCallbackMessage;
        public HICON hIcon;
        public char[] szTip = new char[128];
        public int dwState;
        public int dwStateMask;
        public char[] szInfo = new char[256];
        public int uTimeoutOrVersion;
        public char[] szInfoTitle = new char[64];
        public int dwInfoFlags;
        public Guid.GUID guidItem;
        public HICON hBalloonIcon;

        public NOTIFYICONDATA() {
            cbSize = size();
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("cbSize", "hWnd", "uID", "uFlags", "uCallbackMessage", "hIcon",
                    "szTip", "dwState", "dwStateMask", "szInfo", "uTimeoutOrVersion",
                    "szInfoTitle", "dwInfoFlags", "guidItem", "hBalloonIcon");
        }
    }

    /**
     * Notification severity levels.
     */
    public enum NotificationLevel {
        INFO(NIIF_INFO),
        WARNING(NIIF_WARNING),
        ERROR(NIIF_ERROR),
        NONE(NIIF_NONE);

        private final int flag;

        NotificationLevel(int flag) {
            this.flag = flag;
        }

        public int getFlag() {
            return flag;
        }
    }

    @PostConstruct
    public void initialize() {
        if (!isWindows()) {
            log.info("JNA notifications disabled - not running on Windows");
            return;
        }

        CompletableFuture.runAsync(this::initializeNotificationIcon);
    }

    private void initializeNotificationIcon() {
        try {
            // Create hidden message-only window
            messageWindow = createMessageWindow();
            if (messageWindow == null) {
                log.warn("Failed to create message window for notifications");
                return;
            }

            // Initialize notification icon data
            notifyIconData = new NOTIFYICONDATA();
            notifyIconData.hWnd = messageWindow;
            notifyIconData.uID = 1;
            notifyIconData.uFlags = NIF_MESSAGE | NIF_TIP | NIF_ICON;
            notifyIconData.uCallbackMessage = WM_TRAYICON;

            // Set tooltip
            setString(notifyIconData.szTip, "AIOS Monitor");

            // Load default application icon
            notifyIconData.hIcon = loadDefaultIcon();

            // Add icon to notification area (hidden - we just use it for balloons)
            boolean added = Shell32Ex.INSTANCE.Shell_NotifyIconW(NIM_ADD, notifyIconData);
            if (added) {
                // Set to version 4 for modern balloon behavior
                notifyIconData.uTimeoutOrVersion = NOTIFYICON_VERSION_4;
                Shell32Ex.INSTANCE.Shell_NotifyIconW(NIM_SETVERSION, notifyIconData);
                initialized.set(true);
                log.info("JNA notification system initialized");
            } else {
                log.warn("Failed to add notification icon: error {}", Native.getLastError());
            }

        } catch (Exception e) {
            log.error("Failed to initialize JNA notifications", e);
        }
    }

    /**
     * Show a notification with the specified parameters.
     *
     * @param title   notification title (max 64 chars)
     * @param message notification message (max 256 chars)
     * @param level   notification severity level
     */
    public void showNotification(String title, String message, NotificationLevel level) {
        if (!initialized.get()) {
            log.debug("JNA notifications not initialized, falling back to log");
            log.info("Notification [{}]: {} - {}", level, title, message);
            return;
        }

        CompletableFuture.runAsync(() -> showBalloonNotification(title, message, level));
    }

    /**
     * Show an info-level notification.
     */
    public void showInfo(String title, String message) {
        showNotification(title, message, NotificationLevel.INFO);
    }

    /**
     * Show a warning-level notification.
     */
    public void showWarning(String title, String message) {
        showNotification(title, message, NotificationLevel.WARNING);
    }

    /**
     * Show an error-level notification.
     */
    public void showError(String title, String message) {
        showNotification(title, message, NotificationLevel.ERROR);
    }

    /**
     * Show a critical notification with sound.
     */
    public void showCritical(String title, String message) {
        if (!initialized.get()) {
            log.error("CRITICAL: {} - {}", title, message);
            return;
        }

        CompletableFuture.runAsync(() -> {
            showBalloonNotification(title, message, NotificationLevel.ERROR);
            // Play system sound for critical notifications
            playSystemSound();
        });
    }

    private void showBalloonNotification(String title, String message, NotificationLevel level) {
        try {
            synchronized (this) {
                // Set balloon info
                notifyIconData.uFlags = NIF_INFO;
                setString(notifyIconData.szInfoTitle, truncate(title, 63));
                setString(notifyIconData.szInfo, truncate(message, 255));
                notifyIconData.dwInfoFlags = level.getFlag() | NIIF_RESPECT_QUIET_TIME;

                // Show balloon
                boolean success = Shell32Ex.INSTANCE.Shell_NotifyIconW(NIM_MODIFY, notifyIconData);
                if (!success) {
                    log.warn("Failed to show balloon notification: error {}", Native.getLastError());
                } else {
                    log.debug("Balloon notification shown: {}", title);
                }
            }
        } catch (Exception e) {
            log.warn("Error showing balloon notification", e);
        }
    }

    private HWND createMessageWindow() {
        try {
            // Use User32 to create a message-only window
            String className = "AIOSNotificationClass" + System.currentTimeMillis();
            HMODULE hInstance = Kernel32.INSTANCE.GetModuleHandle(null);
            
            WNDCLASSEX wndClass = new WNDCLASSEX();
            wndClass.hInstance = hInstance;
            wndClass.lpszClassName = className;
            wndClass.lpfnWndProc = new WindowProc() {
                @Override
                public LRESULT callback(HWND hwnd, int uMsg, WPARAM wParam, LPARAM lParam) {
                    return User32.INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam);
                }
            };

            ATOM atom = User32.INSTANCE.RegisterClassEx(wndClass);
            if (atom.intValue() == 0) {
                log.warn("Failed to register window class");
                return null;
            }

            HWND hwnd = User32.INSTANCE.CreateWindowEx(
                    0,
                    className,
                    "AIOS Notification Window",
                    0,
                    0, 0, 0, 0,
                    null, // No parent - standalone hidden window
                    null,
                    hInstance,
                    null
            );

            return hwnd;
        } catch (Exception e) {
            log.warn("Could not create message window: {}", e.getMessage());
            return null;
        }
    }

    private HICON loadDefaultIcon() {
        // For balloon notifications, the icon is optional.
        // The system will display the appropriate icon based on dwInfoFlags (NIIF_INFO, etc.)
        // Return null to use system default icons.
        return null;
    }

    private void playSystemSound() {
        try {
            // Play Windows exclamation sound
            User32Ex.INSTANCE.MessageBeep(0x00000030); // MB_ICONEXCLAMATION
        } catch (Exception e) {
            // Ignore sound errors
        }
    }

    /**
     * Extended User32 with MessageBeep.
     */
    public interface User32Ex extends StdCallLibrary {
        User32Ex INSTANCE = Native.load("user32", User32Ex.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean MessageBeep(int uType);
    }

    private void setString(char[] dest, String src) {
        Arrays.fill(dest, '\0');
        if (src != null) {
            char[] chars = src.toCharArray();
            System.arraycopy(chars, 0, dest, 0, Math.min(chars.length, dest.length - 1));
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    private boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("windows");
    }

    @PreDestroy
    public void cleanup() {
        if (initialized.get() && notifyIconData != null) {
            try {
                Shell32Ex.INSTANCE.Shell_NotifyIconW(NIM_DELETE, notifyIconData);
                log.info("JNA notification icon removed");
            } catch (Exception e) {
                log.debug("Error removing notification icon", e);
            }
        }

        if (messageWindow != null) {
            try {
                User32.INSTANCE.DestroyWindow(messageWindow);
            } catch (Exception e) {
                log.debug("Error destroying message window", e);
            }
        }
    }
}
