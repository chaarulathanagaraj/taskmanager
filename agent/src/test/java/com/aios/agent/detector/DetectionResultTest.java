package com.aios.agent.detector;

import com.aios.shared.enums.IssueType;
import com.aios.shared.enums.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DetectionResult.
 * 
 * Tests the detection result builder pattern, evidence handling, and value
 * extraction.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@DisplayName("DetectionResult Tests")
class DetectionResultTest {

    @Nested
    @DisplayName("Builder Pattern")
    class BuilderPatternTests {

        @Test
        @DisplayName("Should create result with all fields")
        void shouldCreateResultWithAllFields() {
            DetectionResult result = DetectionResult.builder()
                    .type(IssueType.MEMORY_LEAK)
                    .severity(Severity.HIGH)
                    .confidence(0.85)
                    .affectedPid(1234)
                    .processName("chrome.exe")
                    .description("Memory leak detected in chrome.exe")
                    .build();

            assertEquals(IssueType.MEMORY_LEAK, result.getType());
            assertEquals(Severity.HIGH, result.getSeverity());
            assertEquals(0.85, result.getConfidence(), 0.001);
            assertEquals(1234, result.getAffectedPid());
            assertEquals("chrome.exe", result.getProcessName());
            assertEquals("Memory leak detected in chrome.exe", result.getDescription());
        }

        @Test
        @DisplayName("Should create result with minimum fields")
        void shouldCreateResultWithMinimumFields() {
            DetectionResult result = DetectionResult.builder()
                    .type(IssueType.RESOURCE_HOG)
                    .affectedPid(5678)
                    .build();

            assertEquals(IssueType.RESOURCE_HOG, result.getType());
            assertEquals(5678, result.getAffectedPid());
            assertNull(result.getSeverity());
            assertNull(result.getProcessName());
        }

        @Test
        @DisplayName("Should initialize evidence map by default")
        void shouldInitializeEvidenceMapByDefault() {
            DetectionResult result = DetectionResult.builder()
                    .type(IssueType.HUNG_PROCESS)
                    .affectedPid(9999)
                    .build();

            assertNotNull(result.getEvidence());
            assertTrue(result.getEvidence().isEmpty());
        }

        @Test
        @DisplayName("Should accept pre-built evidence map")
        void shouldAcceptPreBuiltEvidenceMap() {
            Map<String, Object> evidence = new HashMap<>();
            evidence.put("key1", "value1");
            evidence.put("key2", 42);

            DetectionResult result = DetectionResult.builder()
                    .type(IssueType.MEMORY_LEAK)
                    .affectedPid(1234)
                    .evidence(evidence)
                    .build();

            assertEquals("value1", result.getEvidence("key1"));
            assertEquals(42, result.getEvidence("key2"));
        }
    }

    @Nested
    @DisplayName("Evidence Handling")
    class EvidenceHandlingTests {

        @Test
        @DisplayName("Should add evidence via fluent API")
        void shouldAddEvidenceFluently() {
            DetectionResult result = DetectionResult.builder()
                    .type(IssueType.HUNG_PROCESS)
                    .affectedPid(1234)
                    .build()
                    .addEvidence("growthRate", 5.5)
                    .addEvidence("currentValue", 1024L)
                    .addEvidence("trend", "increasing");

            assertEquals(5.5, result.getEvidence("growthRate"));
            assertEquals(1024L, result.getEvidence("currentValue"));
            assertEquals("increasing", result.getEvidence("trend"));
        }

        @Test
        @DisplayName("Should return this from addEvidence for chaining")
        void shouldReturnThisFromAddEvidence() {
            DetectionResult result = DetectionResult.builder()
                    .type(IssueType.RESOURCE_HOG)
                    .affectedPid(5678)
                    .build();

            DetectionResult sameResult = result.addEvidence("key", "value");

            assertSame(result, sameResult);
        }

        @Test
        @DisplayName("Should get evidence by key")
        void shouldGetEvidenceByKey() {
            DetectionResult result = DetectionResult.builder()
                    .type(IssueType.HUNG_PROCESS)
                    .affectedPid(9999)
                    .build()
                    .addEvidence("threadCount", 100)
                    .addEvidence("blockedThreads", 50);

            assertEquals(100, result.getEvidence("threadCount"));
            assertEquals(50, result.getEvidence("blockedThreads"));
        }

        @Test
        @DisplayName("Should return null for non-existent evidence key")
        void shouldReturnNullForNonExistentKey() {
            DetectionResult result = DetectionResult.builder()
                    .type(IssueType.MEMORY_LEAK)
                    .affectedPid(1234)
                    .build();

            assertNull(result.getEvidence("nonExistentKey"));
        }

        @Test
        @DisplayName("Should overwrite existing evidence value")
        void shouldOverwriteExistingEvidence() {
            DetectionResult result = DetectionResult.builder()
                    .type(IssueType.MEMORY_LEAK)
                    .affectedPid(1234)
                    .build()
                    .addEvidence("growthRate", 5.0)
                    .addEvidence("growthRate", 10.0);

            assertEquals(10.0, result.getEvidence("growthRate"));
        }
    }

    @Nested
    @DisplayName("Typed Evidence Retrieval")
    class TypedEvidenceRetrievalTests {

        @Test
        @DisplayName("Should get evidence as double")
        void shouldGetEvidenceAsDouble() {
            DetectionResult result = DetectionResult.builder()
                    .type(IssueType.MEMORY_LEAK)
                    .affectedPid(1234)
                    .build()
                    .addEvidence("growthRate", 5.5);

            double value = result.getEvidenceAsDouble("growthRate", 0.0);
            assertEquals(5.5, value, 0.001);
        }

        @Test
        @DisplayName("Should return default for non-existent double evidence")
        void shouldReturnDefaultForNonExistentDouble() {
            DetectionResult result = DetectionResult.builder()
                    .type(IssueType.MEMORY_LEAK)
                    .affectedPid(1234)
                    .build();

            double value = result.getEvidenceAsDouble("nonExistent", 42.0);
            assertEquals(42.0, value, 0.001);
        }

        @Test
        @DisplayName("Should get evidence as double from Number")
        void shouldGetEvidenceAsDoubleFromNumber() {
            DetectionResult result = DetectionResult.builder()
                    .type(IssueType.MEMORY_LEAK)
                    .affectedPid(1234)
                    .build()
                    .addEvidence("memoryBytes", 1073741824L);

            double value = result.getEvidenceAsDouble("memoryBytes", 0.0);
            assertEquals(1073741824.0, value, 0.001);
        }

        @Test
        @DisplayName("Should return default for non-matching type")
        void shouldReturnDefaultForNonMatchingType() {
            DetectionResult result = DetectionResult.builder()
                    .type(IssueType.MEMORY_LEAK)
                    .affectedPid(1234)
                    .build()
                    .addEvidence("stringValue", "not a number");

            double value = result.getEvidenceAsDouble("stringValue", 999.0);
            assertEquals(999.0, value, 0.001);
        }
    }

    @Nested
    @DisplayName("Confidence Ranges")
    class ConfidenceRangesTests {

        @Test
        @DisplayName("Should support minimum confidence value")
        void shouldSupportMinimumConfidence() {
            DetectionResult result = DetectionResult.builder()
                    .type(IssueType.MEMORY_LEAK)
                    .affectedPid(1234)
                    .confidence(0.0)
                    .build();

            assertEquals(0.0, result.getConfidence(), 0.001);
        }

        @Test
        @DisplayName("Should support maximum confidence value")
        void shouldSupportMaximumConfidence() {
            DetectionResult result = DetectionResult.builder()
                    .type(IssueType.MEMORY_LEAK)
                    .affectedPid(1234)
                    .confidence(1.0)
                    .build();

            assertEquals(1.0, result.getConfidence(), 0.001);
        }

        @Test
        @DisplayName("Should support mid-range confidence values")
        void shouldSupportMidRangeConfidence() {
            DetectionResult result = DetectionResult.builder()
                    .type(IssueType.MEMORY_LEAK)
                    .affectedPid(1234)
                    .confidence(0.75)
                    .build();

            assertEquals(0.75, result.getConfidence(), 0.001);
        }
    }

    @Nested
    @DisplayName("Severity Levels")
    class SeverityLevelsTests {

        @Test
        @DisplayName("Should support LOW severity")
        void shouldSupportLowSeverity() {
            DetectionResult result = DetectionResult.builder()
                    .type(IssueType.MEMORY_LEAK)
                    .severity(Severity.LOW)
                    .affectedPid(1234)
                    .build();

            assertEquals(Severity.LOW, result.getSeverity());
        }

        @Test
        @DisplayName("Should support MEDIUM severity")
        void shouldSupportMediumSeverity() {
            DetectionResult result = DetectionResult.builder()
                    .type(IssueType.MEMORY_LEAK)
                    .severity(Severity.MEDIUM)
                    .affectedPid(1234)
                    .build();

            assertEquals(Severity.MEDIUM, result.getSeverity());
        }

        @Test
        @DisplayName("Should support HIGH severity")
        void shouldSupportHighSeverity() {
            DetectionResult result = DetectionResult.builder()
                    .type(IssueType.MEMORY_LEAK)
                    .severity(Severity.HIGH)
                    .affectedPid(1234)
                    .build();

            assertEquals(Severity.HIGH, result.getSeverity());
        }

        @Test
        @DisplayName("Should support CRITICAL severity")
        void shouldSupportCriticalSeverity() {
            DetectionResult result = DetectionResult.builder()
                    .type(IssueType.HUNG_PROCESS)
                    .severity(Severity.CRITICAL)
                    .affectedPid(1234)
                    .build();

            assertEquals(Severity.CRITICAL, result.getSeverity());
        }
    }

    @Nested
    @DisplayName("Issue Types")
    class IssueTypesTests {

        @Test
        @DisplayName("Should support MEMORY_LEAK type")
        void shouldSupportMemoryLeakType() {
            DetectionResult result = DetectionResult.builder()
                    .type(IssueType.MEMORY_LEAK)
                    .affectedPid(1234)
                    .build();

            assertEquals(IssueType.MEMORY_LEAK, result.getType());
        }

        @Test
        @DisplayName("Should support RESOURCE_HOG type")
        void shouldSupportResourceHogType() {
            DetectionResult result = DetectionResult.builder()
                    .type(IssueType.RESOURCE_HOG)
                    .affectedPid(1234)
                    .build();

            assertEquals(IssueType.RESOURCE_HOG, result.getType());
        }

        @Test
        @DisplayName("Should support HUNG_PROCESS type")
        void shouldSupportHungProcessType() {
            DetectionResult result = DetectionResult.builder()
                    .type(IssueType.HUNG_PROCESS)
                    .affectedPid(1234)
                    .build();

            assertEquals(IssueType.HUNG_PROCESS, result.getType());
        }

        @Test
        @DisplayName("Should support THREAD_EXPLOSION type")
        void shouldSupportThreadExplosionType() {
            DetectionResult result = DetectionResult.builder()
                    .type(IssueType.THREAD_EXPLOSION)
                    .affectedPid(1234)
                    .build();

            assertEquals(IssueType.THREAD_EXPLOSION, result.getType());
        }
    }

    @Nested
    @DisplayName("Description")
    class DescriptionTests {

        @Test
        @DisplayName("Should store description")
        void shouldStoreDescription() {
            String description = "Process chrome.exe (PID: 1234) showing memory growth of 5.5 MB/min over 10 minutes";

            DetectionResult result = DetectionResult.builder()
                    .type(IssueType.MEMORY_LEAK)
                    .affectedPid(1234)
                    .description(description)
                    .build();

            assertEquals(description, result.getDescription());
        }

        @Test
        @DisplayName("Should allow null description")
        void shouldAllowNullDescription() {
            DetectionResult result = DetectionResult.builder()
                    .type(IssueType.MEMORY_LEAK)
                    .affectedPid(1234)
                    .description(null)
                    .build();

            assertNull(result.getDescription());
        }

        @Test
        @DisplayName("Should allow empty description")
        void shouldAllowEmptyDescription() {
            DetectionResult result = DetectionResult.builder()
                    .type(IssueType.MEMORY_LEAK)
                    .affectedPid(1234)
                    .description("")
                    .build();

            assertEquals("", result.getDescription());
        }
    }
}
