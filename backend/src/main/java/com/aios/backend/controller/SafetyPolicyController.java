package com.aios.backend.controller;

import com.aios.backend.model.SafetyPolicyEntity;
import com.aios.backend.service.SafetyPolicyService;
import com.aios.shared.dto.PolicyViolation;
import com.aios.shared.enums.ActionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * REST controller for managing safety policies.
 * 
 * <p>
 * Provides endpoints for CRUD operations on safety policies
 * and policy validation checks.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Safety Policies", description = "Safety policy management endpoints")
public class SafetyPolicyController {

    private final SafetyPolicyService policyService;

    /**
     * Get all enabled safety policies.
     */
    @GetMapping
    @Operation(summary = "Get all policies", description = "Retrieve all enabled safety policies")
    public ResponseEntity<List<SafetyPolicyEntity>> getAllPolicies() {
        return ResponseEntity.ok(policyService.getAllPolicies());
    }

    /**
     * Get a specific policy by name.
     */
    @GetMapping("/name/{name}")
    @Operation(summary = "Get policy by name", description = "Retrieve a specific policy by its unique name")
    public ResponseEntity<SafetyPolicyEntity> getPolicyByName(
            @PathVariable @Parameter(description = "Policy name") String name) {
        return policyService.getPolicyByName(name)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Policy not found: " + name));
    }

    /**
     * Check if an action is allowed by policies.
     */
    @PostMapping("/check")
    @Operation(summary = "Check policy", description = "Check if an action is allowed by current policies")
    public ResponseEntity<PolicyViolation> checkPolicy(
            @RequestBody PolicyCheckRequest request) {

        PolicyViolation violation = policyService.checkPolicy(
                request.actionType(),
                request.processName(),
                request.pid(),
                request.isDryRun(),
                request.confidence());

        return ResponseEntity.ok(violation);
    }

    /**
     * Create a new safety policy.
     */
    @PostMapping
    @Operation(summary = "Create policy", description = "Create a new safety policy")
    public ResponseEntity<SafetyPolicyEntity> createPolicy(
            @RequestBody SafetyPolicyEntity policy) {

        // Check if name already exists
        if (policyService.getPolicyByName(policy.getName()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Policy with name '" + policy.getName() + "' already exists");
        }

        SafetyPolicyEntity saved = policyService.savePolicy(policy);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Update an existing policy.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update policy", description = "Update an existing safety policy")
    public ResponseEntity<SafetyPolicyEntity> updatePolicy(
            @PathVariable Long id,
            @RequestBody SafetyPolicyEntity policy) {

        policy.setId(id);
        SafetyPolicyEntity saved = policyService.savePolicy(policy);
        return ResponseEntity.ok(saved);
    }

    /**
     * Delete a policy.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete policy", description = "Delete a safety policy")
    public ResponseEntity<Void> deletePolicy(@PathVariable Long id) {
        policyService.deletePolicy(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Enable or disable a policy.
     */
    @PatchMapping("/{id}/enabled")
    @Operation(summary = "Toggle policy", description = "Enable or disable a policy")
    public ResponseEntity<Void> setPolicyEnabled(
            @PathVariable Long id,
            @RequestParam boolean enabled) {

        policyService.setPolicyEnabled(id, enabled);
        return ResponseEntity.ok().build();
    }

    /**
     * Add a protected process pattern to a policy.
     */
    @PostMapping("/{id}/protected-patterns")
    @Operation(summary = "Add protected pattern", description = "Add a protected process pattern to a policy")
    public ResponseEntity<Void> addProtectedPattern(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {

        String pattern = request.get("pattern");
        if (pattern == null || pattern.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pattern is required");
        }

        policyService.addProtectedProcess(id, pattern);
        return ResponseEntity.ok().build();
    }

    /**
     * Get the list of system-protected processes (hardcoded).
     */
    @GetMapping("/system-protected")
    @Operation(summary = "Get system protected", description = "Get list of system-critical protected processes")
    public ResponseEntity<List<String>> getSystemProtectedProcesses() {
        return ResponseEntity.ok(policyService.getSystemProtectedProcesses());
    }

    /**
     * Reset rate limit counters.
     */
    @PostMapping("/reset-rate-limits")
    @Operation(summary = "Reset rate limits", description = "Reset all rate limit counters")
    public ResponseEntity<Void> resetRateLimits() {
        policyService.resetRateLimits();
        return ResponseEntity.ok().build();
    }

    /**
     * Get current execution counts (for monitoring rate limits).
     */
    @GetMapping("/execution-counts")
    @Operation(summary = "Get execution counts", description = "Get current execution counts for rate limiting")
    public ResponseEntity<Map<ActionType, Integer>> getExecutionCounts() {
        var counts = policyService.getExecutionCounts();
        // Convert AtomicInteger to Integer for JSON serialization
        Map<ActionType, Integer> result = new java.util.HashMap<>();
        counts.forEach((k, v) -> result.put(k, v.get()));
        return ResponseEntity.ok(result);
    }

    /**
     * Re-initialize default policies (useful for testing/reset).
     */
    @PostMapping("/init-defaults")
    @Operation(summary = "Initialize defaults", description = "Re-initialize default policies")
    public ResponseEntity<Void> initializeDefaults() {
        policyService.initializeDefaultPolicies();
        return ResponseEntity.ok().build();
    }

    /**
     * Request body for policy check endpoint.
     */
    public record PolicyCheckRequest(
            ActionType actionType,
            String processName,
            int pid,
            boolean isDryRun,
            double confidence) {
    }
}
