package com.aios.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents information about a running process
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessInfo {
    private int pid;
    private String name;
    private double cpuPercent;
    private long memoryBytes;
    private int threadCount;
    private long handleCount;
    private long ioReadBytes;
    private long ioWriteBytes;
}
