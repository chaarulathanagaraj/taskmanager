package com.aios.backend.service;

import com.aios.backend.model.ActionEntity;
import com.aios.backend.model.IssueEntity;
import com.aios.backend.repository.ActionRepository;
import com.aios.backend.repository.IssueRepository;
import com.aios.shared.dto.RemediationActionLog;
import com.aios.shared.enums.ActionStatus;
import com.aios.shared.enums.ActionType;
import com.aios.shared.enums.SafetyLevel;
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

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ActionService.
 * 
 * Tests action logging, retrieval, statistics, and WebSocket broadcasting.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ActionService Tests")
class ActionServiceTest {

    @Mock
    private ActionRepository actionRepository;

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private WebSocketBroadcaster broadcaster;

    @Mock
    private PerformanceMetrics performanceMetrics;

    @Captor
    private ArgumentCaptor<ActionEntity> actionCaptor;

    private ActionService service;

    @BeforeEach
    void setUp() {
        service = new ActionService(actionRepository, issueRepository, broadcaster, performanceMetrics);
    }

    @Nested
    @DisplayName("Action Logging")
    class ActionLoggingTests {

        @Test
        @DisplayName("Should log action from RemediationActionLog DTO")
        void shouldLogAction() {
            RemediationActionLog dto = RemediationActionLog.builder()
                    .actionType(ActionType.KILL_PROCESS)
                    .status(ActionStatus.SUCCESS)
                    .targetPid(1234)
                    .targetName("chrome.exe")
                    .safetyLevel(SafetyLevel.MEDIUM)
                    .dryRun(false)
                    .executedAt(Instant.now())
                    .result("Process terminated")
                    .build();

            ActionEntity savedEntity = ActionEntity.builder()
                    .id(1L)
                    .actionType(ActionType.KILL_PROCESS)
                    .status(ActionStatus.SUCCESS)
                    .targetPid(1234)
                    .targetName("chrome.exe")
                    .safetyLevel(SafetyLevel.MEDIUM)
                    .dryRun(false)
                    .build();

            when(actionRepository.save(any(ActionEntity.class))).thenReturn(savedEntity);

            ActionEntity result = service.logAction(dto);

            assertNotNull(result);
            assertEquals(1L, result.getId());
            verify(actionRepository).save(any(ActionEntity.class));
        }

        @Test
        @DisplayName("Should record success metrics on successful action")
        void shouldRecordSuccessMetrics() {
            RemediationActionLog dto = RemediationActionLog.builder()
                    .actionType(ActionType.REDUCE_PRIORITY)
                    .status(ActionStatus.SUCCESS)
                    .targetPid(5678)
                    .targetName("app.exe")
                    .safetyLevel(SafetyLevel.LOW)
                    .dryRun(false)
                    .executedAt(Instant.now())
                    .build();

            ActionEntity savedEntity = ActionEntity.builder()
                    .id(2L)
                    .status(ActionStatus.SUCCESS)
                    .safetyLevel(SafetyLevel.LOW)
                    .build();

            when(actionRepository.save(any())).thenReturn(savedEntity);

            service.logAction(dto);

            verify(performanceMetrics).recordRemediationSuccess();
            verify(performanceMetrics, never()).recordRemediationFailure();
        }

        @Test
        @DisplayName("Should record failure metrics on failed action")
        void shouldRecordFailureMetrics() {
            RemediationActionLog dto = RemediationActionLog.builder()
                    .actionType(ActionType.KILL_PROCESS)
                    .status(ActionStatus.FAILED)
                    .targetPid(9999)
                    .targetName("protected.exe")
                    .safetyLevel(SafetyLevel.HIGH)
                    .dryRun(false)
                    .result("Access denied")
                    .executedAt(Instant.now())
                    .build();

            ActionEntity savedEntity = ActionEntity.builder()
                    .id(3L)
                    .status(ActionStatus.FAILED)
                    .safetyLevel(SafetyLevel.HIGH)
                    .build();

            when(actionRepository.save(any())).thenReturn(savedEntity);

            service.logAction(dto);

            verify(performanceMetrics).recordRemediationFailure();
            verify(performanceMetrics, never()).recordRemediationSuccess();
        }

        @Test
        @DisplayName("Should broadcast action via WebSocket")
        void shouldBroadcastAction() {
            RemediationActionLog dto = RemediationActionLog.builder()
                    .actionType(ActionType.TRIM_WORKING_SET)
                    .status(ActionStatus.SUCCESS)
                    .targetPid(1111)
                    .targetName("browser.exe")
                    .safetyLevel(SafetyLevel.LOW)
                    .dryRun(true)
                    .executedAt(Instant.now())
                    .build();

            ActionEntity savedEntity = ActionEntity.builder()
                    .id(4L)
                    .actionType(ActionType.TRIM_WORKING_SET)
                    .status(ActionStatus.SUCCESS)
                    .safetyLevel(SafetyLevel.LOW)
                    .build();

            when(actionRepository.save(any())).thenReturn(savedEntity);

            service.logAction(dto);

            verify(broadcaster).broadcastAction(actionCaptor.capture());
            assertEquals(ActionType.TRIM_WORKING_SET, actionCaptor.getValue().getActionType());
        }

        @Test
        @DisplayName("Should link action to issue when issueId provided")
        void shouldLinkToIssue() {
            IssueEntity issue = IssueEntity.builder().id(42L).build();
            when(issueRepository.findById(42L)).thenReturn(Optional.of(issue));

            RemediationActionLog dto = RemediationActionLog.builder()
                    .actionType(ActionType.KILL_PROCESS)
                    .status(ActionStatus.SUCCESS)
                    .targetPid(1234)
                    .targetName("leaky.exe")
                    .safetyLevel(SafetyLevel.MEDIUM)
                    .issueId(42L)
                    .dryRun(false)
                    .executedAt(Instant.now())
                    .build();

            ActionEntity savedEntity = ActionEntity.builder()
                    .id(5L)
                    .issue(issue)
                    .safetyLevel(SafetyLevel.MEDIUM)
                    .build();

            when(actionRepository.save(any())).thenReturn(savedEntity);

            service.logAction(dto);

            verify(issueRepository).findById(42L);
            verify(actionRepository).save(actionCaptor.capture());
            assertEquals(issue, actionCaptor.getValue().getIssue());
        }

        @Test
        @DisplayName("Should broadcast alert for actions requiring manual review")
        void shouldBroadcastAlertForManualReview() {
            RemediationActionLog dto = RemediationActionLog.builder()
                    .actionType(ActionType.KILL_PROCESS)
                    .status(ActionStatus.FAILED)
                    .targetPid(7777)
                    .targetName("critical.exe")
                    .safetyLevel(SafetyLevel.CRITICAL)
                    .dryRun(false)
                    .result("Access denied")
                    .executedAt(Instant.now())
                    .build();

            // Use mock because requiresManualReview() is a computed method
            ActionEntity savedEntity = mock(ActionEntity.class);
            when(savedEntity.getId()).thenReturn(6L);
            when(savedEntity.getActionType()).thenReturn(ActionType.KILL_PROCESS);
            when(savedEntity.getStatus()).thenReturn(ActionStatus.FAILED);
            when(savedEntity.getTargetPid()).thenReturn(7777);
            when(savedEntity.getSafetyLevel()).thenReturn(SafetyLevel.CRITICAL);
            when(savedEntity.requiresManualReview()).thenReturn(true);

            when(actionRepository.save(any())).thenReturn(savedEntity);

            service.logAction(dto);

            verify(broadcaster).broadcastAlert(anyString(), eq("CRITICAL"));
        }
    }

    @Nested
    @DisplayName("Action Retrieval")
    class ActionRetrievalTests {

        @Test
        @DisplayName("Should get recent actions within time window")
        void shouldGetRecentActions() {
            List<ActionEntity> recentActions = List.of(
                    ActionEntity.builder().id(1L).safetyLevel(SafetyLevel.LOW).build(),
                    ActionEntity.builder().id(2L).safetyLevel(SafetyLevel.LOW).build());
            when(actionRepository.findByExecutedAtAfterOrderByExecutedAtDesc(any(Instant.class)))
                    .thenReturn(recentActions);

            List<ActionEntity> result = service.getRecentActions(24);

            assertEquals(2, result.size());
            verify(actionRepository).findByExecutedAtAfterOrderByExecutedAtDesc(any(Instant.class));
        }

        @Test
        @DisplayName("Should get all actions")
        void shouldGetAllActions() {
            List<ActionEntity> allActions = List.of(
                    ActionEntity.builder().id(1L).safetyLevel(SafetyLevel.MEDIUM).build());
            when(actionRepository.findAll()).thenReturn(allActions);

            List<ActionEntity> result = service.getAllActions();

            assertEquals(1, result.size());
            verify(actionRepository).findAll();
        }

        @Test
        @DisplayName("Should get action by ID")
        void shouldGetActionById() {
            ActionEntity action = ActionEntity.builder().id(99L).safetyLevel(SafetyLevel.LOW).build();
            when(actionRepository.findById(99L)).thenReturn(Optional.of(action));

            Optional<ActionEntity> result = service.getActionById(99L);

            assertTrue(result.isPresent());
            assertEquals(99L, result.get().getId());
        }

        @Test
        @DisplayName("Should return empty for non-existent action")
        void shouldReturnEmptyForNonExistent() {
            when(actionRepository.findById(999L)).thenReturn(Optional.empty());

            Optional<ActionEntity> result = service.getActionById(999L);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should get actions by type")
        void shouldGetActionsByType() {
            List<ActionEntity> killActions = List.of(
                    ActionEntity.builder().id(1L).actionType(ActionType.KILL_PROCESS).safetyLevel(SafetyLevel.HIGH)
                            .build());
            when(actionRepository.findByActionTypeOrderByExecutedAtDesc(ActionType.KILL_PROCESS))
                    .thenReturn(killActions);

            List<ActionEntity> result = service.getActionsByType(ActionType.KILL_PROCESS);

            assertEquals(1, result.size());
            assertEquals(ActionType.KILL_PROCESS, result.get(0).getActionType());
        }

        @Test
        @DisplayName("Should get actions by status")
        void shouldGetActionsByStatus() {
            List<ActionEntity> successActions = List.of(
                    ActionEntity.builder().id(1L).status(ActionStatus.SUCCESS).safetyLevel(SafetyLevel.LOW).build());
            when(actionRepository.findByStatusOrderByExecutedAtDesc(ActionStatus.SUCCESS))
                    .thenReturn(successActions);

            List<ActionEntity> result = service.getActionsByStatus(ActionStatus.SUCCESS);

            assertEquals(1, result.size());
            assertEquals(ActionStatus.SUCCESS, result.get(0).getStatus());
        }

        @Test
        @DisplayName("Should get actions by process ID")
        void shouldGetActionsByProcess() {
            List<ActionEntity> processActions = List.of(
                    ActionEntity.builder().id(1L).targetPid(1234).safetyLevel(SafetyLevel.MEDIUM).build());
            when(actionRepository.findByTargetPidOrderByExecutedAtDesc(1234))
                    .thenReturn(processActions);

            List<ActionEntity> result = service.getActionsByProcess(1234);

            assertEquals(1, result.size());
            assertEquals(1234, result.get(0).getTargetPid());
        }

        @Test
        @DisplayName("Should get actions by issue")
        void shouldGetActionsByIssue() {
            List<ActionEntity> issueActions = List.of(
                    ActionEntity.builder().id(1L).safetyLevel(SafetyLevel.LOW).build());
            when(actionRepository.findByIssueId(42L)).thenReturn(issueActions);

            List<ActionEntity> result = service.getActionsByIssue(42L);

            assertEquals(1, result.size());
            verify(actionRepository).findByIssueId(42L);
        }

        @Test
        @DisplayName("Should get real (non-dry-run) actions only")
        void shouldGetRealActions() {
            List<ActionEntity> realActions = List.of(
                    ActionEntity.builder().id(1L).dryRun(false).safetyLevel(SafetyLevel.MEDIUM).build());
            when(actionRepository.findByDryRunFalseOrderByExecutedAtDesc())
                    .thenReturn(realActions);

            List<ActionEntity> result = service.getRealActions();

            assertEquals(1, result.size());
            assertFalse(result.get(0).getDryRun());
        }

        @Test
        @DisplayName("Should get actions requiring manual review")
        void shouldGetActionsRequiringReview() {
            ActionEntity reviewAction = mock(ActionEntity.class);
            when(reviewAction.getId()).thenReturn(1L);
            when(reviewAction.requiresManualReview()).thenReturn(true);

            List<ActionEntity> reviewActions = List.of(reviewAction);
            when(actionRepository.findActionsRequiringManualReview())
                    .thenReturn(reviewActions);

            List<ActionEntity> result = service.getActionsRequiringReview();

            assertEquals(1, result.size());
        }
    }

    @Nested
    @DisplayName("Action Statistics")
    class ActionStatisticsTests {

        @Test
        @DisplayName("Should calculate success rate for action type")
        void shouldCalculateSuccessRate() {
            when(actionRepository.getSuccessRateByActionType(eq(ActionType.KILL_PROCESS), any(Instant.class)))
                    .thenReturn(85.5);

            double rate = service.getSuccessRate(ActionType.KILL_PROCESS, 24);

            assertEquals(85.5, rate, 0.01);
        }

        @Test
        @DisplayName("Should return zero for null success rate")
        void shouldReturnZeroForNullRate() {
            when(actionRepository.getSuccessRateByActionType(any(), any(Instant.class)))
                    .thenReturn(null);

            double rate = service.getSuccessRate(ActionType.REDUCE_PRIORITY, 24);

            assertEquals(0.0, rate, 0.01);
        }

        @Test
        @DisplayName("Should get statistics for time window")
        void shouldGetStatisticsForTimeWindow() {
            List<ActionEntity> actions = List.of(
                    ActionEntity.builder().id(1L).status(ActionStatus.SUCCESS).safetyLevel(SafetyLevel.LOW).build(),
                    ActionEntity.builder().id(2L).status(ActionStatus.SUCCESS).safetyLevel(SafetyLevel.LOW).build(),
                    ActionEntity.builder().id(3L).status(ActionStatus.FAILED).safetyLevel(SafetyLevel.LOW).build());
            when(actionRepository.findByExecutedAtAfterOrderByExecutedAtDesc(any(Instant.class)))
                    .thenReturn(actions);
            when(actionRepository.countByDryRunFalseAndExecutedAtBetween(any(Instant.class), any(Instant.class)))
                    .thenReturn(2L);

            Map<String, Object> stats = service.getStatistics(24);

            assertEquals(3L, stats.get("totalActions"));
            assertEquals(2L, stats.get("successfulActions"));
            assertEquals(1L, stats.get("failedActions"));
            assertEquals(2L, stats.get("realActions"));
            assertEquals("24 hours", stats.get("timeWindow"));
        }

        @Test
        @DisplayName("Should calculate overall success rate correctly")
        void shouldCalculateOverallSuccessRate() {
            List<ActionEntity> actions = List.of(
                    ActionEntity.builder().id(1L).status(ActionStatus.SUCCESS).safetyLevel(SafetyLevel.LOW).build(),
                    ActionEntity.builder().id(2L).status(ActionStatus.SUCCESS).safetyLevel(SafetyLevel.LOW).build(),
                    ActionEntity.builder().id(3L).status(ActionStatus.SUCCESS).safetyLevel(SafetyLevel.LOW).build(),
                    ActionEntity.builder().id(4L).status(ActionStatus.FAILED).safetyLevel(SafetyLevel.LOW).build());
            when(actionRepository.findByExecutedAtAfterOrderByExecutedAtDesc(any(Instant.class)))
                    .thenReturn(actions);
            when(actionRepository.countByDryRunFalseAndExecutedAtBetween(any(), any()))
                    .thenReturn(3L);

            Map<String, Object> stats = service.getStatistics(24);

            assertEquals(75.0, (Double) stats.get("overallSuccessRate"), 0.01);
        }

        @Test
        @DisplayName("Should handle empty action list in statistics")
        void shouldHandleEmptyActionList() {
            when(actionRepository.findByExecutedAtAfterOrderByExecutedAtDesc(any(Instant.class)))
                    .thenReturn(Collections.emptyList());
            when(actionRepository.countByDryRunFalseAndExecutedAtBetween(any(), any()))
                    .thenReturn(0L);

            Map<String, Object> stats = service.getStatistics(24);

            assertEquals(0L, stats.get("totalActions"));
            assertEquals(0.0, (Double) stats.get("overallSuccessRate"), 0.01);
        }

        @Test
        @DisplayName("Should get all-time statistics when hours is null")
        void shouldGetAllTimeStatistics() {
            List<ActionEntity> allActions = List.of(
                    ActionEntity.builder().id(1L).status(ActionStatus.SUCCESS).safetyLevel(SafetyLevel.LOW).build());
            when(actionRepository.findAll()).thenReturn(allActions);
            when(actionRepository.countByDryRunFalseAndExecutedAtBetween(any(), any()))
                    .thenReturn(1L);

            Map<String, Object> stats = service.getStatistics(null);

            assertEquals("all time", stats.get("timeWindow"));
            verify(actionRepository).findAll();
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle action without issue link")
        void shouldHandleActionWithoutIssueLink() {
            RemediationActionLog dto = RemediationActionLog.builder()
                    .actionType(ActionType.KILL_PROCESS)
                    .status(ActionStatus.SUCCESS)
                    .targetPid(1234)
                    .targetName("chrome.exe")
                    .safetyLevel(SafetyLevel.MEDIUM)
                    .issueId(null)
                    .dryRun(false)
                    .executedAt(Instant.now())
                    .build();

            ActionEntity savedEntity = ActionEntity.builder()
                    .id(1L)
                    .issue(null)
                    .safetyLevel(SafetyLevel.MEDIUM)
                    .build();

            when(actionRepository.save(any())).thenReturn(savedEntity);

            ActionEntity result = service.logAction(dto);

            assertNotNull(result);
            verify(issueRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Should handle non-existent issue link")
        void shouldHandleNonExistentIssueLink() {
            when(issueRepository.findById(999L)).thenReturn(Optional.empty());

            RemediationActionLog dto = RemediationActionLog.builder()
                    .actionType(ActionType.KILL_PROCESS)
                    .status(ActionStatus.SUCCESS)
                    .targetPid(1234)
                    .targetName("chrome.exe")
                    .safetyLevel(SafetyLevel.MEDIUM)
                    .issueId(999L)
                    .dryRun(false)
                    .executedAt(Instant.now())
                    .build();

            ActionEntity savedEntity = ActionEntity.builder()
                    .id(1L)
                    .issue(null)
                    .safetyLevel(SafetyLevel.MEDIUM)
                    .build();

            when(actionRepository.save(any())).thenReturn(savedEntity);

            ActionEntity result = service.logAction(dto);

            assertNotNull(result);
            assertNull(result.getIssue());
        }

        @Test
        @DisplayName("Should handle empty recent actions list")
        void shouldHandleEmptyRecentActions() {
            when(actionRepository.findByExecutedAtAfterOrderByExecutedAtDesc(any(Instant.class)))
                    .thenReturn(Collections.emptyList());

            List<ActionEntity> result = service.getRecentActions(24);

            assertTrue(result.isEmpty());
        }
    }
}
