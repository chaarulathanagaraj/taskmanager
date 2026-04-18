package com.aios.agent.remediation;

/**
 * Windows process priority classes.
 * 
 * These correspond to the Windows priority classes that determine
 * how much CPU time a process receives relative to other processes.
 * 
 * Priority levels (highest to lowest):
 * 1. REALTIME - Time-critical operations (use with extreme caution)
 * 2. HIGH - High priority (e.g., performance monitoring tools)
 * 3. ABOVE_NORMAL - Slightly elevated priority
 * 4. NORMAL - Default priority for most applications
 * 5. BELOW_NORMAL - Lower priority (good for background tasks)
 * 6. IDLE - Only runs when system is idle
 * 
 * See: https://docs.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-setpriorityclass
 */
public enum ProcessPriority {

    /**
     * Idle priority - only runs when system is idle.
     * Use for very low priority background tasks.
     */
    IDLE(0x00000040),

    /**
     * Below normal priority.
     * Use for background tasks that should yield to interactive applications.
     */
    BELOW_NORMAL(0x00004000),

    /**
     * Normal priority - default for most applications.
     */
    NORMAL(0x00000020),

    /**
     * Above normal priority.
     * Use for important but not time-critical tasks.
     */
    ABOVE_NORMAL(0x00008000),

    /**
     * High priority.
     * Use for time-sensitive operations.
     * Can impact system responsiveness if overused.
     */
    HIGH(0x00000080),

    /**
     * Realtime priority - highest possible priority.
     * 
     * WARNING: Use with extreme caution!
     * Can cause system instability if process enters infinite loop.
     * Should only be used for very short, time-critical operations.
     */
    REALTIME(0x00000100);

    private final int value;

    ProcessPriority(int value) {
        this.value = value;
    }

    /**
     * Get the Windows API priority class value.
     * 
     * @return Windows priority class constant
     */
    public int getValue() {
        return value;
    }

    /**
     * Get ProcessPriority from Windows API value.
     * 
     * @param value Windows priority class constant
     * @return Corresponding ProcessPriority, or NORMAL if unknown
     */
    public static ProcessPriority fromValue(int value) {
        for (ProcessPriority priority : values()) {
            if (priority.value == value) {
                return priority;
            }
        }
        return NORMAL; // Default fallback
    }

    /**
     * Check if this priority is higher than another.
     * 
     * @param other Priority to compare against
     * @return true if this priority is higher
     */
    public boolean isHigherThan(ProcessPriority other) {
        return this.ordinal() > other.ordinal();
    }

    /**
     * Check if this priority is considered safe for automated changes.
     * 
     * HIGH and REALTIME priorities should generally require manual approval.
     * 
     * @return true if safe for automated use
     */
    public boolean isSafeForAutomation() {
        return this != HIGH && this != REALTIME;
    }
}
