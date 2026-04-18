package com.aios.shared.dto;

import com.aios.shared.enums.ActionStatus;
import com.aios.shared.enums.ActionType;
import com.aios.shared.enums.SafetyLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents a remediation action to fix an issue
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemediationAction {
    private Long id;
    private ActionType actionType;
    private int targetPid;
    private String targetName;
    private SafetyLevel safetyLevel;
    private ActionStatus status;
    private String result;
    private boolean dryRun;
    private Instant executedAt;
}
