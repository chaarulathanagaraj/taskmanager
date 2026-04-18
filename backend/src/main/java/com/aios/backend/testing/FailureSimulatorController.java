package com.aios.backend.testing;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for triggering failure simulations.
 * 
 * <p>
 * WARNING: Only available in 'dev' and 'test' profiles.
 * These endpoints can create resource-intensive conditions.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@RestController
@RequestMapping("/api/test/simulate")
@RequiredArgsConstructor
@Slf4j
@Profile({ "dev", "test" })
@Tag(name = "Test Simulation", description = "Failure simulation endpoints for testing (dev/test only)")
public class FailureSimulatorController {

    private final FailureSimulator failureSimulator;

    /**
     * Get current simulation status.
     */
    @GetMapping("/status")
    @Operation(summary = "Get status", description = "Get status of all active simulations")
    public ResponseEntity<FailureSimulator.SimulationStatus> getStatus() {
        return ResponseEntity.ok(failureSimulator.getStatus());
    }

    /**
     * Stop all simulations.
     */
    @PostMapping("/stop-all")
    @Operation(summary = "Stop all", description = "Stop all active simulations")
    public ResponseEntity<Map<String, String>> stopAll() {
        failureSimulator.stopAll();
        return ResponseEntity.ok(Map.of("message", "All simulations stopped"));
    }

    // ==================== Memory Leak Simulation ====================

    /**
     * Start memory leak simulation.
     */
    @PostMapping("/memory-leak/start")
    @Operation(summary = "Start memory leak", description = "Start simulating a memory leak")
    public ResponseEntity<Map<String, Object>> startMemoryLeak(
            @RequestParam(defaultValue = "100") @Parameter(description = "Maximum MB to leak") int maxMB,
            @RequestParam(defaultValue = "1000") @Parameter(description = "Interval between allocations in ms") int intervalMs) {

        log.warn("API: Starting memory leak simulation: maxMB={}, intervalMs={}", maxMB, intervalMs);
        failureSimulator.startMemoryLeak(maxMB, intervalMs);

        return ResponseEntity.ok(Map.of(
                "message", "Memory leak simulation started",
                "maxMB", maxMB,
                "intervalMs", intervalMs));
    }

    /**
     * Stop memory leak simulation.
     */
    @PostMapping("/memory-leak/stop")
    @Operation(summary = "Stop memory leak", description = "Stop memory leak simulation and free memory")
    public ResponseEntity<Map<String, Object>> stopMemoryLeak() {
        int leaked = failureSimulator.stopMemoryLeak();
        return ResponseEntity.ok(Map.of(
                "message", "Memory leak simulation stopped",
                "freedMB", leaked));
    }

    // ==================== Thread Explosion Simulation ====================

    /**
     * Start thread explosion simulation.
     */
    @PostMapping("/thread-explosion/start")
    @Operation(summary = "Start thread explosion", description = "Start creating many threads")
    public ResponseEntity<Map<String, Object>> startThreadExplosion(
            @RequestParam(defaultValue = "500") @Parameter(description = "Number of threads to create") int threadCount,
            @RequestParam(defaultValue = "30000") @Parameter(description = "Sleep time per thread in ms") int sleepMs) {

        log.warn("API: Starting thread explosion simulation: threads={}, sleepMs={}", threadCount, sleepMs);
        failureSimulator.startThreadExplosion(threadCount, sleepMs);

        return ResponseEntity.ok(Map.of(
                "message", "Thread explosion simulation started",
                "threadCount", threadCount,
                "sleepMs", sleepMs));
    }

    /**
     * Stop thread explosion simulation.
     */
    @PostMapping("/thread-explosion/stop")
    @Operation(summary = "Stop thread explosion", description = "Stop and interrupt all explosion threads")
    public ResponseEntity<Map<String, Object>> stopThreadExplosion() {
        int count = failureSimulator.stopThreadExplosion();
        return ResponseEntity.ok(Map.of(
                "message", "Thread explosion simulation stopped",
                "interruptedThreads", count));
    }

    // ==================== CPU Stress Simulation ====================

    /**
     * Start CPU stress simulation.
     */
    @PostMapping("/cpu-stress/start")
    @Operation(summary = "Start CPU stress", description = "Start CPU intensive operations")
    public ResponseEntity<Map<String, Object>> startCpuStress(
            @RequestParam(defaultValue = "0") @Parameter(description = "Number of stress threads (0 = auto)") int threads,
            @RequestParam(defaultValue = "30") @Parameter(description = "Duration in seconds") int durationSeconds) {

        log.warn("API: Starting CPU stress simulation: threads={}, duration={}s", threads, durationSeconds);
        failureSimulator.startCpuStress(threads, durationSeconds);

        return ResponseEntity.ok(Map.of(
                "message", "CPU stress simulation started",
                "threads", threads > 0 ? threads : Runtime.getRuntime().availableProcessors(),
                "durationSeconds", durationSeconds));
    }

    /**
     * Stop CPU stress simulation.
     */
    @PostMapping("/cpu-stress/stop")
    @Operation(summary = "Stop CPU stress", description = "Stop CPU stress threads")
    public ResponseEntity<Map<String, Object>> stopCpuStress() {
        int count = failureSimulator.stopCpuStress();
        return ResponseEntity.ok(Map.of(
                "message", "CPU stress simulation stopped",
                "stoppedThreads", count));
    }

    // ==================== I/O Bottleneck Simulation ====================

    /**
     * Start I/O bottleneck simulation.
     */
    @PostMapping("/io-bottleneck/start")
    @Operation(summary = "Start I/O bottleneck", description = "Start disk I/O intensive operations")
    public ResponseEntity<Map<String, Object>> startIoBottleneck(
            @RequestParam(defaultValue = "30") @Parameter(description = "Duration in seconds") int durationSeconds) {

        log.warn("API: Starting I/O bottleneck simulation: duration={}s", durationSeconds);
        failureSimulator.startIoBottleneck(durationSeconds);

        return ResponseEntity.ok(Map.of(
                "message", "I/O bottleneck simulation started",
                "durationSeconds", durationSeconds));
    }

    /**
     * Stop I/O bottleneck simulation.
     */
    @PostMapping("/io-bottleneck/stop")
    @Operation(summary = "Stop I/O bottleneck", description = "Stop I/O bottleneck simulation")
    public ResponseEntity<Map<String, String>> stopIoBottleneck() {
        failureSimulator.stopIoBottleneck();
        return ResponseEntity.ok(Map.of("message", "I/O bottleneck simulation stopped"));
    }
}
