package com.aios.shared.enums;

/**
 * Safety levels for remediation actions
 */
public enum SafetyLevel {
    LOW,      // Minimal risk (reduce priority, trim memory)
    MEDIUM,   // Moderate risk (clear temp files)
    HIGH,     // Significant risk (kill user process)
    CRITICAL  // System risk (restart service, registry edit)
}
