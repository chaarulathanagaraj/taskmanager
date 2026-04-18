package com.aios.backend.controller;

import com.aios.ai.dto.CompleteDiagnosisReport;
import com.aios.backend.service.DiagnosisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for AI-powered diagnosis endpoints.
 * 
 * <p>
 * Provides APIs for:
 * <ul>
 * <li>Running AI diagnosis on detected issues</li>
 * <li>Getting diagnosis recommendations</li>
 * <li>Auto-triggering diagnosis for low-confidence issues</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/diagnose")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Tag(name = "Diagnosis", description = "AI-powered issue diagnosis APIs")
public class DiagnosisController {

    private final DiagnosisService diagnosisService;

    /**
     * Run AI diagnosis on a specific issue.
     * 
     * @param issueId the issue ID to diagnose
     * @return diagnosis report with analysis, remediation plan, and safety
     *         validation
     */
    @PostMapping("/{issueId}")
    @Operation(summary = "Run AI diagnosis", description = "Analyzes an issue using specialized AI agents and returns a remediation plan")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Diagnosis completed (check success field and confidence level in response)"),
            @ApiResponse(responseCode = "404", description = "Issue not found")
    })
    public ResponseEntity<CompleteDiagnosisReport> diagnoseIssue(
            @Parameter(description = "Issue ID to diagnose") @PathVariable(name = "issueId") Long issueId) {

        log.info("POST /api/diagnose/{} - Running AI diagnosis", issueId);

        return diagnosisService.diagnoseIssue(issueId)
                .map(report -> {
                    if (report.isSuccess()) {
                        log.info("Diagnosis completed for issue {}: confidence={}",
                                issueId, report.getConfidence());
                    } else {
                        log.warn("Diagnosis completed with low confidence for issue {}: {} (confidence={})",
                                issueId, report.getMessage(), report.getConfidence());
                    }
                    // Return 200 OK regardless - the diagnosis operation succeeded, even if
                    // confidence is low
                    return ResponseEntity.ok(report);
                })
                .orElseGet(() -> {
                    log.warn("Issue not found: {}", issueId);
                    return ResponseEntity.notFound().build();
                });
    }

    /**
     * Check if an issue needs AI diagnosis.
     * Returns true for low-confidence issues (<0.6).
     *
     * @param issueId the issue ID to check
     * @return whether auto-diagnosis is recommended
     */
    @GetMapping("/{issueId}/needs-diagnosis")
    @Operation(summary = "Check if diagnosis needed", description = "Returns true if the issue has low confidence and would benefit from AI analysis")
    public ResponseEntity<NeedsDiagnosisResponse> checkNeedsDiagnosis(
            @Parameter(description = "Issue ID to check") @PathVariable(name = "issueId") Long issueId) {

        boolean needsDiagnosis = diagnosisService.shouldAutoDiagnose(issueId);
        return ResponseEntity.ok(new NeedsDiagnosisResponse(issueId, needsDiagnosis));
    }

    /**
     * Auto-diagnose issue if it has low confidence.
     * Only triggers if confidence < 0.6.
     *
     * @param issueId the issue ID to potentially diagnose
     * @return diagnosis report if triggered, 204 No Content otherwise
     */
    @PostMapping("/{issueId}/auto")
    @Operation(summary = "Auto-diagnose if needed", description = "Runs diagnosis only if issue has low confidence (<0.6)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Auto-diagnosis triggered and completed"),
            @ApiResponse(responseCode = "204", description = "Diagnosis not needed (confidence already high)"),
            @ApiResponse(responseCode = "404", description = "Issue not found")
    })
    public ResponseEntity<CompleteDiagnosisReport> autoDiagnose(
            @Parameter(description = "Issue ID to auto-diagnose") @PathVariable(name = "issueId") Long issueId) {

        log.info("POST /api/diagnose/{}/auto - Checking auto-diagnosis", issueId);

        return diagnosisService.autoDiagnoseIfNeeded(issueId)
                .map(report -> {
                    log.info("Auto-diagnosis completed for issue {}", issueId);
                    return ResponseEntity.ok(report);
                })
                .orElseGet(() -> {
                    log.info("Auto-diagnosis not needed for issue {}", issueId);
                    return ResponseEntity.noContent().build();
                });
    }

    /**
     * Get diagnosis history (all past diagnosis reports).
     * 
     * @return list of all diagnosis reports
     */
    @GetMapping("/history")
    @Operation(summary = "Get diagnosis history", description = "Returns all historical diagnosis reports")
    @ApiResponse(responseCode = "200", description = "History retrieved successfully")
    public ResponseEntity<java.util.List<CompleteDiagnosisReport>> getHistory() {
        log.info("GET /api/diagnose/history - Fetching diagnosis history");

        // TODO: Implement actual history retrieval from database
        // For now, return empty list as diagnosis reports are not persisted
        return ResponseEntity.ok(java.util.Collections.emptyList());
    }

    /**
     * Response DTO for needs-diagnosis check.
     */
    public record NeedsDiagnosisResponse(Long issueId, boolean needsDiagnosis) {
    }
}
