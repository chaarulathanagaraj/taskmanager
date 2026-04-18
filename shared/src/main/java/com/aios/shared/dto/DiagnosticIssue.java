package com.aios.shared.dto;

import com.aios.shared.enums.IssueType;
import com.aios.shared.enums.Severity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents a detected system issue
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosticIssue {
    private Long id;
    private IssueType type;
    private Severity severity;
    private double confidence;
    private int affectedPid;
    private String processName;
    private String details;
    private Instant detectedAt;
}
