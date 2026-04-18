package com.aios.shared.enums;

/**
 * Types of remediation actions
 */
public enum ActionType {
    KILL_PROCESS,
    REDUCE_PRIORITY,
    TRIM_WORKING_SET,
    SUSPEND_PROCESS,
    RESTART_PROCESS,
    CLEAR_TEMP_FILES,
    RESTART_SERVICE,
    DISABLE_STARTUP_APP,
    NOTIFY_USER,
    SUGGEST_REBOOT
}
