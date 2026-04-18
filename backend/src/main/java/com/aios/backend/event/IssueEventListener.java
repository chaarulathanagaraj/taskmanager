package com.aios.backend.event;

import com.aios.ai.dto.CompleteDiagnosisReport;
import com.aios.backend.model.IssueEntity;
import com.aios.backend.service.DiagnosisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Event listener for issue-related events.
 * Handles async processing like auto-triggering AI diagnosis.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IssueEventListener {

    private final DiagnosisService diagnosisService;

    @Value("${aios.ai.auto-diagnose.enabled:true}")
    private boolean autoDiagnoseEnabled;

    @Value("${aios.ai.auto-diagnose.confidence-threshold:0.6}")
    private double confidenceThreshold;

    /**
     * Handle new issue creation.
     * Auto-triggers AI diagnosis for low-confidence detections.
     */
    @Async
    @EventListener
    public void onIssueCreated(IssueCreatedEvent event) {
        IssueEntity issue = event.getIssue();

        if (!autoDiagnoseEnabled) {
            log.debug("Auto-diagnosis disabled, skipping issue {}", issue.getId());
            return;
        }

        if (issue.getConfidence() >= confidenceThreshold) {
            log.debug("Issue {} has sufficient confidence ({} >= {}), skipping auto-diagnosis",
                    issue.getId(), issue.getConfidence(), confidenceThreshold);
            return;
        }

        log.info("Auto-triggering AI diagnosis for low-confidence issue {} (confidence: {})",
                issue.getId(), issue.getConfidence());

        try {
            Optional<CompleteDiagnosisReport> report = diagnosisService.diagnoseIssue(issue.getId());

            if (report.isPresent() && report.get().isSuccess()) {
                log.info("Auto-diagnosis completed for issue {}: new confidence={}",
                        issue.getId(), report.get().getConfidence());
            } else {
                log.warn("Auto-diagnosis failed or returned no result for issue {}", issue.getId());
            }
        } catch (Exception e) {
            log.error("Error during auto-diagnosis for issue {}: {}", issue.getId(), e.getMessage(), e);
        }
    }
}
