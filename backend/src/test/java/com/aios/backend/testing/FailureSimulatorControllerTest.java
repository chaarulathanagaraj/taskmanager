package com.aios.backend.testing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for FailureSimulatorController.
 * 
 * Tests failure simulation endpoints for memory leaks, thread explosion,
 * CPU stress, and I/O bottleneck scenarios.
 * 
 * Note: These tests verify the API behavior, not the actual simulations
 * (which could affect test stability).
 * 
 * @author AIOS Team
 * @since 1.0
 */
@DisplayName("FailureSimulatorController Integration Tests")
class FailureSimulatorControllerTest {

    private WebTestClient webTestClient;

    private FailureSimulator failureSimulator;

    @BeforeEach
    void setUp() {
        failureSimulator = new FailureSimulator();
        FailureSimulatorController controller = new FailureSimulatorController(failureSimulator);
        webTestClient = WebTestClient.bindToController(controller).build();
        // Ensure all simulations are stopped before each test
        failureSimulator.stopAll();
    }

    @Nested
    @DisplayName("Status Endpoint")
    class StatusEndpointTests {

        @Test
        @DisplayName("Should return current simulation status")
        void shouldReturnStatus() {
            webTestClient.get()
                    .uri("/api/test/simulate/status")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.memoryLeakActive").isBoolean()
                    .jsonPath("$.threadExplosionActive").isBoolean()
                    .jsonPath("$.cpuStressActive").isBoolean()
                    .jsonPath("$.ioBottleneckActive").isBoolean();
        }

        @Test
        @DisplayName("Should show all simulations inactive initially")
        void shouldShowAllInactive() {
            webTestClient.get()
                    .uri("/api/test/simulate/status")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.memoryLeakActive").isEqualTo(false)
                    .jsonPath("$.threadExplosionActive").isEqualTo(false)
                    .jsonPath("$.cpuStressActive").isEqualTo(false)
                    .jsonPath("$.ioBottleneckActive").isEqualTo(false);
        }
    }

    @Nested
    @DisplayName("Stop All Endpoint")
    class StopAllEndpointTests {

        @Test
        @DisplayName("Should stop all simulations")
        void shouldStopAllSimulations() {
            webTestClient.post()
                    .uri("/api/test/simulate/stop-all")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.message").isEqualTo("All simulations stopped");
        }
    }

    @Nested
    @DisplayName("Memory Leak Simulation")
    class MemoryLeakSimulationTests {

        @Test
        @DisplayName("Should start memory leak simulation")
        void shouldStartMemoryLeak() {
            webTestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/test/simulate/memory-leak/start")
                            .queryParam("maxMB", 5)
                            .queryParam("intervalMs", 500)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.message").isEqualTo("Memory leak simulation started")
                    .jsonPath("$.maxMB").isEqualTo(5)
                    .jsonPath("$.intervalMs").isEqualTo(500);

            // Stop simulation after test
            failureSimulator.stopMemoryLeak();
        }

        @Test
        @DisplayName("Should stop memory leak simulation")
        void shouldStopMemoryLeak() {
            // First start it
            failureSimulator.startMemoryLeak(5, 500);

            webTestClient.post()
                    .uri("/api/test/simulate/memory-leak/stop")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.message").isEqualTo("Memory leak simulation stopped")
                    .jsonPath("$.freedMB").isNumber();
        }
    }

    @Nested
    @DisplayName("Thread Explosion Simulation")
    class ThreadExplosionSimulationTests {

        @Test
        @DisplayName("Should start thread explosion simulation")
        void shouldStartThreadExplosion() {
            webTestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/test/simulate/thread-explosion/start")
                            .queryParam("threadCount", 10)
                            .queryParam("sleepMs", 1000)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.message").isEqualTo("Thread explosion simulation started")
                    .jsonPath("$.threadCount").isEqualTo(10);

            // Stop simulation after test
            failureSimulator.stopThreadExplosion();
        }

        @Test
        @DisplayName("Should stop thread explosion simulation")
        void shouldStopThreadExplosion() {
            // First start it
            failureSimulator.startThreadExplosion(10, 5000);

            webTestClient.post()
                    .uri("/api/test/simulate/thread-explosion/stop")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.message").isEqualTo("Thread explosion simulation stopped")
                    .jsonPath("$.interruptedThreads").isNumber();
        }
    }

    @Nested
    @DisplayName("CPU Stress Simulation")
    class CpuStressSimulationTests {

        @Test
        @DisplayName("Should start CPU stress simulation")
        void shouldStartCpuStress() {
            webTestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/test/simulate/cpu-stress/start")
                            .queryParam("threads", 2)
                            .queryParam("durationSeconds", 1)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.message").isEqualTo("CPU stress simulation started")
                    .jsonPath("$.threads").isEqualTo(2)
                    .jsonPath("$.durationSeconds").isEqualTo(1);

            // Stop simulation after test
            failureSimulator.stopCpuStress();
        }

        @Test
        @DisplayName("Should stop CPU stress simulation")
        void shouldStopCpuStress() {
            // First start it
            failureSimulator.startCpuStress(2, 5);

            webTestClient.post()
                    .uri("/api/test/simulate/cpu-stress/stop")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.message").isEqualTo("CPU stress simulation stopped")
                    .jsonPath("$.stoppedThreads").isNumber();
        }
    }

    @Nested
    @DisplayName("I/O Bottleneck Simulation")
    class IoBottleneckSimulationTests {

        @Test
        @DisplayName("Should start I/O bottleneck simulation")
        void shouldStartIoBottleneck() {
            webTestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/test/simulate/io-bottleneck/start")
                            .queryParam("durationSeconds", 1)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.message").isEqualTo("I/O bottleneck simulation started")
                    .jsonPath("$.durationSeconds").isEqualTo(1);

            // Stop simulation after test
            failureSimulator.stopIoBottleneck();
        }

        @Test
        @DisplayName("Should stop I/O bottleneck simulation")
        void shouldStopIoBottleneck() {
            // First start it
            failureSimulator.startIoBottleneck(5);

            webTestClient.post()
                    .uri("/api/test/simulate/io-bottleneck/stop")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.message").isEqualTo("I/O bottleneck simulation stopped");
        }
    }

    @Nested
    @DisplayName("Default Parameters")
    class DefaultParametersTests {

        @Test
        @DisplayName("Should use default values when parameters not provided")
        void shouldUseDefaults() {
            webTestClient.post()
                    .uri("/api/test/simulate/memory-leak/start")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.maxMB").isEqualTo(100)
                    .jsonPath("$.intervalMs").isEqualTo(1000);

            // Stop simulation after test
            failureSimulator.stopMemoryLeak();
        }
    }
}
