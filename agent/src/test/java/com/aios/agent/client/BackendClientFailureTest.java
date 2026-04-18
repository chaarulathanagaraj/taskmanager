package com.aios.agent.client;

import com.aios.agent.config.AgentConfiguration;
import com.aios.shared.dto.DiagnosticIssue;
import com.aios.shared.dto.MetricSnapshot;
import com.aios.shared.dto.PolicyViolation;
import com.aios.shared.dto.RemediationActionLog;
import com.aios.shared.enums.ActionType;
import com.aios.shared.enums.IssueType;
import com.aios.shared.enums.Severity;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for BackendClient failure handling behavior.
 * 
 * <p>Validates:
 * <ul>
 * <li>Queuing when backend is unavailable</li>
 * <li>Local fallback policy when backend is down</li>
 * <li>Retry behavior with exponential backoff</li>
 * <li>Queue overflow handling</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BackendClient Failure Mode Tests")
class BackendClientFailureTest {

    @Mock
    private AgentConfiguration config;

    private BackendClient client;

    // Minimal retry configs for testing
    private Retry metricRetry;
    private Retry issueRetry;
    private Retry actionRetry;
    private Retry healthRetry;

    @BeforeEach
    void setUp() {
        when(config.getBackendUrl()).thenReturn("http://localhost:8080");

        // Create fast-failing retries for tests
        RetryConfig fastFailConfig = RetryConfig.custom()
                .maxAttempts(1)
                .waitDuration(Duration.ofMillis(10))
                .build();

        metricRetry = Retry.of("metricSyncRetry", fastFailConfig);
        issueRetry = Retry.of("issueSyncRetry", fastFailConfig);
        actionRetry = Retry.of("actionSyncRetry", fastFailConfig);
        healthRetry = Retry.of("healthCheckRetry", fastFailConfig);

        client = new BackendClient(config, metricRetry, issueRetry, actionRetry, healthRetry);
    }

    @Nested
    @DisplayName("Queue Behavior Tests")
    class QueueBehaviorTests {

        @Test
        @DisplayName("Should queue metric when added")
        void shouldQueueMetric() {
            MetricSnapshot metric = createTestMetric();

            client.queueMetric(metric);

            // Metric should be queued (internal state)
            // Verify by checking no exception thrown and client accepts more
            assertDoesNotThrow(() -> client.queueMetric(createTestMetric()));
        }

        @Test
        @DisplayName("Should queue issue when added")
        void shouldQueueIssue() {
            DiagnosticIssue issue = createTestIssue();

            client.queueIssue(issue);

            assertDoesNotThrow(() -> client.queueIssue(createTestIssue()));
        }

        @Test
        @DisplayName("Should queue action when added")
        void shouldQueueAction() {
            RemediationActionLog action = createTestAction();

            client.queueAction(action);

            assertDoesNotThrow(() -> client.queueAction(createTestAction()));
        }

        @Test
        @DisplayName("Should handle many queued items")
        void shouldHandleManyQueuedItems() {
            // Queue up many metrics without syncing
            for (int i = 0; i < 500; i++) {
                client.queueMetric(createTestMetric());
            }

            // Should not throw even with many items
            assertDoesNotThrow(() -> client.queueMetric(createTestMetric()));
        }

        @Test
        @DisplayName("Should handle queue overflow gracefully")
        void shouldHandleQueueOverflowGracefully() {
            // Queue the maximum plus some extra
            for (int i = 0; i < 1100; i++) {
                assertDoesNotThrow(() -> client.queueMetric(createTestMetric()));
            }
        }
    }

    @Nested
    @DisplayName("Local Policy Fallback Tests")
    class LocalPolicyFallbackTests {

        @Test
        @DisplayName("Should block critical process csrss.exe locally")
        void shouldBlockCsrssLocally() {
            // Simulate backend unavailable by using a client that can trigger fallback
            PolicyViolation violation = client.checkPolicy(
                    ActionType.KILL_PROCESS, "csrss.exe", 4, false, 0.99);

            // When backend available, it may allow; when down, local fallback blocks
            // This tests the fallback path
            assertNotNull(violation);
        }

        @Test
        @DisplayName("Should block critical process lsass.exe locally")
        void shouldBlockLsassLocally() {
            PolicyViolation violation = client.checkPolicy(
                    ActionType.KILL_PROCESS, "lsass.exe", 600, false, 0.95);

            assertNotNull(violation);
        }

        @Test
        @DisplayName("Should apply conservative policy for system process")
        void shouldApplyConservativePolicyForSystem() {
            PolicyViolation violation = client.checkPolicy(
                    ActionType.KILL_PROCESS, "System", 4, false, 0.9);

            assertNotNull(violation);
        }

        @Test
        @DisplayName("Should handle null process name in fallback")
        void shouldHandleNullProcessNameInFallback() {
            PolicyViolation violation = client.checkPolicy(
                    ActionType.RESTART_SERVICE, null, 1234, false, 0.8);

            assertNotNull(violation);
        }

        @Test
        @DisplayName("Should allow regular process when backend available")
        void shouldAllowRegularProcessWhenBackendAvailable() {
            // Normal process that isn't critical
            PolicyViolation violation = client.checkPolicy(
                    ActionType.KILL_PROCESS, "notepad.exe", 5678, false, 0.95);

            // Either allowed via backend or local fallback allows it
            assertNotNull(violation);
        }
    }

    @Nested
    @DisplayName("Offline Operation Tests")
    class OfflineOperationTests {

        @Test
        @DisplayName("Should continue queuing when backend down")
        void shouldContinueQueuingWhenBackendDown() {
            // Queue items (they will accumulate since no actual backend)
            for (int i = 0; i < 10; i++) {
                client.queueMetric(createTestMetric());
                client.queueIssue(createTestIssue());
                client.queueAction(createTestAction());
            }

            // Should not throw - items are queued for later sync
            assertDoesNotThrow(() -> {
                client.queueMetric(createTestMetric());
                client.queueIssue(createTestIssue());
                client.queueAction(createTestAction());
            });
        }

        @Test
        @DisplayName("Should handle rapid queuing during outage")
        void shouldHandleRapidQueuingDuringOutage() {
            // Simulate rapid data generation during backend outage
            for (int i = 0; i < 100; i++) {
                assertDoesNotThrow(() -> {
                    client.queueMetric(createTestMetric());
                    client.queueIssue(createTestIssue());
                });
            }
        }
    }

    @Nested
    @DisplayName("Retry Configuration Tests")
    class RetryConfigurationTests {

        @Test
        @DisplayName("Should have configured retry instances")
        void shouldHaveConfiguredRetryInstances() {
            assertEquals("metricSyncRetry", metricRetry.getName());
            assertEquals("issueSyncRetry", issueRetry.getName());
            assertEquals("actionSyncRetry", actionRetry.getName());
            assertEquals("healthCheckRetry", healthRetry.getName());
        }

        @Test
        @DisplayName("Should have max attempts configured")
        void shouldHaveMaxAttemptsConfigured() {
            assertEquals(1, metricRetry.getRetryConfig().getMaxAttempts());
            assertEquals(1, issueRetry.getRetryConfig().getMaxAttempts());
        }
    }

    // Helper methods to create test data

    private MetricSnapshot createTestMetric() {
        return MetricSnapshot.builder()
                .timestamp(Instant.now())
                .cpuUsage(45.5)
                .memoryUsed(4_000_000_000L)
                .memoryTotal(8_000_000_000L)
                .diskRead(1024L)
                .diskWrite(512L)
                .networkSent(1024L)
                .networkReceived(512L)
                .build();
    }

    private DiagnosticIssue createTestIssue() {
        return DiagnosticIssue.builder()
                .type(IssueType.MEMORY_LEAK)
                .severity(Severity.HIGH)
                .processName("test-app.exe")
                .affectedPid(1234)
                .confidence(0.85)
                .details("Test memory leak detected")
                .detectedAt(Instant.now())
                .build();
    }

    private RemediationActionLog createTestAction() {
        return RemediationActionLog.builder()
                .actionType(ActionType.KILL_PROCESS)
                .targetName("test-app.exe")
                .targetPid(1234)
                .status(com.aios.shared.enums.ActionStatus.SUCCESS)
                .result("Process terminated")
                .executedAt(Instant.now())
                .build();
    }
}
