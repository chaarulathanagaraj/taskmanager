package com.aios.agent.remediation.actions;

import com.aios.agent.remediation.ActionResult;
import com.aios.agent.remediation.ProcessPriority;
import com.aios.agent.remediation.RemediationAction;
import com.aios.agent.remediation.RemediationContext;
import com.aios.agent.remediation.WindowsNativeUtils;
import com.aios.shared.enums.SafetyLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ReducePriorityAction implements RemediationAction {

    @Override
    public ActionResult execute(RemediationContext context) {
        int pid = context.getTargetPid();
        if (context.isDryRun()) {
            log.info("DRY RUN: Would reduce priority of PID {}", pid);
            return ActionResult.success("Dry run simulated reducing priority");
        }

        try {
            boolean success = WindowsNativeUtils.setPriority(pid, ProcessPriority.BELOW_NORMAL);
            if (success) {
                return ActionResult.success("Successfully reduced priority of PID " + pid + " to BELOW_NORMAL");
            } else {
                return ActionResult.failure("Failed to reduce priority of PID " + pid + ". Insufficient access or process died.");
            }
        } catch (Exception e) {
             return ActionResult.failure("Exception reducing priority: " + e.getMessage());
        }
    }

    @Override
    public SafetyLevel getSafetyLevel() {
        return SafetyLevel.LOW;
    }

    @Override
    public String getName() {
        return "ReducePriorityAction";
    }

    @Override
    public String getDescription() {
        return "Reduces the CPU scheduling priority of a target process to BELOW_NORMAL.";
    }
}