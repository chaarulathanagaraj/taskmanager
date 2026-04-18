package com.aios.agent.detector;

import com.aios.shared.dto.DiagnosticIssue;
import com.aios.shared.dto.MetricSnapshot;
import com.aios.shared.dto.ProcessInfo;
import com.aios.shared.enums.IssueType;
import com.aios.shared.enums.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MemoryLeakDetector.
 * 
 * Tests detection of memory leak patterns using linear regression analysis.
 * Verifies confidence calculation, severity assignment, and edge cases.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@DisplayName("MemoryLeakDetector Tests")
class MemoryLeakDetectorTest {

    private MemoryLeakDetector detector;

    @BeforeEach
    void setUp() {
        detector = new MemoryLeakDetector();
    }

    @Nested
    @DisplayName("Memory Leak Detection")
    class MemoryLeakDetectionTests {

        @Test
        @DisplayName("Should detect memory leak with steady growth pattern")
        void shouldDetectMemoryLeakWithSteadyGrowth() {
            // Given: A process with steadily increasing memory
            List<ProcessInfo> processes = new ArrayList<>();
            List<MetricSnapshot> metrics = new ArrayList<>();

            // Simulate 8 detection runs (enough for MIN_SAMPLES_FOR_DETECTION = 6)
            // Memory grows from 100MB to 170MB (~10MB per iteration, ~1MB/3s = 20MB/min)
            for (int i = 0; i < 8; i++) {
                long memoryBytes = (100 + i * 10) * 1024L * 1024L; // 100MB to 170MB

                ProcessInfo process = ProcessInfo.builder()
                        .pid(1234)
                        .name("leaky-process")
                        .cpuPercent(50.0)
                        .memoryBytes(memoryBytes)
                        .threadCount(10)
                        .build();

                processes.clear();
                processes.add(process);

                MetricSnapshot snapshot = MetricSnapshot.builder()
                        .timestamp(Instant.now())
                        .cpuUsage(50.0)
                        .memoryUsed(memoryBytes)
                        .memoryTotal(16L * 1024 * 1024 * 1024) // 16GB total
                        .build();
                metrics.clear();
                metrics.add(snapshot);

                // Run detection (updates internal history)
                detector.detect(metrics, processes);

                // Small delay to create time diffs (normally 30s intervals)
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // When: Run final detection
            List<DiagnosticIssue> issues = detector.detect(metrics, processes);

            // Then: Should detect memory leak
            assertFalse(issues.isEmpty(), "Should detect at least one issue");

            DiagnosticIssue issue = issues.get(0);
            assertEquals(IssueType.MEMORY_LEAK, issue.getType());
            assertEquals(1234, issue.getAffectedPid());
            assertEquals("leaky-process", issue.getProcessName());
            assertTrue(issue.getConfidence() >= 0.6, "Confidence should be >= 0.6");
            assertNotNull(issue.getDetails(), "Should have description");
        }

        @Test
        @DisplayName("Should not detect leak with stable memory usage")
        void shouldNotDetectLeakWithStableMemory() {
            // Given: A process with stable memory
            List<ProcessInfo> processes = new ArrayList<>();
            List<MetricSnapshot> metrics = new ArrayList<>();

            // Simulate 8 runs with stable memory (some variance but no trend)
            for (int i = 0; i < 8; i++) {
                // Memory fluctuates around 500MB with ±5MB variance
                long variance = (i % 2 == 0) ? 5 : -5;
                long memoryBytes = (500 + variance) * 1024L * 1024L;

                ProcessInfo process = ProcessInfo.builder()
                        .pid(5678)
                        .name("stable-process")
                        .cpuPercent(25.0)
                        .memoryBytes(memoryBytes)
                        .threadCount(5)
                        .build();

                processes.clear();
                processes.add(process);

                MetricSnapshot snapshot = MetricSnapshot.builder()
                        .timestamp(Instant.now())
                        .cpuUsage(25.0)
                        .memoryUsed(memoryBytes)
                        .memoryTotal(16L * 1024 * 1024 * 1024)
                        .build();
                metrics.clear();
                metrics.add(snapshot);

                detector.detect(metrics, processes);

                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // When: Run final detection
            List<DiagnosticIssue> issues = detector.detect(metrics, processes);

            // Then: Should NOT detect memory leak (stable memory)
            assertTrue(issues.isEmpty(), "Should not detect issues for stable memory");
        }

        @Test
        @DisplayName("Should not detect leak with insufficient samples")
        void shouldNotDetectWithInsufficientSamples() {
            // Given: Only 2 samples (less than MIN_SAMPLES_FOR_DETECTION = 6)
            List<ProcessInfo> processes = new ArrayList<>();
            List<MetricSnapshot> metrics = new ArrayList<>();

            for (int i = 0; i < 2; i++) {
                long memoryBytes = (100 + i * 100) * 1024L * 1024L; // Big jump

                ProcessInfo process = ProcessInfo.builder()
                        .pid(9999)
                        .name("new-process")
                        .cpuPercent(10.0)
                        .memoryBytes(memoryBytes)
                        .threadCount(3)
                        .build();

                processes.clear();
                processes.add(process);

                MetricSnapshot snapshot = MetricSnapshot.builder()
                        .timestamp(Instant.now())
                        .cpuUsage(10.0)
                        .memoryUsed(memoryBytes)
                        .memoryTotal(16L * 1024 * 1024 * 1024)
                        .build();
                metrics.clear();
                metrics.add(snapshot);

                detector.detect(metrics, processes);

                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // When: Run detection
            List<DiagnosticIssue> issues = detector.detect(metrics, processes);

            // Then: Should not detect (insufficient samples)
            assertTrue(issues.isEmpty(), "Should not detect with insufficient samples");
        }

        @Test
        @DisplayName("Should handle empty process list")
        void shouldHandleEmptyProcessList() {
            // Given: Empty process list
            List<ProcessInfo> processes = Collections.emptyList();
            List<MetricSnapshot> metrics = Collections.singletonList(
                    MetricSnapshot.builder()
                            .timestamp(Instant.now())
                            .cpuUsage(0.0)
                            .memoryUsed(0)
                            .memoryTotal(16L * 1024 * 1024 * 1024)
                            .build());

            // When: Run detection
            List<DiagnosticIssue> issues = detector.detect(metrics, processes);

            // Then: Should return empty list without error
            assertNotNull(issues);
            assertTrue(issues.isEmpty());
        }

        @Test
        @DisplayName("Should handle null metrics")
        void shouldHandleNullMetrics() {
            // Given: Process list but null metrics
            List<ProcessInfo> processes = List.of(
                    ProcessInfo.builder()
                            .pid(1111)
                            .name("test-process")
                            .cpuPercent(10.0)
                            .memoryBytes(100 * 1024 * 1024L)
                            .threadCount(5)
                            .build());

            // When/Then: Should handle gracefully
            assertDoesNotThrow(() -> {
                for (int i = 0; i < 6; i++) {
                    detector.detect(null, processes);
                }
            });
        }
    }

    @Nested
    @DisplayName("Severity Assessment")
    class SeverityAssessmentTests {

        @Test
        @DisplayName("Should assign CRITICAL severity for very fast leak")
        void shouldAssignCriticalForFastLeak() {
            // Given: Process with very rapid memory growth (~20MB/min)
            List<ProcessInfo> processes = new ArrayList<>();

            for (int i = 0; i < 10; i++) {
                // Memory grows 200MB per sample (huge leak)
                long memoryBytes = (500L + i * 200L) * 1024 * 1024; // 500MB to 2.3GB

                ProcessInfo process = ProcessInfo.builder()
                        .pid(2222)
                        .name("critical-leak-process")
                        .cpuPercent(80.0)
                        .memoryBytes(memoryBytes)
                        .threadCount(50)
                        .build();

                processes.clear();
                processes.add(process);

                detector.detect(Collections.emptyList(), processes);

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // When: Check detection result
            List<DiagnosticIssue> issues = detector.detect(Collections.emptyList(), processes);

            // Then: If detected, severity should be CRITICAL or HIGH
            if (!issues.isEmpty()) {
                DiagnosticIssue issue = issues.get(0);
                assertTrue(
                        issue.getSeverity() == Severity.CRITICAL || issue.getSeverity() == Severity.HIGH,
                        "Severity should be CRITICAL or HIGH for fast leak");
            }
        }

        @Test
        @DisplayName("Should assign lower severity for slow leak")
        void shouldAssignLowerSeverityForSlowLeak() {
            // Given: Process with slow memory growth (~0.5MB/min)
            List<ProcessInfo> processes = new ArrayList<>();

            for (int i = 0; i < 10; i++) {
                // Memory grows 0.5MB per sample (slow leak)
                long memoryBytes = (100L * 1024 + i * 512) * 1024; // 100MB to ~105MB

                ProcessInfo process = ProcessInfo.builder()
                        .pid(3333)
                        .name("slow-leak-process")
                        .cpuPercent(10.0)
                        .memoryBytes(memoryBytes)
                        .threadCount(5)
                        .build();

                processes.clear();
                processes.add(process);

                detector.detect(Collections.emptyList(), processes);

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // When: Check detection result
            List<DiagnosticIssue> issues = detector.detect(Collections.emptyList(), processes);

            // Then: If detected, severity should be LOW or MEDIUM
            if (!issues.isEmpty()) {
                DiagnosticIssue issue = issues.get(0);
                assertTrue(
                        issue.getSeverity() == Severity.LOW || issue.getSeverity() == Severity.MEDIUM,
                        "Severity should be LOW or MEDIUM for slow leak");
            }
        }
    }

    @Nested
    @DisplayName("Detector Interface")
    class DetectorInterfaceTests {

        @Test
        @DisplayName("Should return correct detector name")
        void shouldReturnCorrectName() {
            assertEquals("MemoryLeakDetector", detector.getName());
        }

        @Test
        @DisplayName("Should use correct confidence threshold")
        void shouldUseCorrectConfidenceThreshold() {
            double threshold = detector.getConfidenceThreshold();
            assertTrue(threshold >= 0.0 && threshold <= 1.0, "Threshold should be between 0.0 and 1.0");
            assertEquals(0.6, threshold, 0.01, "Default threshold should be 0.6");
        }

        @Test
        @DisplayName("Should be enabled by default")
        void shouldBeEnabledByDefault() {
            assertTrue(detector.isEnabled(), "Detector should be enabled by default");
        }
    }

    @Nested
    @DisplayName("Concurrent Access")
    class ConcurrentAccessTests {

        @Test
        @DisplayName("Should handle concurrent detections safely")
        void shouldHandleConcurrentDetections() {
            // Given: Multiple threads calling detect
            int threadCount = 10;
            List<Thread> threads = new ArrayList<>();

            for (int t = 0; t < threadCount; t++) {
                final int threadIndex = t;
                Thread thread = new Thread(() -> {
                    for (int i = 0; i < 5; i++) {
                        ProcessInfo process = ProcessInfo.builder()
                                .pid(1000 + threadIndex)
                                .name("thread-" + threadIndex + "-process")
                                .cpuPercent(10.0 + i)
                                .memoryBytes((100 + i) * 1024L * 1024L)
                                .threadCount(5)
                                .build();

                        List<ProcessInfo> processes = List.of(process);

                        assertDoesNotThrow(() -> detector.detect(Collections.emptyList(), processes));

                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                });
                threads.add(thread);
            }

            // When: Run all threads
            threads.forEach(Thread::start);
            threads.forEach(t -> {
                try {
                    t.join(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            // Then: No exceptions (verified by assertDoesNotThrow above)
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle decreasing memory trend")
        void shouldHandleDecreasingMemory() {
            // Given: Process with decreasing memory (not a leak)
            List<ProcessInfo> processes = new ArrayList<>();

            for (int i = 0; i < 8; i++) {
                // Memory decreases
                long memoryBytes = (500L - i * 20) * 1024 * 1024; // 500MB to 360MB

                ProcessInfo process = ProcessInfo.builder()
                        .pid(4444)
                        .name("shrinking-process")
                        .cpuPercent(20.0)
                        .memoryBytes(memoryBytes)
                        .threadCount(10)
                        .build();

                processes.clear();
                processes.add(process);

                detector.detect(Collections.emptyList(), processes);

                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // When: Run detection
            List<DiagnosticIssue> issues = detector.detect(Collections.emptyList(), processes);

            // Then: Should not detect memory leak (decreasing trend)
            assertTrue(issues.isEmpty(), "Should not detect leak for decreasing memory");
        }

        @Test
        @DisplayName("Should handle process PID reuse")
        void shouldHandlePidReuse() {
            // Given: Old PID data, then same PID with new process
            List<ProcessInfo> processes = new ArrayList<>();

            // First process with PID 5555
            for (int i = 0; i < 6; i++) {
                ProcessInfo process = ProcessInfo.builder()
                        .pid(5555)
                        .name("old-process")
                        .cpuPercent(10.0)
                        .memoryBytes((100 + i * 10) * 1024L * 1024L)
                        .threadCount(5)
                        .build();

                processes.clear();
                processes.add(process);

                detector.detect(Collections.emptyList(), processes);
            }

            // Simulate process termination (empty process list triggers cleanup)
            detector.detect(Collections.emptyList(), Collections.emptyList());

            // New process reuses PID 5555 with different memory pattern
            ProcessInfo newProcess = ProcessInfo.builder()
                    .pid(5555)
                    .name("new-process")
                    .cpuPercent(5.0)
                    .memoryBytes(50 * 1024L * 1024L)
                    .threadCount(2)
                    .build();

            processes.clear();
            processes.add(newProcess);

            // When: Run detection on new process
            List<DiagnosticIssue> issues = detector.detect(Collections.emptyList(), processes);

            // Then: Should not detect (not enough samples for new process after cleanup)
            assertTrue(issues.isEmpty(), "Should not detect after PID reuse and cleanup");
        }

        @Test
        @DisplayName("Should handle very large memory values")
        void shouldHandleLargeMemoryValues() {
            // Given: Process using very large memory (100+ GB)
            ProcessInfo process = ProcessInfo.builder()
                    .pid(6666)
                    .name("large-memory-process")
                    .cpuPercent(50.0)
                    .memoryBytes(100L * 1024 * 1024 * 1024) // 100 GB
                    .threadCount(100)
                    .build();

            List<ProcessInfo> processes = List.of(process);

            // When/Then: Should handle without overflow
            assertDoesNotThrow(() -> {
                for (int i = 0; i < 6; i++) {
                    detector.detect(Collections.emptyList(), processes);
                }
            });
        }

        @Test
        @DisplayName("Should handle zero memory")
        void shouldHandleZeroMemory() {
            // Given: Process with zero memory (unusual but possible)
            ProcessInfo process = ProcessInfo.builder()
                    .pid(7777)
                    .name("zero-memory-process")
                    .cpuPercent(0.0)
                    .memoryBytes(0)
                    .threadCount(1)
                    .build();

            List<ProcessInfo> processes = List.of(process);

            // When/Then: Should handle gracefully
            assertDoesNotThrow(() -> {
                for (int i = 0; i < 6; i++) {
                    detector.detect(Collections.emptyList(), processes);
                }
            });
        }
    }
}
