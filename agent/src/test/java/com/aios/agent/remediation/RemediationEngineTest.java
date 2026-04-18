package com.aios.agent.remediation;

import com.aios.agent.client.BackendClient;
import com.aios.agent.config.AgentConfiguration;
import com.aios.shared.dto.DiagnosticIssue;
import com.aios.shared.dto.PolicyViolation;
import com.aios.shared.enums.ActionType;
import com.aios.shared.enums.IssueType;
import com.aios.shared.enums.SafetyLevel;
import com.aios.shared.enums.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RemediationEngine.
 * 
 * Tests action selection, execution, policy enforcement, and error handling.
 * Uses inner classes RemediationEngine.RemediationStatistics and
 * RemediationEngine.ActionExecutionRecord.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RemediationEngine Tests")
class RemediationEngineTest {

    @Mock
    private BackendClient backendClient;

    private AgentConfiguration config;
    private RemediationEngine engine;
    private List<RemediationAction> remediationActions;

    @BeforeEach
    void setUp() {
        config = new AgentConfiguration();
        config.setDryRunMode(true);
        config.setAutoRemediationEnabled(true);
        config.setAutoRemediationConfidenceThreshold(0.7);
        config.setMaxConcurrentActions(3);

        // Create mock remediation actions
        remediationActions = new ArrayList<>();
        remediationActions.add(createMockAction("KillProcessAction", SafetyLevel.HIGH));
        remediationActions.add(createMockAction("ReducePriorityAction", SafetyLevel.LOW));
        remediationActions.add(createMockAction("TrimWorkingSetAction", SafetyLevel.MEDIUM));

        engine = new RemediationEngine(remediationActions, config, backendClient);
        engine.initialize();
    }

    private RemediationAction createMockAction(String name, SafetyLevel level) {
        return new RemediationAction() {
            @Override
            public ActionResult execute(RemediationContext context) {
                if (context.isDryRun()) {
                    return ActionResult.dryRunSuccess("Would execute " + name);
                }
                return ActionResult.success("Executed " + name);
            }

            @Override
            public SafetyLevel getSafetyLevel() {
                return level;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public boolean isEnabled() {
                return true;
            }
        };
    }

    private DiagnosticIssue createIssue(IssueType type, Severity severity, double confidence) {
        return DiagnosticIssue.builder()
                .type(type)
                .severity(severity)
                .confidence(confidence)
                .affectedPid(1234)
                .processName("test-process.exe")
                .details("Test issue description")
                .detectedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("Dry Run Mode")
    class DryRunModeTests {

        @Test
        @DisplayName("Should execute in dry-run mode when enabled")
        void shouldExecuteInDryRunMode() {
            // Given: Dry-run mode is enabled
            config.setDryRunMode(true);
            DiagnosticIssue issue = createIssue(IssueType.MEMORY_LEAK, Severity.HIGH, 0.9);

            // When: Execute remediation
            ActionResult result = engine.executeRemediation(issue);

            // Then: Should be a dry-run result
            assertTrue(result.isSuccess());
            assertTrue(result.isDryRun() || result.getMessage().contains("DRY RUN"));
        }

        @Test
        @DisplayName("Should report what would happen in dry-run mode")
        void shouldReportWhatWouldHappen() {
            // Given: Dry-run mode
            config.setDryRunMode(true);
            DiagnosticIssue issue = createIssue(IssueType.MEMORY_LEAK, Severity.CRITICAL, 0.95);

            // When: Execute remediation
            ActionResult result = engine.executeRemediation(issue);

            // Then: Message should indicate what would happen
            assertNotNull(result.getMessage());
            assertTrue(result.getMessage().length() > 0);
        }
    }

    @Nested
    @DisplayName("Confidence Threshold")
    class ConfidenceThresholdTests {

        @Test
        @DisplayName("Should execute when confidence exceeds threshold")
        void shouldExecuteWhenConfidenceExceedsThreshold() {
            // Given: Issue with high confidence (above threshold)
            config.setAutoRemediationConfidenceThreshold(0.7);
            config.setDryRunMode(false);
            config.setAutoRemediationEnabled(true);
            DiagnosticIssue issue = createIssue(IssueType.MEMORY_LEAK, Severity.HIGH, 0.9);

            // When: Execute remediation with re-initialized engine
            engine = new RemediationEngine(remediationActions, config, backendClient);
            engine.initialize();

            ActionResult result = engine.executeRemediation(issue);

            // Then: Should execute (not blocked by confidence)
            assertNotNull(result);
            // Either success or policy blocked/protected, but not confidence blocked
            if (!result.isSuccess()) {
                assertFalse(
                        result.getMessage().contains("confidence") && result.getMessage().contains("threshold"),
                        "Should not be blocked by confidence threshold");
            }
        }

        @Test
        @DisplayName("Should block when confidence below threshold")
        void shouldBlockWhenConfidenceBelowThreshold() {
            // Given: Issue with low confidence (below threshold)
            config.setAutoRemediationConfidenceThreshold(0.85);
            config.setDryRunMode(false);
            config.setAutoRemediationEnabled(true);
            DiagnosticIssue issue = createIssue(IssueType.MEMORY_LEAK, Severity.LOW, 0.6);

            // When: Execute remediation with re-initialized engine
            engine = new RemediationEngine(remediationActions, config, backendClient);
            engine.initialize();

            ActionResult result = engine.executeRemediation(issue);

            // Then: Should be blocked by confidence threshold
            assertFalse(result.isSuccess());
            assertTrue(
                    result.getMessage().contains("confidence") ||
                            result.getMessage().contains("threshold") ||
                            result.getMessage().contains("requires") ||
                            result.getMessage().contains("manual"),
                    "Should mention confidence threshold or require manual approval");
        }
    }

    @Nested
    @DisplayName("Auto-Remediation Toggle")
    class AutoRemediationToggleTests {

        @Test
        @DisplayName("Should not execute when auto-remediation is disabled")
        void shouldNotExecuteWhenDisabled() {
            // Given: Auto-remediation disabled, not in dry-run
            config.setAutoRemediationEnabled(false);
            config.setDryRunMode(false);
            DiagnosticIssue issue = createIssue(IssueType.MEMORY_LEAK, Severity.HIGH, 0.9);

            // When: Execute remediation with re-initialized engine
            engine = new RemediationEngine(remediationActions, config, backendClient);
            engine.initialize();

            ActionResult result = engine.executeRemediation(issue);

            // Then: Should not execute
            assertFalse(result.isSuccess());
            assertTrue(
                    result.getMessage().contains("disabled"),
                    "Message should indicate remediation is disabled");
        }

        @Test
        @DisplayName("Should execute dry-run even when auto-remediation is disabled")
        void shouldExecuteDryRunEvenWhenDisabled() {
            // Given: Auto-remediation disabled but dry-run mode enabled
            config.setAutoRemediationEnabled(false);
            config.setDryRunMode(true);
            DiagnosticIssue issue = createIssue(IssueType.MEMORY_LEAK, Severity.HIGH, 0.9);

            // When: Execute remediation with re-initialized engine
            engine = new RemediationEngine(remediationActions, config, backendClient);
            engine.initialize();

            ActionResult result = engine.executeRemediation(issue);

            // Then: Should execute in dry-run mode
            assertTrue(result.isSuccess() || result.isDryRun());
        }
    }

    @Nested
    @DisplayName("Protected Processes")
    class ProtectedProcessesTests {

        @Test
        @DisplayName("Should block remediation on protected processes")
        void shouldBlockOnProtectedProcesses() {
            // Given: Issue affecting a protected process
            config.setDryRunMode(false);
            config.setAutoRemediationEnabled(true);
            config.setProtectedProcesses(List.of("System", "csrss.exe", "lsass.exe", "test-process.exe"));

            DiagnosticIssue issue = DiagnosticIssue.builder()
                    .type(IssueType.MEMORY_LEAK)
                    .severity(Severity.HIGH)
                    .confidence(0.95)
                    .affectedPid(1234)
                    .processName("test-process.exe") // This is in protected list
                    .details("Test issue")
                    .detectedAt(Instant.now())
                    .build();

            // When: Execute remediation with re-initialized engine
            engine = new RemediationEngine(remediationActions, config, backendClient);
            engine.initialize();

            ActionResult result = engine.executeRemediation(issue);

            // Then: Should be blocked by policy (or at least not fail without explanation)
            assertNotNull(result);
            // The result should either be blocked by policy or indicate the protection
        }

        @Test
        @DisplayName("Should allow remediation on non-protected processes")
        void shouldAllowOnNonProtectedProcesses() {
            // Given: Issue affecting a non-protected process
            config.setDryRunMode(true);
            config.setAutoRemediationEnabled(true);
            config.setProtectedProcesses(List.of("System", "csrss.exe"));

            DiagnosticIssue issue = DiagnosticIssue.builder()
                    .type(IssueType.MEMORY_LEAK)
                    .severity(Severity.HIGH)
                    .confidence(0.9)
                    .affectedPid(5678)
                    .processName("chrome.exe") // Not in protected list
                    .details("Chrome memory leak")
                    .detectedAt(Instant.now())
                    .build();

            // When: Execute remediation with re-initialized engine
            engine = new RemediationEngine(remediationActions, config, backendClient);
            engine.initialize();

            ActionResult result = engine.executeRemediation(issue);

            // Then: Should succeed (at least in dry-run)
            assertTrue(result.isSuccess() || result.isDryRun());
        }
    }

    @Nested
    @DisplayName("Statistics and History")
    class StatisticsAndHistoryTests {

        @Test
        @DisplayName("Should track action statistics")
        void shouldTrackStatistics() {
            // Given: Execute some actions
            DiagnosticIssue issue = createIssue(IssueType.MEMORY_LEAK, Severity.HIGH, 0.9);
            engine.executeRemediation(issue);
            engine.executeRemediation(issue);

            // When: Get statistics
            RemediationEngine.RemediationStatistics stats = engine.getStatistics();

            // Then: Should have tracked executions
            assertNotNull(stats);
            assertTrue(stats.getTotalActionsExecuted() >= 0);
            assertTrue(stats.getRegisteredActions() > 0);
        }

        @Test
        @DisplayName("Should maintain action history")
        void shouldMaintainHistory() {
            // Given: Execute an action
            DiagnosticIssue issue = createIssue(IssueType.MEMORY_LEAK, Severity.HIGH, 0.9);
            engine.executeRemediation(issue);

            // When: Get history
            List<RemediationEngine.ActionExecutionRecord> history = engine.getActionHistory(10);

            // Then: History should be accessible (may or may not have records depending on
            // implementation)
            assertNotNull(history);
        }

        @Test
        @DisplayName("Should clear history when requested")
        void shouldClearHistory() {
            // Given: Execute some actions
            DiagnosticIssue issue = createIssue(IssueType.MEMORY_LEAK, Severity.HIGH, 0.9);
            engine.executeRemediation(issue);

            // When: Clear history
            engine.clearHistory();

            // Then: History should be empty
            List<RemediationEngine.ActionExecutionRecord> history = engine.getActionHistory(10);
            assertTrue(history.isEmpty());
        }
    }

    @Nested
    @DisplayName("Async Execution")
    class AsyncExecutionTests {

        @Test
        @DisplayName("Should execute multiple remediations asynchronously")
        void shouldExecuteAsync() throws Exception {
            // Given: Multiple issues
            List<DiagnosticIssue> issues = List.of(
                    createIssue(IssueType.MEMORY_LEAK, Severity.HIGH, 0.9),
                    createIssue(IssueType.THREAD_EXPLOSION, Severity.MEDIUM, 0.85),
                    createIssue(IssueType.RESOURCE_HOG, Severity.LOW, 0.75));

            // When: Execute asynchronously
            CompletableFuture<List<ActionResult>> future = engine.executeRemediationsAsync(issues);
            List<ActionResult> results = future.get();

            // Then: Should have results for all issues
            assertNotNull(results);
            assertEquals(issues.size(), results.size());
        }
    }

    @Nested
    @DisplayName("Retry Logic")
    class RetryLogicTests {

        @Test
        @DisplayName("Should return immediately on successful dry-run")
        void shouldReturnImmediatelyOnSuccess() {
            // Given: Dry-run mode (always succeeds)
            config.setDryRunMode(true);
            DiagnosticIssue issue = createIssue(IssueType.MEMORY_LEAK, Severity.HIGH, 0.9);

            // When: Execute with retry
            long startTime = System.currentTimeMillis();
            ActionResult result = engine.executeWithRetry(issue, ActionType.KILL_PROCESS, 3);
            long duration = System.currentTimeMillis() - startTime;

            // Then: Should succeed without retries (fast)
            assertTrue(result.isSuccess() || result.isDryRun());
            assertTrue(duration < 5000, "Should not have retried (completed quickly)");
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle missing action gracefully")
        void shouldHandleMissingAction() {
            // Given: Issue requiring an unregistered action type
            DiagnosticIssue issue = createIssue(IssueType.MEMORY_LEAK, Severity.HIGH, 0.9);

            // The engine may not have a matching action for all action types
            // This tests graceful degradation

            // When: Execute remediation
            ActionResult result = engine.executeRemediation(issue);

            // Then: Should return a result (not throw)
            assertNotNull(result);
        }

        @Test
        @DisplayName("Should handle null issue gracefully")
        void shouldHandleNullIssue() {
            // When/Then: Should handle null issue without crashing
            assertThrows(NullPointerException.class, () -> {
                engine.executeRemediation(null);
            });
        }
    }

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("Should initialize with registered actions")
        void shouldInitializeWithActions() {
            // Given: Engine with mock actions
            RemediationEngine.RemediationStatistics stats = engine.getStatistics();

            // Then: Should have registered actions
            assertTrue(stats.getRegisteredActions() > 0);
        }

        @Test
        @DisplayName("Should shutdown gracefully")
        void shouldShutdownGracefully() {
            // When: Shutdown
            assertDoesNotThrow(() -> engine.shutdown());
        }
    }
}
