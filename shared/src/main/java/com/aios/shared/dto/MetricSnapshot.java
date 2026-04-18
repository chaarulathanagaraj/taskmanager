package com.aios.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents a snapshot of system metrics at a point in time
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricSnapshot {
    private Instant timestamp;
    private double cpuUsage;
    private long memoryUsed;
    private long memoryTotal;
    private long diskRead;
    private long diskWrite;
    private long networkSent;
    private long networkReceived;
    private int processCount;
}
