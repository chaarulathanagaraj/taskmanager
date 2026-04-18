package com.aios.backend.dto;
import lombok.Data;
@Data
public class ManualExecuteRequest {
    private String actionType;
    private int targetPid;
    private boolean dryRun;
}
