package com.aios.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA Entity for storing system metrics snapshots.
 * 
 * <p>Stores CPU, memory, disk, and network metrics collected
 * by the agent at regular intervals (default: every 10 seconds).
 * 
 * <p>Table: metrics
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Entity
@Table(name = "metrics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Timestamp when metrics were collected.
     */
    @Column(nullable = false)
    private Instant timestamp;

    /**
     * CPU usage percentage (0-100).
     */
    @Column(name = "cpu_usage")
    private Double cpuUsage;

    /**
     * Memory used in bytes.
     */
    @Column(name = "memory_used")
    private Long memoryUsed;

    /**
     * Total memory available in bytes.
     */
    @Column(name = "memory_total")
    private Long memoryTotal;

    /**
     * Disk read bytes since last measurement.
     */
    @Column(name = "disk_read")
    private Long diskRead;

    /**
     * Disk write bytes since last measurement.
     */
    @Column(name = "disk_write")
    private Long diskWrite;

    /**
     * Network bytes sent since last measurement.
     */
    @Column(name = "network_sent")
    private Long networkSent;

    /**
     * Network bytes received since last measurement.
     */
    @Column(name = "network_received")
    private Long networkReceived;

    /**
     * Calculate memory usage percentage.
     * 
     * @return memory usage as percentage (0-100)
     */
    @Transient
    public double getMemoryUsagePercent() {
        if (memoryTotal == null || memoryTotal == 0) {
            return 0.0;
        }
        return (memoryUsed.doubleValue() / memoryTotal) * 100.0;
    }
}
