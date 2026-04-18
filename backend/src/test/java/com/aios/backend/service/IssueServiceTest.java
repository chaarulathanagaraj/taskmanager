package com.aios.backend.service;

import com.aios.backend.event.IssueCreatedEvent;
import com.aios.backend.dto.IssueResolutionSummary;
import com.aios.backend.model.IssueEntity;
import com.aios.backend.repository.IssueRepository;
import com.aios.shared.dto.DiagnosticIssue;
import com.aios.shared.enums.IssueType;
import com.aios.shared.enums.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IssueService.
 * 
 * Tests issue creation, retrieval, resolution, and event publishing.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("IssueService Tests")
class IssueServiceTest {

        @Mock
        private IssueRepository issueRepository;

        @Mock
        private WebSocketBroadcaster broadcaster;

        @Mock
        private ApplicationEventPublisher eventPublisher;

        @Mock
        private PerformanceMetrics performanceMetrics;

        @Captor
        private ArgumentCaptor<IssueEntity> issueCaptor;

        @Captor
        private ArgumentCaptor<IssueCreatedEvent> eventCaptor;

        private IssueService service;

        @BeforeEach
        void setUp() {
                service = new IssueService(issueRepository, broadcaster, eventPublisher, performanceMetrics);
                when(issueRepository.findFirstByTypeAndAffectedPidAndResolvedFalseOrderByDetectedAtDesc(any(), any()))
                                .thenReturn(Optional.empty());
        }

        @Nested
        @DisplayName("Issue Creation")
        class IssueCreationTests {

                @Test
                @DisplayName("Should create issue from DiagnosticIssue DTO")
                void shouldCreateIssue() {
                        DiagnosticIssue dto = DiagnosticIssue.builder()
                                        .type(IssueType.MEMORY_LEAK)
                                        .severity(Severity.HIGH)
                                        .processName("chrome.exe")
                                        .affectedPid(1234)
                                        .confidence(0.85)
                                        .details("Memory leak detected")
                                        .detectedAt(Instant.now())
                                        .build();

                        IssueEntity savedEntity = IssueEntity.builder()
                                        .id(1L)
                                        .type(IssueType.MEMORY_LEAK)
                                        .severity(Severity.HIGH)
                                        .processName("chrome.exe")
                                        .affectedPid(1234)
                                        .confidence(0.85)
                                        .details("Memory leak detected")
                                        .detectedAt(Instant.now())
                                        .resolved(false)
                                        .build();

                        when(issueRepository.save(any(IssueEntity.class))).thenReturn(savedEntity);

                        IssueEntity result = service.createIssue(dto);

                        assertNotNull(result);
                        assertEquals(1L, result.getId());
                        assertEquals(IssueType.MEMORY_LEAK, result.getType());
                        verify(issueRepository).save(any(IssueEntity.class));
                }

                @Test
                @DisplayName("Should record metrics on issue creation")
                void shouldRecordMetricsOnCreation() {
                        DiagnosticIssue dto = DiagnosticIssue.builder()
                                        .type(IssueType.RESOURCE_HOG)
                                        .severity(Severity.MEDIUM)
                                        .processName("app.exe")
                                        .affectedPid(5678)
                                        .confidence(0.75)
                                        .details("Resource hog detected")
                                        .detectedAt(Instant.now())
                                        .build();

                        IssueEntity savedEntity = IssueEntity.builder()
                                        .id(2L)
                                        .type(IssueType.RESOURCE_HOG)
                                        .severity(Severity.MEDIUM)
                                        .confidence(0.75)
                                        .resolved(false)
                                        .build();

                        when(issueRepository.save(any())).thenReturn(savedEntity);

                        service.createIssue(dto);

                        verify(performanceMetrics).recordIssueDetected(IssueType.RESOURCE_HOG, Severity.MEDIUM);
                        verify(performanceMetrics).recordConfidence(0.75);
                        verify(performanceMetrics).incrementActiveIssues();
                }

                @Test
                @DisplayName("Should broadcast new issue via WebSocket")
                void shouldBroadcastNewIssue() {
                        DiagnosticIssue dto = DiagnosticIssue.builder()
                                        .type(IssueType.HUNG_PROCESS)
                                        .severity(Severity.CRITICAL)
                                        .processName("server.exe")
                                        .affectedPid(9999)
                                        .confidence(0.92)
                                        .details("Hung process detected")
                                        .detectedAt(Instant.now())
                                        .build();

                        IssueEntity savedEntity = IssueEntity.builder()
                                        .id(3L)
                                        .type(IssueType.HUNG_PROCESS)
                                        .severity(Severity.CRITICAL)
                                        .processName("server.exe")
                                        .affectedPid(9999)
                                        .confidence(0.92)
                                        .resolved(false)
                                        .build();

                        when(issueRepository.save(any())).thenReturn(savedEntity);

                        service.createIssue(dto);

                        verify(broadcaster).broadcastNewIssue(issueCaptor.capture());
                        assertEquals(IssueType.HUNG_PROCESS, issueCaptor.getValue().getType());
                }

                @Test
                @DisplayName("Should publish IssueCreatedEvent")
                void shouldPublishEvent() {
                        DiagnosticIssue dto = DiagnosticIssue.builder()
                                        .type(IssueType.MEMORY_LEAK)
                                        .severity(Severity.LOW)
                                        .processName("notepad.exe")
                                        .affectedPid(1111)
                                        .confidence(0.55)
                                        .details("Potential memory leak")
                                        .detectedAt(Instant.now())
                                        .build();

                        IssueEntity savedEntity = IssueEntity.builder()
                                        .id(4L)
                                        .type(IssueType.MEMORY_LEAK)
                                        .severity(Severity.LOW)
                                        .confidence(0.55)
                                        .resolved(false)
                                        .build();

                        when(issueRepository.save(any())).thenReturn(savedEntity);

                        service.createIssue(dto);

                        verify(eventPublisher).publishEvent(eventCaptor.capture());
                        assertEquals(4L, eventCaptor.getValue().getIssue().getId());
                }

                @Test
                @DisplayName("Should broadcast alert for high-priority issues")
                void shouldBroadcastAlertForHighPriority() {
                        DiagnosticIssue dto = DiagnosticIssue.builder()
                                        .type(IssueType.HUNG_PROCESS)
                                        .severity(Severity.CRITICAL)
                                        .processName("critical-service.exe")
                                        .affectedPid(8888)
                                        .confidence(0.98)
                                        .details("Critical hung process")
                                        .detectedAt(Instant.now())
                                        .build();

                        // highPriority is computed, not a field - use a mock
                        IssueEntity savedEntity = mock(IssueEntity.class);
                        when(savedEntity.getId()).thenReturn(5L);
                        when(savedEntity.getType()).thenReturn(IssueType.HUNG_PROCESS);
                        when(savedEntity.getSeverity()).thenReturn(Severity.CRITICAL);
                        when(savedEntity.getProcessName()).thenReturn("critical-service.exe");
                        when(savedEntity.getAffectedPid()).thenReturn(8888);
                        when(savedEntity.isHighPriority()).thenReturn(true);
                        when(savedEntity.getResolved()).thenReturn(false);

                        when(issueRepository.save(any())).thenReturn(savedEntity);

                        service.createIssue(dto);

                        verify(broadcaster).broadcastAlert(anyString(), eq("CRITICAL"));
                }

                @Test
                @DisplayName("Should update existing active issue instead of creating duplicate")
                void shouldUpdateExistingIssueWithoutCreatingDuplicate() {
                        DiagnosticIssue dto = DiagnosticIssue.builder()
                                        .type(IssueType.MEMORY_LEAK)
                                        .severity(Severity.HIGH)
                                        .processName("chrome.exe")
                                        .affectedPid(1234)
                                        .confidence(0.90)
                                        .details("Memory usage still increasing")
                                        .detectedAt(Instant.now())
                                        .build();

                        IssueEntity existing = IssueEntity.builder()
                                        .id(9L)
                                        .type(IssueType.MEMORY_LEAK)
                                        .severity(Severity.MEDIUM)
                                        .processName("chrome.exe")
                                        .affectedPid(1234)
                                        .confidence(0.72)
                                        .details("Possible leak")
                                        .detectedAt(Instant.now().minusSeconds(180))
                                        .lastSeenAt(Instant.now().minusSeconds(20))
                                        .occurrenceCount(2)
                                        .resolved(false)
                                        .build();

                        when(issueRepository.findFirstByTypeAndAffectedPidAndResolvedFalseOrderByDetectedAtDesc(
                                        IssueType.MEMORY_LEAK, 1234)).thenReturn(Optional.of(existing));
                        when(issueRepository.save(any(IssueEntity.class))).thenAnswer(inv -> inv.getArgument(0));

                        IssueEntity result = service.createIssue(dto);

                        assertNotNull(result);
                        assertEquals(9L, result.getId());
                        assertEquals(0.90, result.getConfidence());
                        assertEquals(Severity.HIGH, result.getSeverity());

                        verify(issueRepository, never()).findRecentSimilarIssues(any(), any(), any());
                        verify(issueRepository, times(1)).save(existing);
                        verify(performanceMetrics, never()).recordIssueDetected(any(), any());
                        verify(broadcaster, never()).broadcastNewIssue(any());
                        verify(eventPublisher, never()).publishEvent(any(IssueCreatedEvent.class));
                }

                @Test
                @DisplayName("Should raise persistence alert when issue lasts over ten minutes")
                void shouldEscalatePersistentIssueAfterTenMinutes() {
                        DiagnosticIssue dto = DiagnosticIssue.builder()
                                        .type(IssueType.MEMORY_LEAK)
                                        .severity(Severity.MEDIUM)
                                        .processName("chrome.exe")
                                        .affectedPid(2222)
                                        .confidence(0.70)
                                        .details("Still leaking")
                                        .detectedAt(Instant.now())
                                        .build();

                        IssueEntity existing = IssueEntity.builder()
                                        .id(10L)
                                        .type(IssueType.MEMORY_LEAK)
                                        .severity(Severity.MEDIUM)
                                        .processName("chrome.exe")
                                        .affectedPid(2222)
                                        .confidence(0.70)
                                        .details("Still leaking")
                                        .detectedAt(Instant.now().minusSeconds(620))
                                        .lastSeenAt(Instant.now().minusSeconds(12))
                                        .lastPersistenceAlertAt(null)
                                        .occurrenceCount(8)
                                        .resolved(false)
                                        .build();

                        when(issueRepository.findFirstByTypeAndAffectedPidAndResolvedFalseOrderByDetectedAtDesc(
                                        IssueType.MEMORY_LEAK, 2222)).thenReturn(Optional.of(existing));
                        when(issueRepository.save(any(IssueEntity.class))).thenAnswer(inv -> inv.getArgument(0));

                        service.createIssue(dto);

                        assertNotNull(existing.getLastPersistenceAlertAt());
                        assertEquals(Severity.HIGH, existing.getSeverity());
                        verify(broadcaster).broadcastAlert(contains("Persistent issue"), eq("HIGH"));
                }
        }

        @Nested
        @DisplayName("Issue Retrieval")
        class IssueRetrievalTests {

                @Test
                @DisplayName("Should get all issues")
                void shouldGetAllIssues() {
                        List<IssueEntity> issues = List.of(
                                        IssueEntity.builder().id(1L).type(IssueType.MEMORY_LEAK).build(),
                                        IssueEntity.builder().id(2L).type(IssueType.RESOURCE_HOG).build());
                        when(issueRepository.findAll()).thenReturn(issues);

                        List<IssueEntity> result = service.getAllIssues();

                        assertEquals(2, result.size());
                        verify(issueRepository).findAll();
                }

                @Test
                @DisplayName("Should get active issues")
                void shouldGetActiveIssues() {
                        List<IssueEntity> activeIssues = List.of(
                                        IssueEntity.builder().id(1L).resolved(false).build());
                        when(issueRepository.findByResolvedFalseOrderBySeverityDescDetectedAtDesc())
                                        .thenReturn(activeIssues);

                        List<IssueEntity> result = service.getActiveIssues();

                        assertEquals(1, result.size());
                        verify(issueRepository).findByResolvedFalseOrderBySeverityDescDetectedAtDesc();
                }

                @Test
                @DisplayName("Should get high-priority issues")
                void shouldGetHighPriorityIssues() {
                        List<IssueEntity> highPriority = List.of(
                                        IssueEntity.builder().id(1L).severity(Severity.CRITICAL).build());
                        when(issueRepository.findHighPriorityActiveIssues()).thenReturn(highPriority);

                        List<IssueEntity> result = service.getHighPriorityIssues();

                        assertEquals(1, result.size());
                        verify(issueRepository).findHighPriorityActiveIssues();
                }

                @Test
                @DisplayName("Should get issue by ID")
                void shouldGetIssueById() {
                        IssueEntity issue = IssueEntity.builder().id(42L).build();
                        when(issueRepository.findById(42L)).thenReturn(Optional.of(issue));

                        Optional<IssueEntity> result = service.getIssueById(42L);

                        assertTrue(result.isPresent());
                        assertEquals(42L, result.get().getId());
                }

                @Test
                @DisplayName("Should return empty for non-existent ID")
                void shouldReturnEmptyForNonExistentId() {
                        when(issueRepository.findById(999L)).thenReturn(Optional.empty());

                        Optional<IssueEntity> result = service.getIssueById(999L);

                        assertTrue(result.isEmpty());
                }

                @Test
                @DisplayName("Should get issues by type")
                void shouldGetIssuesByType() {
                        List<IssueEntity> memoryLeaks = List.of(
                                        IssueEntity.builder().id(1L).type(IssueType.MEMORY_LEAK).build());
                        when(issueRepository.findByTypeAndResolvedFalseOrderByDetectedAtDesc(IssueType.MEMORY_LEAK))
                                        .thenReturn(memoryLeaks);

                        List<IssueEntity> result = service.getIssuesByType(IssueType.MEMORY_LEAK);

                        assertEquals(1, result.size());
                        assertEquals(IssueType.MEMORY_LEAK, result.get(0).getType());
                }

                @Test
                @DisplayName("Should get issues by severity")
                void shouldGetIssuesBySeverity() {
                        List<IssueEntity> critical = List.of(
                                        IssueEntity.builder().id(1L).severity(Severity.CRITICAL).build());
                        when(issueRepository.findBySeverityAndResolvedFalseOrderByDetectedAtDesc(Severity.CRITICAL))
                                        .thenReturn(critical);

                        List<IssueEntity> result = service.getIssuesBySeverity(Severity.CRITICAL);

                        assertEquals(1, result.size());
                        assertEquals(Severity.CRITICAL, result.get(0).getSeverity());
                }

                @Test
                @DisplayName("Should get issues by process ID")
                void shouldGetIssuesByProcess() {
                        List<IssueEntity> processIssues = List.of(
                                        IssueEntity.builder().id(1L).affectedPid(1234).build());
                        when(issueRepository.findByAffectedPidAndResolvedFalseOrderByDetectedAtDesc(1234))
                                        .thenReturn(processIssues);

                        List<IssueEntity> result = service.getIssuesByProcess(1234);

                        assertEquals(1, result.size());
                        assertEquals(1234, result.get(0).getAffectedPid());
                }

                @Test
                @DisplayName("Should get issues eligible for remediation")
                void shouldGetEligibleForRemediation() {
                        List<IssueEntity> eligible = List.of(
                                        IssueEntity.builder().id(1L).confidence(0.95).build());
                        when(issueRepository.findEligibleForAutoRemediation(0.85)).thenReturn(eligible);

                        List<IssueEntity> result = service.getEligibleForRemediation(0.85);

                        assertEquals(1, result.size());
                        verify(issueRepository).findEligibleForAutoRemediation(0.85);
                }
        }

        @Nested
        @DisplayName("Issue Resolution")
        class IssueResolutionTests {

                @Test
                @DisplayName("Should resolve existing issue")
                void shouldResolveExistingIssue() {
                        IssueEntity issue = IssueEntity.builder()
                                        .id(1L)
                                        .type(IssueType.MEMORY_LEAK)
                                        .resolved(false)
                                        .build();

                        when(issueRepository.findById(1L)).thenReturn(Optional.of(issue));
                        when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                        IssueResolutionSummary result = service.resolveIssue(1L);

                        assertNotNull(result);
                        assertTrue(result.getResolved());
                        verify(issueRepository).save(issueCaptor.capture());
                        assertTrue(issueCaptor.getValue().getResolved());
                }

                @Test
                @DisplayName("Should broadcast resolution via WebSocket")
                void shouldBroadcastResolution() {
                        IssueEntity issue = IssueEntity.builder()
                                        .id(1L)
                                        .resolved(false)
                                        .build();

                        when(issueRepository.findById(1L)).thenReturn(Optional.of(issue));
                        when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                        service.resolveIssue(1L);

                        verify(broadcaster).broadcastIssueResolved(any(IssueResolutionSummary.class));
                }

                @Test
                @DisplayName("Should return false for non-existent issue")
                void shouldReturnFalseForNonExistent() {
                        when(issueRepository.findById(999L)).thenReturn(Optional.empty());

                        IssueResolutionSummary result = service.resolveIssue(999L);

                        assertNull(result);
                        verify(issueRepository, never()).save(any());
                }

                @Test
                @DisplayName("Should handle already resolved issue")
                void shouldHandleAlreadyResolved() {
                        IssueEntity resolvedIssue = IssueEntity.builder()
                                        .id(1L)
                                        .resolved(true)
                                        .build();

                        when(issueRepository.findById(1L)).thenReturn(Optional.of(resolvedIssue));

                        IssueResolutionSummary result = service.resolveIssue(1L);

                        assertNotNull(result);
                        assertTrue(result.getResolved());
                        verify(issueRepository, never()).save(any());
                }
        }

        @Nested
        @DisplayName("Edge Cases")
        class EdgeCaseTests {

                @Test
                @DisplayName("Should handle empty issue list")
                void shouldHandleEmptyList() {
                        when(issueRepository.findAll()).thenReturn(Collections.emptyList());

                        List<IssueEntity> result = service.getAllIssues();

                        assertTrue(result.isEmpty());
                }

                @Test
                @DisplayName("Should handle null confidence in DTO")
                void shouldHandleNullConfidence() {
                        DiagnosticIssue dto = DiagnosticIssue.builder()
                                        .type(IssueType.MEMORY_LEAK)
                                        .severity(Severity.MEDIUM)
                                        .processName("test.exe")
                                        .affectedPid(1234)
                                        .confidence(0.0)
                                        .detectedAt(Instant.now())
                                        .build();

                        IssueEntity savedEntity = IssueEntity.builder()
                                        .id(1L)
                                        .type(IssueType.MEMORY_LEAK)
                                        .confidence(0.0)
                                        .resolved(false)
                                        .build();

                        when(issueRepository.save(any())).thenReturn(savedEntity);

                        IssueEntity result = service.createIssue(dto);

                        assertNotNull(result);
                        verify(performanceMetrics).recordConfidence(0.0);
                }
        }
}
