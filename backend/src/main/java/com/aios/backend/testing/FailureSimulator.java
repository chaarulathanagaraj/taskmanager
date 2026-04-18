package com.aios.backend.testing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for simulating various failure scenarios for testing.
 * 
 * <p>
 * WARNING: This service is for testing purposes only.
 * It can create resource-intensive conditions that may affect system stability.
 * Only enable in development/testing environments.
 * 
 * <p>
 * Provides simulation of:
 * - Memory leaks (gradual memory consumption)
 * - Thread explosion (rapid thread creation)
 * - CPU stress (high CPU usage)
 * - I/O bottleneck (disk thrashing)
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Service
@Slf4j
public class FailureSimulator {

    // Track active simulations
    private final AtomicBoolean memoryLeakActive = new AtomicBoolean(false);
    private final AtomicBoolean threadExplosionActive = new AtomicBoolean(false);
    private final AtomicBoolean cpuStressActive = new AtomicBoolean(false);
    private final AtomicBoolean ioBottleneckActive = new AtomicBoolean(false);

    // Memory leak storage
    private final List<byte[]> leakedMemory = new CopyOnWriteArrayList<>();

    // Thread explosion storage
    private final List<Thread> explosionThreads = new CopyOnWriteArrayList<>();

    // CPU stress threads
    private final List<Thread> cpuStressThreads = new CopyOnWriteArrayList<>();

    // Counters
    private final AtomicInteger memoryLeakMB = new AtomicInteger(0);
    private final AtomicInteger threadCount = new AtomicInteger(0);

    /**
     * Start simulating a memory leak.
     * Allocates 1 MB per second until stopped or max reached.
     * 
     * @param maxMB      maximum megabytes to leak (default: 100)
     * @param intervalMs interval between allocations in ms (default: 1000)
     */
    public void startMemoryLeak(int maxMB, int intervalMs) {
        if (memoryLeakActive.getAndSet(true)) {
            log.warn("Memory leak simulation already active");
            return;
        }

        int max = maxMB > 0 ? maxMB : 100;
        int interval = intervalMs > 0 ? intervalMs : 1000;

        log.warn("STARTING MEMORY LEAK SIMULATION: max={}MB, interval={}ms", max, interval);

        Thread leakThread = new Thread(() -> {
            try {
                while (memoryLeakActive.get() && memoryLeakMB.get() < max) {
                    byte[] chunk = new byte[1024 * 1024]; // 1 MB
                    // Fill with data to ensure it's not optimized away
                    for (int i = 0; i < chunk.length; i += 4096) {
                        chunk[i] = (byte) (i % 256);
                    }
                    leakedMemory.add(chunk);
                    int current = memoryLeakMB.incrementAndGet();
                    log.info("Memory leak: allocated {} MB", current);
                    Thread.sleep(interval);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Memory leak simulation interrupted");
            }
            log.info("Memory leak simulation ended at {} MB", memoryLeakMB.get());
        }, "aios-memory-leak-simulator");

        leakThread.setDaemon(true);
        leakThread.start();
    }

    /**
     * Stop memory leak simulation and release memory.
     * 
     * @return number of MB that was leaked
     */
    public int stopMemoryLeak() {
        int leaked = memoryLeakMB.getAndSet(0);
        memoryLeakActive.set(false);
        leakedMemory.clear();
        System.gc();
        log.info("Memory leak simulation stopped, freed {} MB", leaked);
        return leaked;
    }

    /**
     * Start simulating thread explosion.
     * Creates many threads rapidly.
     * 
     * @param threadCount number of threads to create (default: 500)
     * @param sleepMs     how long each thread sleeps (default: 30000)
     */
    public void startThreadExplosion(int threadCount, int sleepMs) {
        if (threadExplosionActive.getAndSet(true)) {
            log.warn("Thread explosion simulation already active");
            return;
        }

        int count = threadCount > 0 ? threadCount : 500;
        int sleep = sleepMs > 0 ? sleepMs : 30000;

        log.warn("STARTING THREAD EXPLOSION SIMULATION: threads={}, sleepMs={}", count, sleep);

        for (int i = 0; i < count; i++) {
            final int threadNum = i;
            Thread t = new Thread(() -> {
                try {
                    this.threadCount.incrementAndGet();
                    Thread.sleep(sleep);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                this.threadCount.decrementAndGet();
            }, "aios-thread-explosion-" + threadNum);

            t.setDaemon(true);
            t.start();
            explosionThreads.add(t);
        }

        log.info("Thread explosion: created {} threads", count);
    }

    /**
     * Stop thread explosion simulation.
     * 
     * @return number of threads that were created
     */
    public int stopThreadExplosion() {
        int count = explosionThreads.size();
        threadExplosionActive.set(false);

        for (Thread t : explosionThreads) {
            t.interrupt();
        }
        explosionThreads.clear();
        threadCount.set(0);

        log.info("Thread explosion simulation stopped, interrupted {} threads", count);
        return count;
    }

    /**
     * Start simulating CPU stress.
     * Creates threads that consume CPU cycles.
     * 
     * @param threads         number of stress threads (default: available
     *                        processors)
     * @param durationSeconds how long to run (default: 30)
     */
    public void startCpuStress(int threads, int durationSeconds) {
        if (cpuStressActive.getAndSet(true)) {
            log.warn("CPU stress simulation already active");
            return;
        }

        int numThreads = threads > 0 ? threads : Runtime.getRuntime().availableProcessors();
        int duration = durationSeconds > 0 ? durationSeconds : 30;

        log.warn("STARTING CPU STRESS SIMULATION: threads={}, duration={}s", numThreads, duration);

        for (int i = 0; i < numThreads; i++) {
            final int threadNum = i;
            Thread t = new Thread(() -> {
                long endTime = System.currentTimeMillis() + (duration * 1000L);
                while (cpuStressActive.get() && System.currentTimeMillis() < endTime) {
                    // CPU intensive calculation
                    double result = 0;
                    for (int j = 0; j < 10_000_000; j++) {
                        result += Math.sin(j) * Math.cos(j);
                    }
                    // Prevent optimization
                    if (result == Double.MAX_VALUE) {
                        log.trace("Unreachable: {}", result);
                    }
                }
                log.debug("CPU stress thread {} finished", threadNum);
            }, "aios-cpu-stress-" + threadNum);

            t.setDaemon(true);
            t.start();
            cpuStressThreads.add(t);
        }

        // Auto-stop after duration
        Thread stopper = new Thread(() -> {
            try {
                Thread.sleep(duration * 1000L + 1000);
                if (cpuStressActive.get()) {
                    stopCpuStress();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "aios-cpu-stress-stopper");
        stopper.setDaemon(true);
        stopper.start();
    }

    /**
     * Stop CPU stress simulation.
     * 
     * @return number of stress threads that were running
     */
    public int stopCpuStress() {
        int count = cpuStressThreads.size();
        cpuStressActive.set(false);

        for (Thread t : cpuStressThreads) {
            t.interrupt();
        }
        cpuStressThreads.clear();

        log.info("CPU stress simulation stopped, interrupted {} threads", count);
        return count;
    }

    /**
     * Start simulating I/O bottleneck.
     * Creates disk I/O pressure by reading/writing temp files.
     * 
     * @param durationSeconds how long to run (default: 30)
     */
    public void startIoBottleneck(int durationSeconds) {
        if (ioBottleneckActive.getAndSet(true)) {
            log.warn("I/O bottleneck simulation already active");
            return;
        }

        int duration = durationSeconds > 0 ? durationSeconds : 30;

        log.warn("STARTING I/O BOTTLENECK SIMULATION: duration={}s", duration);

        Thread ioThread = new Thread(() -> {
            java.io.File tempFile = null;
            try {
                tempFile = java.io.File.createTempFile("aios-io-test-", ".tmp");
                tempFile.deleteOnExit();

                long endTime = System.currentTimeMillis() + (duration * 1000L);
                byte[] buffer = new byte[1024 * 1024]; // 1 MB buffer

                while (ioBottleneckActive.get() && System.currentTimeMillis() < endTime) {
                    // Write
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile)) {
                        for (int i = 0; i < 10; i++) {
                            fos.write(buffer);
                        }
                        fos.flush();
                    }

                    // Read
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(tempFile)) {
                        while (fis.read(buffer) != -1) {
                            // Just read
                        }
                    }
                }
            } catch (Exception e) {
                log.error("I/O bottleneck simulation error", e);
            } finally {
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
                ioBottleneckActive.set(false);
            }
            log.info("I/O bottleneck simulation ended");
        }, "aios-io-bottleneck-simulator");

        ioThread.setDaemon(true);
        ioThread.start();
    }

    /**
     * Stop I/O bottleneck simulation.
     */
    public void stopIoBottleneck() {
        ioBottleneckActive.set(false);
        log.info("I/O bottleneck simulation stopped");
    }

    /**
     * Stop all active simulations.
     */
    public void stopAll() {
        stopMemoryLeak();
        stopThreadExplosion();
        stopCpuStress();
        stopIoBottleneck();
        log.info("All failure simulations stopped");
    }

    /**
     * Get current simulation status.
     */
    public SimulationStatus getStatus() {
        return SimulationStatus.builder()
                .memoryLeakActive(memoryLeakActive.get())
                .memoryLeakMB(memoryLeakMB.get())
                .threadExplosionActive(threadExplosionActive.get())
                .threadCount(threadCount.get())
                .cpuStressActive(cpuStressActive.get())
                .cpuStressThreads(cpuStressThreads.size())
                .ioBottleneckActive(ioBottleneckActive.get())
                .build();
    }

    /**
     * Status DTO for active simulations.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class SimulationStatus {
        private boolean memoryLeakActive;
        private int memoryLeakMB;
        private boolean threadExplosionActive;
        private int threadCount;
        private boolean cpuStressActive;
        private int cpuStressThreads;
        private boolean ioBottleneckActive;
    }
}
