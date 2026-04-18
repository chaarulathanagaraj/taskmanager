package com.aios.agent.remediation.actions;

import com.aios.agent.remediation.ActionResult;
import com.aios.agent.remediation.RemediationAction;
import com.aios.agent.remediation.RemediationContext;
import com.aios.agent.remediation.WindowsNativeUtils;
import com.aios.shared.enums.SafetyLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KillProcessAction implements RemediationAction {

    @Override
    public ActionResult execute(RemediationContext context) {
        int pid = context.getTargetPid();
        if (context.isDryRun()) {
            log.info("DRY RUN: Would terminate PID {}", pid);
            return ActionResult.success("Dry run simulated terminating process");
        }

        try {
            boolean success = WindowsNativeUtils.terminateProcess(pid, 1);
            if (success) {
                return ActionResult.success("Successfully terminated PID " + pid);
            } else {
                return ActionResult.failure("Failed to terminate PID " + pid + ". Insufficient access or process already dead.");
            }
        } catch (Exception e) {
             return ActionResult.failure("Exception terminating process: " + e.getMessage());
        }
    }

    @Override
    public SafetyLevel getSafetyLevel() {
        return SafetyLevel.HIGH;
    }

    @Override
    public String getName() {
        return "KillProcessAction";
    }

    @Override
    public String getDescription() {
        return "Forcefully terminates a target process using Windows Native API.";
    }
}