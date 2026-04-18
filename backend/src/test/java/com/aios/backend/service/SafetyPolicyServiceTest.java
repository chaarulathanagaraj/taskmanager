package com.aios.backend.service;

import com.aios.backend.model.SafetyPolicyEntity;
import com.aios.backend.repository.SafetyPolicyRepository;
import com.aios.shared.dto.PolicyViolation;
import com.aios.shared.enums.ActionType;
import com.aios.shared.enums.SafetyLevel;
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

import java.util.List;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SafetyPolicyService.
 * 
 * Tests policy enforcement including protected process blocking,
 * rate limiting, and confidence threshold validation.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SafetyPolicyService Tests")
class SafetyPolicyServiceTest {

    @Mock
    private SafetyPolicyRepository policyRepository;

    @Mock
    private SettingsService settingsService;

    private SafetyPolicyService service;

    @BeforeEach
    void setUp() {
        service = new SafetyPolicyService(policyRepository, settingsService);
        // Set default values via reflection
        ReflectionTestUtils.setField(service, "defaultProtectedProcesses",
                "csrss.exe,winlogon.exe,services.exe,lsass.exe,System,smss.exe,svchost.exe");
        ReflectionTestUtils.setField(service, "enforcePolicies", true);
        ReflectionTestUtils.setField(service, "globalDryRunMode", true);

        // Initialize the service
        when(policyRepository.findApplicablePolicies(any())).thenReturn(Collections.emptyList());
        when(settingsService.isProcessProtected(any())).thenReturn(false);
        service.initialize();
    }

    @Nested
    @DisplayName("System Protected Processes")
    class SystemProtectedProcessesTests {

        @Test
        @DisplayName("Should block action on csrss.exe")
        void shouldBlockCsrss() {
            PolicyViolation violation = service.checkPolicy(
                    ActionType.KILL_PROCESS, "csrss.exe", 4, false, 0.99);

            assertTrue(violation.isViolated());
            assertTrue(violation.isBlocking());
            assertNotNull(violation.getReason());
            assertTrue(violation.getReason().toLowerCase().contains("protected"));
        }

        @Test
        @DisplayName("Should block action on lsass.exe")
        void shouldBlockLsass() {
            PolicyViolation violation = service.checkPolicy(
                    ActionType.KILL_PROCESS, "lsass.exe", 732, false, 0.95);

            assertTrue(violation.isViolated());
            assertEquals("lsass.exe", violation.getTargetProcess());
            assertEquals(732, violation.getTargetPid());
        }

        @Test
        @DisplayName("Should block action on System process")
        void shouldBlockSystemProcess() {
            PolicyViolation violation = service.checkPolicy(
                    ActionType.KILL_PROCESS, "System", 4, false, 1.0);

            assertTrue(violation.isViolated());
        }

        @Test
        @DisplayName("Should block action on svchost.exe")
        void shouldBlockSvchost() {
            PolicyViolation violation = service.checkPolicy(
                    ActionType.KILL_PROCESS, "svchost.exe", 1088, false, 0.9);

            assertTrue(violation.isViolated());
            assertTrue(violation.getReason().contains("svchost.exe"));
        }

        @Test
        @DisplayName("Should allow action on non-protected process")
        void shouldAllowNonProtectedProcess() {
            PolicyViolation violation = service.checkPolicy(
                    ActionType.KILL_PROCESS, "chrome.exe", 5678, true, 0.85);

            assertFalse(violation.isViolated());
        }

        @Test
        @DisplayName("Should allow action on user application")
        void shouldAllowUserApplication() {
            PolicyViolation violation = service.checkPolicy(
                    ActionType.REDUCE_PRIORITY, "notepad.exe", 1234, true, 0.7);

            assertFalse(violation.isViolated());
        }
    }

    @Nested
    @DisplayName("Policy Enforcement Toggle")
    class PolicyEnforcementToggleTests {

        @Test
        @DisplayName("Should allow everything when enforcement disabled")
        void shouldAllowWhenEnforcementDisabled() {
            ReflectionTestUtils.setField(service, "enforcePolicies", false);

            // Even protected processes should be allowed
            PolicyViolation violation = service.checkPolicy(
                    ActionType.KILL_PROCESS, "csrss.exe", 4, false, 0.99);

            assertFalse(violation.isViolated());
        }

        @Test
        @DisplayName("Should enforce policies when enabled")
        void shouldEnforceWhenEnabled() {
            ReflectionTestUtils.setField(service, "enforcePolicies", true);

            PolicyViolation violation = service.checkPolicy(
                    ActionType.KILL_PROCESS, "lsass.exe", 732, false, 0.95);

            assertTrue(violation.isViolated());
        }
    }

    @Nested
    @DisplayName("Action Type Handling")
    class ActionTypeHandlingTests {

        @Test
        @DisplayName("Should check policies for KILL_PROCESS action")
        void shouldCheckKillProcessPolicies() {
            service.checkPolicy(ActionType.KILL_PROCESS, "chrome.exe", 1234, true, 0.8);
            verify(policyRepository).findApplicablePolicies(ActionType.KILL_PROCESS);
        }

        @Test
        @DisplayName("Should check policies for REDUCE_PRIORITY action")
        void shouldCheckReducePriorityPolicies() {
            service.checkPolicy(ActionType.REDUCE_PRIORITY, "notepad.exe", 5678, true, 0.6);
            verify(policyRepository).findApplicablePolicies(ActionType.REDUCE_PRIORITY);
        }

        @Test
        @DisplayName("Should check policies for TRIM_WORKING_SET action")
        void shouldCheckTrimWorkingSetPolicies() {
            service.checkPolicy(ActionType.TRIM_WORKING_SET, "firefox.exe", 9999, true, 0.75);
            verify(policyRepository).findApplicablePolicies(ActionType.TRIM_WORKING_SET);
        }
    }

    @Nested
    @DisplayName("Dry Run Mode")
    class DryRunModeTests {

        @Test
        @DisplayName("Should use dry run flag correctly")
        void shouldHandleDryRunFlag() {
            // In dry run mode, action on non-protected process should be allowed
            PolicyViolation dryRunViolation = service.checkPolicy(
                    ActionType.KILL_PROCESS, "chrome.exe", 1234, true, 0.85);

            assertFalse(dryRunViolation.isViolated());

            // Protected processes should still be blocked even in dry run
            PolicyViolation protectedViolation = service.checkPolicy(
                    ActionType.KILL_PROCESS, "lsass.exe", 732, true, 0.99);

            assertTrue(protectedViolation.isViolated());
        }
    }

    @Nested
    @DisplayName("Case Sensitivity")
    class CaseSensitivityTests {

        @Test
        @DisplayName("Should block case-insensitive matches")
        void shouldBlockCaseInsensitive() {
            // Test various case combinations
            assertTrue(service.checkPolicy(
                    ActionType.KILL_PROCESS, "CSRSS.EXE", 4, false, 0.99).isViolated());
            assertTrue(service.checkPolicy(
                    ActionType.KILL_PROCESS, "Csrss.Exe", 4, false, 0.99).isViolated());
            assertTrue(service.checkPolicy(
                    ActionType.KILL_PROCESS, "csrss.EXE", 4, false, 0.99).isViolated());
        }
    }

    @Nested
    @DisplayName("PolicyViolation Details")
    class PolicyViolationDetailsTests {

        @Test
        @DisplayName("Should include process name in violation")
        void shouldIncludeProcessName() {
            PolicyViolation violation = service.checkPolicy(
                    ActionType.KILL_PROCESS, "lsass.exe", 732, false, 0.95);

            assertEquals("lsass.exe", violation.getTargetProcess());
        }

        @Test
        @DisplayName("Should include PID in violation")
        void shouldIncludePid() {
            PolicyViolation violation = service.checkPolicy(
                    ActionType.KILL_PROCESS, "csrss.exe", 4, false, 0.99);

            assertEquals(4, violation.getTargetPid());
        }

        @Test
        @DisplayName("Should include attempted action in violation")
        void shouldIncludeAttemptedAction() {
            PolicyViolation violation = service.checkPolicy(
                    ActionType.KILL_PROCESS, "winlogon.exe", 608, false, 0.9);

            assertEquals(ActionType.KILL_PROCESS, violation.getAttemptedAction());
        }
    }
}
