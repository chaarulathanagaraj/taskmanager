package com.aios.backend.controller;

import com.aios.ai.dto.CompleteDiagnosisReport;
import com.aios.ai.dto.RuleExecutionPreview;
import com.aios.ai.service.RulePreviewService;
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

import java.util.Optional;

/**
 * REST controller for rule execution preview.
 * Shows users what will happen before automation executes.
 */
@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Tag(name = "Rules", description = "Rule execution and preview APIs")
public class RuleController {

    private final DiagnosisService diagnosisService;
    private final RulePreviewService previewService;

    /**
     * Get a preview of what rule execution will do for an issue.
     * This MUST be called before executing any automation.
     * 
     * @param issueId the issue ID to preview
     * @return preview of execution steps, warnings, and expected outcome
     */
    @GetMapping("/preview/{issueId}")
    @Operation(summary = "Preview rule execution", description = "Shows what will happen if automation is executed for this issue. "
            +
            "Includes all steps, warnings, risk level, and expected outcome.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Preview generated successfully"),
            @ApiResponse(responseCode = "404", description = "Issue not found or no remediation plan available"),
            @ApiResponse(responseCode = "500", description = "Failed to generate preview")
    })
    public ResponseEntity<RuleExecutionPreview> getPreview(
            @Parameter(description = "Issue ID to preview") @PathVariable Long issueId) {

        log.info("GET /api/rules/preview/{} - Generating execution preview", issueId);

        try {
            // Get diagnosis for the issue
            Optional<CompleteDiagnosisReport> reportOpt = diagnosisService.diagnoseIssue(issueId);

            if (reportOpt.isEmpty()) {
                log.warn("No diagnosis report found for issue: {}", issueId);
                return ResponseEntity.notFound().build();
            }

            CompleteDiagnosisReport report = reportOpt.get();

            // Check if diagnosis was successful and has a remediation plan
            if (!report.isSuccess() || report.getRemediationPlan() == null) {
                log.warn("No remediation plan available for issue: {}", issueId);
                return ResponseEntity.notFound().build();
            }

            // Generate preview
            RuleExecutionPreview preview = previewService.generatePreview(report);

            log.info("Generated preview for issue {}: action={}, riskLevel={}, approvalRequired={}",
                    issueId,
                    preview.getPrimaryAction(),
                    preview.getRiskLevel(),
                    preview.isApprovalRequired());

            return ResponseEntity.ok(preview);

        } catch (IllegalArgumentException e) {
            log.error("Invalid request for preview: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Failed to generate preview for issue {}: {}", issueId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Check if an issue has a remediation plan available for preview.
     * Lightweight check before calling the full preview endpoint.
     * 
     * @param issueId the issue ID to check
     * @return true if preview is available
     */
    @GetMapping("/preview/{issueId}/available")
    @Operation(summary = "Check if preview is available", description = "Quick check to see if a rule preview can be generated for this issue")
    public ResponseEntity<Boolean> isPreviewAvailable(
            @Parameter(description = "Issue ID to check") @PathVariable Long issueId) {

        log.debug("Checking if preview available for issue: {}", issueId);

        try {
            Optional<CompleteDiagnosisReport> reportOpt = diagnosisService.diagnoseIssue(issueId);
            boolean available = reportOpt.isPresent()
                    && reportOpt.get().isSuccess()
                    && reportOpt.get().getRemediationPlan() != null;

            return ResponseEntity.ok(available);
        } catch (Exception e) {
            log.error("Error checking preview availability: {}", e.getMessage());
            return ResponseEntity.ok(false);
        }
    }
}
