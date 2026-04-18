package com.aios.backend.dto;

import com.aios.shared.enums.IssueStatus;
import com.aios.shared.enums.IssueType;
import com.aios.shared.enums.Severity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Summary payload describing how an issue was resolved.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueResolutionSummary {

    private Long issueId;
    private String processName;
    private Integer affectedPid;
    private IssueType issueType;
    private Severity severity;
    private IssueStatus status;
    private Boolean resolved;
    private Boolean remediationTaken;
    private String source;
    private String message;
    private Instant resolvedAt;
    @Builder.Default
    private List<String> actionsTaken = List.of();
}