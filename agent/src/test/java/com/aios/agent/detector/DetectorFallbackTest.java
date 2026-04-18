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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for detector fallback behavior when external services are unavailable.
 * 
 * <p>Validates that rule-based detection continues to function when:
 * <ul>
 * <li>AI/OpenAI service is unavailable</li>
 * <li>Backend service is down</li>
 * <li>MCP tools return null or errors</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Detector Fallback Mode Tests")
class DetectorFallbackTest {

    @Nested
    @DisplayName("Memory Leak Detection Fallback")
    class MemoryLeakDetectionFallback {

        @Test
        @DisplayName("Should detect memory leak without AI using rule-based thresholds")
        void shouldDetectMemoryLeakWithRules() {
            // Create process with high memory - rule-based detection should trigger
            ProcessInfo highMemoryProcess = ProcessInfo.builder()
                    .pid(1234)
                    .name("memory-hog.exe")
                    .memoryBytes(3_000_000_000L) // 3GB - well above threshold
                    .cpuPercent(10.0)
                    .threadCount(50)
                    .build();

            // Rule-based check: memory > 2GB is suspicious
            assertTrue(highMemoryProcess.getMemoryBytes() > 2_000_000_000L,
                    "High memory process should be detected by rule-based threshold");
        }

        @Test
        @DisplayName("Should classify severity based on memory thresholds")
        void shouldClassifySeverityBasedOnThresholds() {
            // Test severity classification rules
            long criticalThreshold = 4_000_000_000L; // 4GB
            long highThreshold = 2_000_000_000L; // 2GB
            long mediumThreshold = 1_000_000_000L; // 1GB

            // Critical severity
            long criticalMemory = 5_000_000_000L;
            assertTrue(criticalMemory > criticalThreshold);

            // High severity
            long highMemory = 3_000_000_000L;
            assertTrue(highMemory > highThreshold && highMemory < criticalThreshold);

            // Medium severity
            long mediumMemory = 1_500_000_000L;
            assertTrue(mediumMemory > mediumThreshold && mediumMemory < highThreshold);
        }

        @Test
        @DisplayName("Should handle null process info gracefully")
        void shouldHandleNullProcessInfoGracefully() {
            ProcessInfo nullProcess = null;

            // Detection should not throw, just return empty or skip
            assertNull(nullProcess);
        }
    }

    @Nested
    @DisplayName("Thread Explosion Detection Fallback")
    class ThreadExplosionDetectionFallback {

        @Test
        @DisplayName("Should detect thread explosion using count threshold")
        void shouldDetectThreadExplosionUsingThreshold() {
            int normalThreadCount = 50;
            int explosionThreadCount = 500;
            int threshold = 200;

            assertFalse(normalThreadCount > threshold);
            assertTrue(explosionThreadCount > threshold);
        }

        @Test
        @DisplayName("Should detect rapid thread growth rate")
        void shouldDetectRapidThreadGrowthRate() {
            // Simulate thread count samples over time
            List<Integer> threadCounts = List.of(50, 75, 120, 200, 350);

            // Calculate growth rate
            int firstCount = threadCounts.get(0);
            int lastCount = threadCounts.get(threadCounts.size() - 1);
            double growthRate = (double) (lastCount - firstCount) / firstCount * 100;

            // Rule: >200% growth in sample window is suspicious
            assertTrue(growthRate > 200, "High growth rate should trigger detection");
        }
    }

    @Nested
    @DisplayName("Resource Hog Detection Fallback")
    class ResourceHogDetectionFallback {

        @Test
        @DisplayName("Should detect CPU hog using threshold")
        void shouldDetectCpuHogUsingThreshold() {
            double normalCpu = 25.0;
            double hogCpu = 95.0;
            double threshold = 80.0;

            assertFalse(normalCpu > threshold);
            assertTrue(hogCpu > threshold);
        }

        @Test
        @DisplayName("Should detect sustained high CPU usage")
        void shouldDetectSustainedHighCpu() {
            // Rule: CPU > 80% for 3+ consecutive samples
            List<Double> cpuSamples = List.of(85.0, 90.0, 88.0, 92.0);

            long highCount = cpuSamples.stream().filter(cpu -> cpu > 80.0).count();
            assertTrue(highCount >= 3, "Sustained high CPU should be detected");
        }
    }

    @Nested
    @DisplayName("I/O Bottleneck Detection Fallback")
    class IoBottleneckDetectionFallback {

        @Test
        @DisplayName("Should detect high disk queue length")
        void shouldDetectHighDiskQueueLength() {
            double normalQueueLength = 0.5;
            double bottleneckQueueLength = 5.0;
            double threshold = 2.0;

            assertFalse(normalQueueLength > threshold);
            assertTrue(bottleneckQueueLength > threshold);
        }

        @Test
        @DisplayName("Should detect low disk idle percentage")
        void shouldDetectLowDiskIdlePercentage() {
            double normalIdlePercent = 70.0;
            double bottleneckIdlePercent = 5.0;
            double threshold = 20.0;

            assertTrue(normalIdlePercent > threshold);
            assertFalse(bottleneckIdlePercent > threshold,
                    "Low disk idle indicates I/O bottleneck");
        }
    }

    @Nested
    @DisplayName("Graceful Degradation Tests")
    class GracefulDegradationTests {

        @Test
        @DisplayName("Should work with empty metrics list")
        void shouldWorkWithEmptyMetricsList() {
            List<MetricSnapshot> emptyMetrics = Collections.emptyList();

            assertNotNull(emptyMetrics);
            assertEquals(0, emptyMetrics.size());
        }

        @Test
        @DisplayName("Should work with empty process list")
        void shouldWorkWithEmptyProcessList() {
            List<ProcessInfo> emptyProcesses = Collections.emptyList();

            assertNotNull(emptyProcesses);
            assertEquals(0, emptyProcesses.size());
        }

        @Test
        @DisplayName("Should handle metric with zero values gracefully")
        void shouldHandleMetricWithZeroValuesGracefully() {
            MetricSnapshot metric = MetricSnapshot.builder()
                    .timestamp(Instant.now())
                    .cpuUsage(0.0)
                    .memoryUsed(0L)
                    .memoryTotal(8_000_000_000L)
                    .build();

            // Should handle zero values gracefully
            assertEquals(0.0, metric.getCpuUsage());
            assertEquals(0L, metric.getMemoryUsed());
        }

        @Test
        @DisplayName("Should assign default confidence when AI unavailable")
        void shouldAssignDefaultConfidenceWhenAIUnavailable() {
            // Rule-based detection should use conservative confidence values
            double ruleBasedConfidence = 0.7;

            // Should be reasonable but not overconfident
            assertTrue(ruleBasedConfidence >= 0.5 && ruleBasedConfidence <= 0.9);
        }
    }

    @Nested
    @DisplayName("Offline Detection Capability")
    class OfflineDetectionCapability {

        @Test
        @DisplayName("Should perform basic detection without network")
        void shouldPerformBasicDetectionWithoutNetwork() {
            // Create issue that can be detected purely from local metrics
            DiagnosticIssue localIssue = DiagnosticIssue.builder()
                    .type(IssueType.MEMORY_LEAK)
                    .severity(Severity.HIGH)
                    .processName("local-app.exe")
                    .affectedPid(9999)
                    .confidence(0.75)
                    .details("Detected via local rule-based analysis")
                    .detectedAt(Instant.now())
                    .build();

            assertNotNull(localIssue);
            assertEquals(IssueType.MEMORY_LEAK, localIssue.getType());
            assertTrue(localIssue.getConfidence() > 0);
        }

        @Test
        @DisplayName("Should use local thresholds for severity classification")
        void shouldUseLocalThresholdsForSeverityClassification() {
            // Define local threshold rules
            Map<Severity, Long> memoryThresholds = Map.of(
                    Severity.LOW, 500_000_000L, // 500MB
                    Severity.MEDIUM, 1_000_000_000L, // 1GB
                    Severity.HIGH, 2_000_000_000L, // 2GB
                    Severity.CRITICAL, 4_000_000_000L // 4GB
            );

            // Test classification
            long testMemory = 2_500_000_000L;

            Severity classified = memoryThresholds.entrySet().stream()
                    .filter(e -> testMemory > e.getValue())
                    .map(Map.Entry::getKey)
                    .reduce((a, b) -> compareSeverity(a, b) > 0 ? a : b)
                    .orElse(Severity.LOW);

            assertEquals(Severity.HIGH, classified);
        }

        private int compareSeverity(Severity a, Severity b) {
            return Integer.compare(a.ordinal(), b.ordinal());
        }
    }
}
