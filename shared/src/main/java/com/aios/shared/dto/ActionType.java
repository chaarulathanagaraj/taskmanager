package com.aios.shared.dto;

/**
 * Constants for action types.
 */
public final class ActionType {
    public static final String KILL_PROCESS = "KILL_PROCESS";
    public static final String REDUCE_PRIORITY = "REDUCE_PRIORITY";
    public static final String TRIM_WORKING_SET = "TRIM_WORKING_SET";
    public static final String RESTART_PROCESS = "RESTART_PROCESS";
    public static final String MONITOR = "MONITOR";

    private ActionType() {
        // Utility class
    }
}
