package com.aios.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Result payload for bulk automation on active issues.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkAutomationResult {

    private int totalActive;
    private int automated;
    private int resolved;
    private int skippedProtected;
    private int failed;

    @Builder.Default
    private List<IssueAutomationOutcome> outcomes = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IssueAutomationOutcome {
        private Long issueId;
        private String processName;
        private Integer affectedPid;
        private String issueType;
        private String action;
        private String status;
        private String message;
    }
}
