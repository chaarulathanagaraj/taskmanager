package com.aios.agent.detector;

import com.aios.agent.collector.ProcessInfoCollector;
import com.aios.agent.collector.SystemMetricsCollector;
import com.aios.agent.config.AgentConfiguration;
import com.aios.shared.dto.DiagnosticIssue;
import com.aios.shared.dto.MetricSnapshot;
import com.aios.shared.dto.ProcessInfo;
import com.aios.shared.enums.IssueType;
import com.aios.shared.enums.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DetectorManager.
 * 
 * Tests the detection orchestration, deduplication, and issue tracking.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DetectorManager Tests")
class DetectorManagerTest {

    @Mock
    private SystemMetricsCollector metricsCollector;

    @Mock
    private ProcessInfoCollector processCollector;

    @Mock
    private AgentConfiguration config;

    @Mock
    private com.aios.agent.client.BackendClient backendClient;

    @Mock
    private IssueDetector mockDetector;

    private DetectorManager manager;

    @BeforeEach
    void setUp() {
        when(config.isDetectionEnabled()).thenReturn(true);
        when(config.getRetentionMinutes()).thenReturn(60);
        when(config.getMonitoredProcessLimit()).thenReturn(50);

        manager = new DetectorManager(
                List.of(mockDetector),
                metricsCollector,
                processCollector,
                config,
                backendClient);
    }

    @Nested
    @DisplayName("Detection Execution")
    class DetectionExecutionTests {

        @Test
        @DisplayName("Should run detection when enabled")
        void shouldRunDetectionWhenEnabled() {
            when(mockDetector.isEnabled()).thenReturn(true);
            when(mockDetector.getName()).thenReturn("TestDetector");
            when(metricsCollector.getRecentMetrics(anyInt())).thenReturn(Collections.emptyList());
            when(processCollector.getTopProcesses(anyInt())).thenReturn(Collections.emptyList());
            when(mockDetector.detect(any(), any())).thenReturn(Collections.emptyList());

            manager.runDetection();

            verify(mockDetector).detect(any(), any());
        }

        @Test
        @DisplayName("Should skip detection when disabled in config")
        void shouldSkipDetectionWhenDisabled() {
            when(config.isDetectionEnabled()).thenReturn(false);

            manager.runDetection();

            verify(mockDetector, never()).detect(any(), any());
        }

        @Test
        @DisplayName("Should skip disabled detectors")
        void shouldSkipDisabledDetectors() {
            when(mockDetector.isEnabled()).thenReturn(false);
            when(mockDetector.getName()).thenReturn("DisabledDetector");
            when(metricsCollector.getRecentMetrics(anyInt())).thenReturn(Collections.emptyList());
            when(processCollector.getTopProcesses(anyInt())).thenReturn(Collections.emptyList());

            manager.runDetection();

            verify(mockDetector, never()).detect(any(), any());
        }

        @Test
        @DisplayName("Should increment run counter after detection")
        void shouldIncrementRunCounter() {
            when(mockDetector.isEnabled()).thenReturn(true);
            when(mockDetector.getName()).thenReturn("TestDetector");
            when(metricsCollector.getRecentMetrics(anyInt())).thenReturn(Collections.emptyList());
            when(processCollector.getTopProcesses(anyInt())).thenReturn(Collections.emptyList());
            when(mockDetector.detect(any(), any())).thenReturn(Collections.emptyList());

            long initialRuns = (long) ReflectionTestUtils.getField(manager, "totalDetectionRuns");

            manager.runDetection();
            manager.runDetection();

            long afterRuns = (long) ReflectionTestUtils.getField(manager, "totalDetectionRuns");
            assertEquals(initialRuns + 2, afterRuns);
        }
    }

    @Nested
    @DisplayName("Issue Detection")
    class IssueDetectionTests {

        @Test
        @DisplayName("Should collect issues from detector")
        void shouldCollectIssuesFromDetector() {
            DiagnosticIssue issue = DiagnosticIssue.builder()
                    .type(IssueType.MEMORY_LEAK)
                    .severity(Severity.HIGH)
                    .affectedPid(1234)
                    .processName("chrome.exe")
                    .confidence(0.85)
                    .details("Memory leak detected")
                    .detectedAt(Instant.now())
                    .build();

            when(mockDetector.isEnabled()).thenReturn(true);
            when(mockDetector.getName()).thenReturn("MemoryDetector");
            when(metricsCollector.getRecentMetrics(anyInt())).thenReturn(Collections.emptyList());
            when(processCollector.getTopProcesses(anyInt())).thenReturn(Collections.emptyList());
            when(mockDetector.detect(any(), any())).thenReturn(List.of(issue));

            manager.runDetection();

            assertEquals(1, manager.getActiveIssues().size());
        }

        @Test
        @DisplayName("Should update statistics on detection")
        void shouldUpdateStatisticsOnDetection() {
            DiagnosticIssue issue = DiagnosticIssue.builder()
                    .type(IssueType.RESOURCE_HOG)
                    .severity(Severity.MEDIUM)
                    .affectedPid(5678)
                    .processName("app.exe")
                    .confidence(0.75)
                    .detectedAt(Instant.now())
                    .build();

            when(mockDetector.isEnabled()).thenReturn(true);
            when(mockDetector.getName()).thenReturn("ResourceDetector");
            when(metricsCollector.getRecentMetrics(anyInt())).thenReturn(Collections.emptyList());
            when(processCollector.getTopProcesses(anyInt())).thenReturn(Collections.emptyList());
            when(mockDetector.detect(any(), any())).thenReturn(List.of(issue));

            manager.runDetection();

            long totalIssues = (long) ReflectionTestUtils.getField(manager, "totalIssuesDetected");
            assertTrue(totalIssues > 0);
        }
    }

    @Nested
    @DisplayName("Issue Deduplication")
    class IssueDeduplicationTests {

        @Test
        @DisplayName("Should keep higher confidence issue when duplicates exist")
        void shouldKeepHigherConfidenceIssue() {
            DiagnosticIssue lowConfidence = DiagnosticIssue.builder()
                    .type(IssueType.MEMORY_LEAK)
                    .severity(Severity.HIGH)
                    .affectedPid(1234)
                    .processName("chrome.exe")
                    .confidence(0.7)
                    .detectedAt(Instant.now())
                    .build();

            DiagnosticIssue highConfidence = DiagnosticIssue.builder()
                    .type(IssueType.MEMORY_LEAK)
                    .severity(Severity.HIGH)
                    .affectedPid(1234)
                    .processName("chrome.exe")
                    .confidence(0.95)
                    .detectedAt(Instant.now())
                    .build();

            when(mockDetector.isEnabled()).thenReturn(true);
            when(mockDetector.getName()).thenReturn("TestDetector");
            when(metricsCollector.getRecentMetrics(anyInt())).thenReturn(Collections.emptyList());
            when(processCollector.getTopProcesses(anyInt())).thenReturn(Collections.emptyList());
            when(mockDetector.detect(any(), any())).thenReturn(List.of(lowConfidence, highConfidence));

            manager.runDetection();

            // Should only have one issue (deduplicated)
            assertEquals(1, manager.getActiveIssues().size());
        }

        @Test
        @DisplayName("Should keep separate issues for different PIDs")
        void shouldKeepSeparateIssuesForDifferentPids() {
            DiagnosticIssue process1 = DiagnosticIssue.builder()
                    .type(IssueType.MEMORY_LEAK)
                    .severity(Severity.HIGH)
                    .affectedPid(1234)
                    .processName("chrome.exe")
                    .confidence(0.85)
                    .detectedAt(Instant.now())
                    .build();

            DiagnosticIssue process2 = DiagnosticIssue.builder()
                    .type(IssueType.MEMORY_LEAK)
                    .severity(Severity.HIGH)
                    .affectedPid(5678)
                    .processName("firefox.exe")
                    .confidence(0.8)
                    .detectedAt(Instant.now())
                    .build();

            when(mockDetector.isEnabled()).thenReturn(true);
            when(mockDetector.getName()).thenReturn("TestDetector");
            when(metricsCollector.getRecentMetrics(anyInt())).thenReturn(Collections.emptyList());
            when(processCollector.getTopProcesses(anyInt())).thenReturn(Collections.emptyList());
            when(mockDetector.detect(any(), any())).thenReturn(List.of(process1, process2));

            manager.runDetection();

            // Should have two separate issues
            assertEquals(2, manager.getActiveIssues().size());
        }

        @Test
        @DisplayName("Should keep separate issues for different types on same PID")
        void shouldKeepSeparateIssuesForDifferentTypes() {
            DiagnosticIssue memoryLeak = DiagnosticIssue.builder()
                    .type(IssueType.MEMORY_LEAK)
                    .severity(Severity.HIGH)
                    .affectedPid(1234)
                    .processName("app.exe")
                    .confidence(0.85)
                    .detectedAt(Instant.now())
                    .build();

            DiagnosticIssue resourceHog = DiagnosticIssue.builder()
                    .type(IssueType.RESOURCE_HOG)
                    .severity(Severity.MEDIUM)
                    .affectedPid(1234)
                    .processName("app.exe")
                    .confidence(0.75)
                    .detectedAt(Instant.now())
                    .build();

            when(mockDetector.isEnabled()).thenReturn(true);
            when(mockDetector.getName()).thenReturn("TestDetector");
            when(metricsCollector.getRecentMetrics(anyInt())).thenReturn(Collections.emptyList());
            when(processCollector.getTopProcesses(anyInt())).thenReturn(Collections.emptyList());
            when(mockDetector.detect(any(), any())).thenReturn(List.of(memoryLeak, resourceHog));

            manager.runDetection();

            // Should have two separate issues (different types)
            assertEquals(2, manager.getActiveIssues().size());
        }
    }

    @Nested
    @DisplayName("Active Issues Tracking")
    class ActiveIssuesTrackingTests {

        @Test
        @DisplayName("Should return empty list when no issues")
        void shouldReturnEmptyListWhenNoIssues() {
            List<DiagnosticIssue> activeIssues = manager.getActiveIssues();

            assertTrue(activeIssues.isEmpty());
            assertEquals(0, manager.getActiveIssues().size());
        }

        @Test
        @DisplayName("Should get active issues after detection")
        void shouldGetActiveIssuesAfterDetection() {
            DiagnosticIssue issue = DiagnosticIssue.builder()
                    .type(IssueType.HUNG_PROCESS)
                    .severity(Severity.CRITICAL)
                    .affectedPid(9999)
                    .processName("critical.exe")
                    .confidence(0.92)
                    .detectedAt(Instant.now())
                    .build();

            when(mockDetector.isEnabled()).thenReturn(true);
            when(mockDetector.getName()).thenReturn("DeadlockDetector");
            when(metricsCollector.getRecentMetrics(anyInt())).thenReturn(Collections.emptyList());
            when(processCollector.getTopProcesses(anyInt())).thenReturn(Collections.emptyList());
            when(mockDetector.detect(any(), any())).thenReturn(List.of(issue));

            manager.runDetection();

            List<DiagnosticIssue> activeIssues = manager.getActiveIssues();
            assertEquals(1, activeIssues.size());
            assertEquals(IssueType.HUNG_PROCESS, activeIssues.get(0).getType());
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle detector exception gracefully")
        void shouldHandleDetectorException() {
            when(mockDetector.isEnabled()).thenReturn(true);
            when(mockDetector.getName()).thenReturn("FailingDetector");
            when(metricsCollector.getRecentMetrics(anyInt())).thenReturn(Collections.emptyList());
            when(processCollector.getTopProcesses(anyInt())).thenReturn(Collections.emptyList());
            when(mockDetector.detect(any(), any())).thenThrow(new RuntimeException("Detection failed"));

            // Should not throw exception
            assertDoesNotThrow(() -> manager.runDetection());
        }

        @Test
        @DisplayName("Should continue with other detectors when one fails")
        void shouldContinueWithOtherDetectors() {
            IssueDetector failingDetector = mock(IssueDetector.class);
            IssueDetector workingDetector = mock(IssueDetector.class);

            when(failingDetector.isEnabled()).thenReturn(true);
            when(failingDetector.getName()).thenReturn("FailingDetector");
            when(failingDetector.detect(any(), any())).thenThrow(new RuntimeException("Failed"));

            when(workingDetector.isEnabled()).thenReturn(true);
            when(workingDetector.getName()).thenReturn("WorkingDetector");
            when(workingDetector.detect(any(), any())).thenReturn(Collections.emptyList());

            DetectorManager managerWithMultiple = new DetectorManager(
                    List.of(failingDetector, workingDetector),
                    metricsCollector,
                    processCollector,
                    config,
                    backendClient);

            when(metricsCollector.getRecentMetrics(anyInt())).thenReturn(Collections.emptyList());
            when(processCollector.getTopProcesses(anyInt())).thenReturn(Collections.emptyList());

            assertDoesNotThrow(() -> managerWithMultiple.runDetection());
            verify(workingDetector).detect(any(), any());
        }
    }

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("Should track total detection runs")
        void shouldTrackTotalDetectionRuns() {
            when(mockDetector.isEnabled()).thenReturn(true);
            when(mockDetector.getName()).thenReturn("TestDetector");
            when(metricsCollector.getRecentMetrics(anyInt())).thenReturn(Collections.emptyList());
            when(processCollector.getTopProcesses(anyInt())).thenReturn(Collections.emptyList());
            when(mockDetector.detect(any(), any())).thenReturn(Collections.emptyList());

            manager.runDetection();
            manager.runDetection();
            manager.runDetection();

            long runs = (long) ReflectionTestUtils.getField(manager, "totalDetectionRuns");
            assertEquals(3, runs);
        }

        @Test
        @DisplayName("Should update last detection time")
        void shouldUpdateLastDetectionTime() {
            when(mockDetector.isEnabled()).thenReturn(true);
            when(mockDetector.getName()).thenReturn("TestDetector");
            when(metricsCollector.getRecentMetrics(anyInt())).thenReturn(Collections.emptyList());
            when(processCollector.getTopProcesses(anyInt())).thenReturn(Collections.emptyList());
            when(mockDetector.detect(any(), any())).thenReturn(Collections.emptyList());

            Instant before = Instant.now();
            manager.runDetection();
            Instant after = Instant.now();

            Instant lastRun = (Instant) ReflectionTestUtils.getField(manager, "lastDetectionRun");
            assertNotNull(lastRun);
            assertFalse(lastRun.isBefore(before));
            assertFalse(lastRun.isAfter(after));
        }
    }
}
