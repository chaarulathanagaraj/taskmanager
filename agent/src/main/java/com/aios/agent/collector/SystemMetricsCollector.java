package com.aios.agent.collector;

import com.aios.agent.client.BackendClient;
import com.aios.shared.dto.MetricSnapshot;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.software.os.OperatingSystem;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * Service for collecting system-level metrics (CPU, memory, disk, network)
 * using OSHI.
 * Collects metrics every 10 seconds and maintains a 1-hour rolling history.
 */
@Service
@Slf4j
public class SystemMetricsCollector {

    private final SystemInfo systemInfo;
    private final HardwareAbstractionLayer hardware;
    private final OperatingSystem os;
    private final Deque<MetricSnapshot> metricsHistory;
    private final BackendClient backendClient;

    // Previous tick values for calculating deltas
    private long[] prevCpuTicks;
    private long prevDiskRead = 0;
    private long prevDiskWrite = 0;
    private long prevNetworkSent = 0;
    private long prevNetworkReceived = 0;
    private long prevTimestamp = 0;

    @Getter
    private MetricSnapshot latestSnapshot;

    public SystemMetricsCollector(BackendClient backendClient) {
        this.backendClient = backendClient;
        this.systemInfo = new SystemInfo();
        this.hardware = systemInfo.getHardware();
        this.os = systemInfo.getOperatingSystem();
        this.metricsHistory = new LinkedBlockingDeque<>(360); // 1 hour at 10s intervals
        this.prevCpuTicks = hardware.getProcessor().getSystemCpuLoadTicks();
        this.prevTimestamp = System.currentTimeMillis();

        // Initialize baseline values
        initializeBaseline();

        log.info("SystemMetricsCollector initialized. OS: {}, CPU cores: {}",
                os.getFamily(), hardware.getProcessor().getLogicalProcessorCount());
    }

    /**
     * Initialize baseline values for disk and network I/O
     */
    private void initializeBaseline() {
        List<HWDiskStore> disks = hardware.getDiskStores();
        for (HWDiskStore disk : disks) {
            prevDiskRead += disk.getReadBytes();
            prevDiskWrite += disk.getWriteBytes();
        }

        List<NetworkIF> networks = hardware.getNetworkIFs();
        for (NetworkIF net : networks) {
            prevNetworkSent += net.getBytesSent();
            prevNetworkReceived += net.getBytesRecv();
        }
    }

    /**
     * Collect system metrics every 10 seconds
     */
    @Scheduled(fixedDelay = 10000) // Every 10 seconds
    public void collectMetrics() {
        try {
            long currentTimestamp = System.currentTimeMillis();
            double deltaSeconds = (currentTimestamp - prevTimestamp) / 1000.0;

            MetricSnapshot snapshot = MetricSnapshot.builder()
                    .timestamp(Instant.now())
                    .cpuUsage(getCpuUsage())
                    .memoryUsed(getMemoryUsed())
                    .memoryTotal(hardware.getMemory().getTotal())
                    .diskRead(getDiskRead(deltaSeconds))
                    .diskWrite(getDiskWrite(deltaSeconds))
                    .networkSent(getNetworkSent(deltaSeconds))
                    .networkReceived(getNetworkReceived(deltaSeconds))
                    .processCount(os.getProcessCount())
                    .build();

            metricsHistory.addLast(snapshot);
            if (metricsHistory.size() > 360) {
                metricsHistory.removeFirst();
            }

            latestSnapshot = snapshot;
            prevTimestamp = currentTimestamp;

            // Queue metric for backend sync
            backendClient.queueMetric(snapshot);

            log.info("Collected metrics: CPU={}%, Memory={} MB, Disk R/W={}/{} MB/s, Network R/W={}/{} MB/s",
                    String.format("%.2f", snapshot.getCpuUsage()),
                    snapshot.getMemoryUsed() / 1024 / 1024,
                    snapshot.getDiskRead() / 1024 / 1024,
                    snapshot.getDiskWrite() / 1024 / 1024,
                    snapshot.getNetworkReceived() / 1024 / 1024,
                    snapshot.getNetworkSent() / 1024 / 1024);

        } catch (Exception e) {
            log.error("Error collecting metrics", e);
        }
    }

    /**
     * Calculate CPU usage percentage
     */
    private double getCpuUsage() {
        try {
            CentralProcessor processor = hardware.getProcessor();
            long[] currentTicks = processor.getSystemCpuLoadTicks();

            double cpuLoad = processor.getSystemCpuLoadBetweenTicks(prevCpuTicks) * 100;

            // Update previous ticks for next calculation
            prevCpuTicks = currentTicks;

            // Round to 2 decimal places
            return Math.round(cpuLoad * 100.0) / 100.0;
        } catch (Exception e) {
            log.error("Error calculating CPU usage", e);
            return 0.0;
        }
    }

    /**
     * Get current memory usage in bytes
     */
    private long getMemoryUsed() {
        try {
            GlobalMemory memory = hardware.getMemory();
            return memory.getTotal() - memory.getAvailable();
        } catch (Exception e) {
            log.error("Error getting memory usage", e);
            return 0L;
        }
    }

    /**
     * Calculate disk read rate (bytes per second)
     */
    private long getDiskRead(double deltaSeconds) {
        try {
            long currentRead = 0;
            List<HWDiskStore> disks = hardware.getDiskStores();

            for (HWDiskStore disk : disks) {
                disk.updateAttributes(); // Important: update to get fresh values
                currentRead += disk.getReadBytes();
            }

            long readDelta = currentRead - prevDiskRead;
            prevDiskRead = currentRead;

            // Convert to bytes per second
            return deltaSeconds > 0 ? (long) (readDelta / deltaSeconds) : 0;
        } catch (Exception e) {
            log.error("Error calculating disk read rate", e);
            return 0L;
        }
    }

    /**
     * Calculate disk write rate (bytes per second)
     */
    private long getDiskWrite(double deltaSeconds) {
        try {
            long currentWrite = 0;
            List<HWDiskStore> disks = hardware.getDiskStores();

            for (HWDiskStore disk : disks) {
                disk.updateAttributes(); // Important: update to get fresh values
                currentWrite += disk.getWriteBytes();
            }

            long writeDelta = currentWrite - prevDiskWrite;
            prevDiskWrite = currentWrite;

            // Convert to bytes per second
            return deltaSeconds > 0 ? (long) (writeDelta / deltaSeconds) : 0;
        } catch (Exception e) {
            log.error("Error calculating disk write rate", e);
            return 0L;
        }
    }

    /**
     * Calculate network sent rate (bytes per second)
     */
    private long getNetworkSent(double deltaSeconds) {
        try {
            long currentSent = 0;
            List<NetworkIF> networks = hardware.getNetworkIFs();

            for (NetworkIF net : networks) {
                net.updateAttributes(); // Important: update to get fresh values
                currentSent += net.getBytesSent();
            }

            long sentDelta = currentSent - prevNetworkSent;
            prevNetworkSent = currentSent;

            // Convert to bytes per second
            return deltaSeconds > 0 ? (long) (sentDelta / deltaSeconds) : 0;
        } catch (Exception e) {
            log.error("Error calculating network sent rate", e);
            return 0L;
        }
    }

    /**
     * Calculate network received rate (bytes per second)
     */
    private long getNetworkReceived(double deltaSeconds) {
        try {
            long currentReceived = 0;
            List<NetworkIF> networks = hardware.getNetworkIFs();

            for (NetworkIF net : networks) {
                net.updateAttributes(); // Important: update to get fresh values
                currentReceived += net.getBytesRecv();
            }

            long receivedDelta = currentReceived - prevNetworkReceived;
            prevNetworkReceived = currentReceived;

            // Convert to bytes per second
            return deltaSeconds > 0 ? (long) (receivedDelta / deltaSeconds) : 0;
        } catch (Exception e) {
            log.error("Error calculating network received rate", e);
            return 0L;
        }
    }

    /**
     * Get recent metrics for the specified number of minutes
     * 
     * @param minutes Number of minutes of history to retrieve (max 60)
     * @return List of metric snapshots
     */
    public List<MetricSnapshot> getRecentMetrics(int minutes) {
        int samples = Math.min(minutes * 6, 360); // 6 samples per minute (10s intervals)
        List<MetricSnapshot> result = new ArrayList<>();

        synchronized (metricsHistory) {
            int skip = Math.max(0, metricsHistory.size() - samples);
            int count = 0;

            for (MetricSnapshot snapshot : metricsHistory) {
                if (count++ >= skip) {
                    result.add(snapshot);
                }
            }
        }

        return result;
    }

    /**
     * Get all stored metrics history
     * 
     * @return List of all metric snapshots
     */
    public List<MetricSnapshot> getAllMetrics() {
        return new ArrayList<>(metricsHistory);
    }

    /**
     * Get current metrics count in history
     * 
     * @return Number of stored metric snapshots
     */
    public int getMetricsCount() {
        return metricsHistory.size();
    }

    /**
     * Clear metrics history (useful for testing)
     */
    public void clearHistory() {
        metricsHistory.clear();
        log.info("Metrics history cleared");
    }
}
