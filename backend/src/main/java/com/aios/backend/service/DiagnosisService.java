package com.aios.backend.service;

import com.aios.ai.dto.CompleteDiagnosisReport;
import com.aios.ai.service.AiDiagnosticOrchestrator;
import com.aios.backend.model.IssueEntity;
import com.aios.backend.repository.IssueRepository;
import com.aios.shared.dto.DiagnosticIssue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Service for AI-powered diagnosis of detected issues.
 * Bridges backend entities with AI agent orchestration.
 * Requires ai-agents module for full AI features.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiagnosisService {

    private final IssueRepository issueRepository;
    private final WebSocketBroadcaster broadcaster;
    private final PerformanceMetrics performanceMetrics;
    private final AiDiagnosticOrchestrator aiOrchestrator;

    /**
     * Run AI diagnosis on an issue by ID.
     *
     * @param issueId the issue ID to diagnose
     * @return diagnosis report or empty if issue not found
     */
    public Optional<CompleteDiagnosisReport> diagnoseIssue(Long issueId) {
        log.info("Starting AI diagnosis for issue ID: {}", issueId);

        // Record diagnosis request
        performanceMetrics.recordAiDiagnosisRequest();
        Instant startTime = Instant.now();

        Optional<IssueEntity> issueOpt = issueRepository.findById(issueId);
        if (issueOpt.isEmpty()) {
            log.warn("Issue not found: {}", issueId);
            return Optional.empty();
        }

        IssueEntity issue = issueOpt.get();
        DiagnosticIssue dto = toDto(issue);

        CompleteDiagnosisReport report = aiOrchestrator.diagnose(dto);

        // Record diagnosis metrics
        Duration duration = Duration.between(startTime, Instant.now());
        performanceMetrics.recordAiDiagnosis(duration, report.isSuccess());

        // Broadcast diagnosis result
        if (report.isSuccess()) {
            broadcaster.broadcastDiagnosisComplete(issueId, report);
        }

        return Optional.of(report);
    }

    /**
     * Check if AI diagnosis features are available.
     * Always returns true since AI is a required dependency.
     */
    public boolean isAiAvailable() {
        return true;
    }

    /**
     * Check if an issue should trigger auto-diagnosis.
     * Auto-diagnose issues with low confidence (<0.6).
     *
     * @param issueId the issue ID to check
     * @return true if auto-diagnosis should be triggered
     */
    public boolean shouldAutoDiagnose(Long issueId) {
        return issueRepository.findById(issueId)
                .map(issue -> issue.getConfidence() < 0.6)
                .orElse(false);
    }

    /**
     * Run diagnosis if issue has low confidence.
     * Called automatically when new issues are created.
     *
     * @param issueId the issue ID to potentially diagnose
     * @return diagnosis report if triggered, empty otherwise
     */
    public Optional<CompleteDiagnosisReport> autoDiagnoseIfNeeded(Long issueId) {
        if (shouldAutoDiagnose(issueId)) {
            log.info("Auto-triggering AI diagnosis for low-confidence issue: {}", issueId);
            return diagnoseIssue(issueId);
        }
        return Optional.empty();
    }

    /**
     * Convert IssueEntity to DiagnosticIssue DTO.
     */
    private DiagnosticIssue toDto(IssueEntity entity) {
        return DiagnosticIssue.builder()
                .id(entity.getId())
                .type(entity.getType())
                .severity(entity.getSeverity())
                .confidence(entity.getConfidence())
                .affectedPid(entity.getAffectedPid())
                .processName(entity.getProcessName())
                .details(entity.getDetails())
                .detectedAt(entity.getDetectedAt())
                .build();
    }
}
