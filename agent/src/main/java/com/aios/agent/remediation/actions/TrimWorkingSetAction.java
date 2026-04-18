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
public class TrimWorkingSetAction implements RemediationAction {

    @Override
    public ActionResult execute(RemediationContext context) {
        int pid = context.getTargetPid();
        if (context.isDryRun()) {
            log.info("DRY RUN: Would trim working set memory of PID {}", pid);
            return ActionResult.success("Dry run simulated trimming working set memory");
        }

        try {
            boolean success = WindowsNativeUtils.trimWorkingSet(pid);
            if (success) {
                return ActionResult.success("Successfully trimmed working set memory of PID " + pid);
            } else {
                return ActionResult.failure("Failed to trim memory of PID " + pid + ". Insufficient access.");
            }
        } catch (Exception e) {
             return ActionResult.failure("Exception trimming memory: " + e.getMessage());
        }
    }

    @Override
    public SafetyLevel getSafetyLevel() {
        return SafetyLevel.MEDIUM;
    }

    @Override
    public String getName() {
        return "TrimWorkingSetAction";
    }

    @Override
    public String getDescription() {
        return "Releases unneeded memory pages from the process working set.";
    }
}