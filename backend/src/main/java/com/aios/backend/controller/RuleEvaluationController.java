package com.aios.backend.controller;

import com.aios.backend.dto.RuleEvaluationDto;
import com.aios.backend.model.IssueEntity;
import com.aios.backend.service.IssueService;
import com.aios.backend.service.RuleEvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for deterministic rule evaluation.
 */
@RestController
@RequestMapping("/api/rules/evaluation")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Rule Evaluation", description = "Deterministic backend rule evaluation for active issues")
public class RuleEvaluationController {

    private final IssueService issueService;
    private final RuleEvaluationService ruleEvaluationService;

    /**
     * Evaluate a specific issue by ID.
     */
    @GetMapping("/{issueId}")
    @Operation(summary = "Evaluate issue", description = "Evaluate one issue with backend deterministic rules")
    public ResponseEntity<RuleEvaluationDto> evaluateIssue(@PathVariable Long issueId) {
        log.debug("Evaluating issue {} via deterministic rule engine", issueId);

        return issueService.getIssueById(issueId)
                .map(ruleEvaluationService::evaluate)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Evaluate all active unresolved issues.
     */
    @GetMapping("/active")
    @Operation(summary = "Evaluate active issues", description = "Evaluate all active issues with backend deterministic rules")
    public ResponseEntity<List<RuleEvaluationDto>> evaluateActiveIssues() {
        log.debug("Evaluating all active issues via deterministic rule engine");

        List<IssueEntity> activeIssues = issueService.getActiveIssues();
        List<RuleEvaluationDto> evaluations = ruleEvaluationService.evaluateAll(activeIssues);
        return ResponseEntity.ok(evaluations);
    }
}
