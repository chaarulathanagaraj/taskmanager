package com.aios.backend.service;

import com.aios.backend.model.MetricEntity;
import com.aios.backend.repository.MetricRepository;
import com.aios.shared.dto.MetricSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing system metrics data.
 * 
 * <p>
 * Handles persistence, retrieval, and aggregation of system metrics
 * collected by the agent. Provides methods for querying recent metrics,
 * calculating statistics, and cleaning up old data.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetricService {

    private final MetricRepository metricRepository;
    private final WebSocketBroadcaster broadcaster;

    /**
     * Save metrics received from the agent.
     * Broadcasts the metrics to WebSocket clients for real-time dashboard updates.
     * 
     * @param metrics list of metric snapshots to save
     * @return list of saved metric entities
     */
    @Transactional
    public List<MetricEntity> saveMetrics(List<MetricSnapshot> metrics) {
        log.info("Saving {} metric snapshots", metrics.size());

        List<MetricEntity> entities = metrics.stream()
                .map(this::toEntity)
                .collect(Collectors.toList());

        List<MetricEntity> saved = metricRepository.saveAll(entities);

        // Broadcast the most recent metric for real-time updates
        if (!metrics.isEmpty()) {
            broadcaster.broadcastMetric(metrics.get(metrics.size() - 1));
        }

        return saved;
    }

    /**
     * Get metrics from the last N minutes.
     * 
     * @param minutes number of minutes to look back (default: 10)
     * @return list of metric entities within the time window
     */
    @Transactional(readOnly = true)
    public List<MetricEntity> getRecentMetrics(int minutes) {
        Instant since = Instant.now().minus(minutes, ChronoUnit.MINUTES);
        log.debug("Fetching metrics since {}", since);
        return metricRepository.findByTimestampAfterOrderByTimestampAsc(since);
    }

    /**
     * Get metrics within a specific time range.
     * 
     * @param start start time
     * @param end   end time
     * @return list of metric entities in the range
     */
    @Transactional(readOnly = true)
    public List<MetricEntity> getMetricsInRange(Instant start, Instant end) {
        log.debug("Fetching metrics between {} and {}", start, end);
        return metricRepository.findByTimestampBetweenOrderByTimestampAsc(start, end);
    }

    /**
     * Get the N most recent metrics.
     * 
     * @param limit maximum number of metrics to return
     * @return list of most recent metric entities
     */
    @Transactional(readOnly = true)
    public List<MetricEntity> getLatestMetrics(int limit) {
        log.debug("Fetching {} latest metrics", limit);
        return metricRepository.findTopNByOrderByTimestampDesc(limit);
    }

    /**
     * Get aggregated metric statistics for a time range.
     * 
     * @param start start time (null for last hour)
     * @param end   end time (null for now)
     * @return map of statistic names to values
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStatistics(Instant start, Instant end) {
        Instant actualStart = start != null ? start : Instant.now().minus(1, ChronoUnit.HOURS);
        Instant actualEnd = end != null ? end : Instant.now();

        log.debug("Calculating statistics from {} to {}", actualStart, actualEnd);

        Double avgCpu = metricRepository.getAverageCpuUsage(actualStart, actualEnd);
        Double avgMemory = metricRepository.getAverageMemoryUsagePercent(actualStart, actualEnd);
        Long count = metricRepository.countByTimestampBetween(actualStart, actualEnd);

        return Map.of(
                "averageCpuUsage", avgCpu != null ? avgCpu : 0.0,
                "averageMemoryUsagePercent", avgMemory != null ? avgMemory : 0.0,
                "sampleCount", count,
                "startTime", actualStart,
                "endTime", actualEnd);
    }

    /**
     * Delete metrics older than a specified number of days.
     * 
     * @param days number of days to keep (metrics older than this are deleted)
     * @return number of deleted records
     */
    @Transactional
    public int deleteOldMetrics(int days) {
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        log.info("Deleting metrics older than {} (cutoff: {})", days + " days", cutoff);

        long deleted = metricRepository.deleteByTimestampBefore(cutoff);
        log.info("Deleted {} old metric records", deleted);

        return (int) deleted;
    }

    /**
     * Convert MetricSnapshot DTO to MetricEntity.
     */
    private MetricEntity toEntity(MetricSnapshot dto) {
        return MetricEntity.builder()
                .timestamp(dto.getTimestamp())
                .cpuUsage(dto.getCpuUsage())
                .memoryUsed(dto.getMemoryUsed())
                .memoryTotal(dto.getMemoryTotal())
                .diskRead(dto.getDiskRead())
                .diskWrite(dto.getDiskWrite())
                .networkSent(dto.getNetworkSent())
                .networkReceived(dto.getNetworkReceived())
                .build();
    }

    /**
     * Collect current system metric using OSHI.
     * Used when no stored metrics are available.
     * 
     * @return current metric snapshot
     */
    public MetricSnapshot collectCurrentMetric() {
        log.debug("Collecting current system metric using OSHI");

        try {
            oshi.SystemInfo si = new oshi.SystemInfo();
            var hal = si.getHardware();
            var processor = hal.getProcessor();
            var memory = hal.getMemory();
            var os = si.getOperatingSystem();

            // CPU usage - use ticks-based calculation for accuracy
            long[] prevTicks = processor.getSystemCpuLoadTicks();
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignore) {
            }
            double cpuUsage = processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100.0;
            // Fallback if CPU load is 0 or negative
            if (cpuUsage <= 0) {
                cpuUsage = processor.getSystemCpuLoad(500) * 100.0;
            }

            // Memory
            long totalMemory = memory.getTotal();
            long availableMemory = memory.getAvailable();
            long usedMemory = totalMemory - availableMemory;

            // Disk I/O
            var diskStores = hal.getDiskStores();
            long totalReadBytes = diskStores.stream().mapToLong(d -> d.getReadBytes()).sum();
            long totalWriteBytes = diskStores.stream().mapToLong(d -> d.getWriteBytes()).sum();

            // Network I/O
            var networks = hal.getNetworkIFs();
            long totalSent = networks.stream().mapToLong(n -> n.getBytesSent()).sum();
            long totalReceived = networks.stream().mapToLong(n -> n.getBytesRecv()).sum();

            // Process count
            int processCount = os.getProcessCount();

            MetricSnapshot snapshot = MetricSnapshot.builder()
                    .timestamp(Instant.now())
                    .cpuUsage(Math.round(cpuUsage * 100.0) / 100.0)
                    .memoryUsed(usedMemory)
                    .memoryTotal(totalMemory)
                    .diskRead(totalReadBytes)
                    .diskWrite(totalWriteBytes)
                    .networkReceived(totalReceived)
                    .networkSent(totalSent)
                    .processCount(processCount)
                    .build();

            log.debug("Collected metric: CPU={}%, Memory={}/{} bytes",
                    snapshot.getCpuUsage(), usedMemory, totalMemory);

            return snapshot;

        } catch (Exception e) {
            log.error("Failed to collect current metric: {}", e.getMessage(), e);
            // Return default metric on error
            return MetricSnapshot.builder()
                    .timestamp(Instant.now())
                    .cpuUsage(0.0)
                    .memoryUsed(0L)
                    .memoryTotal(1L)
                    .diskRead(0L)
                    .diskWrite(0L)
                    .networkReceived(0L)
                    .networkSent(0L)
                    .processCount(0)
                    .build();
        }
    }
}
