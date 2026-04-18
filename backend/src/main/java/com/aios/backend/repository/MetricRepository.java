package com.aios.backend.repository;

import com.aios.backend.model.MetricEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository interface for MetricEntity database operations.
 * 
 * <p>Provides CRUD operations and custom queries for system metrics.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Repository
public interface MetricRepository extends JpaRepository<MetricEntity, Long> {

    /**
     * Find all metrics collected after a specific timestamp.
     * Useful for retrieving recent metrics for dashboard display.
     * 
     * @param timestamp the cutoff timestamp
     * @return list of metrics after the timestamp, ordered by timestamp ascending
     */
    List<MetricEntity> findByTimestampAfterOrderByTimestampAsc(Instant timestamp);

    /**
     * Find all metrics within a time range.
     * 
     * @param start start timestamp (inclusive)
     * @param end end timestamp (inclusive)
     * @return list of metrics in the time range
     */
    List<MetricEntity> findByTimestampBetweenOrderByTimestampAsc(Instant start, Instant end);

    /**
     * Get the most recent N metrics.
     * 
     * @param limit maximum number of records to return
     * @return list of most recent metrics
     */
    @Query("SELECT m FROM MetricEntity m ORDER BY m.timestamp DESC LIMIT :limit")
    List<MetricEntity> findTopNByOrderByTimestampDesc(@Param("limit") int limit);

    /**
     * Delete all metrics older than a specific timestamp.
     * Used for periodic cleanup of old data.
     * 
     * @param timestamp cutoff timestamp
     * @return number of deleted records
     */
    long deleteByTimestampBefore(Instant timestamp);

    /**
     * Count metrics within a time range.
     * 
     * @param start start timestamp
     * @param end end timestamp
     * @return count of metrics
     */
    long countByTimestampBetween(Instant start, Instant end);

    /**
     * Get average CPU usage over a time period.
     * 
     * @param start start timestamp
     * @param end end timestamp
     * @return average CPU usage as percentage
     */
    @Query("SELECT AVG(m.cpuUsage) FROM MetricEntity m WHERE m.timestamp BETWEEN :start AND :end")
    Double getAverageCpuUsage(@Param("start") Instant start, @Param("end") Instant end);

    /**
     * Get average memory usage percentage over a time period.
     * 
     * @param start start timestamp
     * @param end end timestamp
     * @return average memory usage as percentage
     */
    @Query("SELECT AVG(CAST(m.memoryUsed AS double) / m.memoryTotal * 100) FROM MetricEntity m WHERE m.timestamp BETWEEN :start AND :end")
    Double getAverageMemoryUsagePercent(@Param("start") Instant start, @Param("end") Instant end);
}
