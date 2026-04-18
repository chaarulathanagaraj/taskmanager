package com.aios.backend.controller;

import com.aios.backend.service.PerformanceMetrics;
import org.junit.jupiter.api.BeforeEach;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.Mockito.when;

/**
 * Integration tests for PerformanceMetricsController.
 * 
 * Tests metrics API endpoints for retrieving performance data.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@DisplayName("PerformanceMetricsController Integration Tests")
class PerformanceMetricsControllerTest {

    private WebTestClient webTestClient;

    private PerformanceMetrics performanceMetrics;

    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        performanceMetrics = org.mockito.Mockito.mock(PerformanceMetrics.class);
        meterRegistry = org.mockito.Mockito.mock(MeterRegistry.class);
        PerformanceMetricsController controller = new PerformanceMetricsController(performanceMetrics, meterRegistry);
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Nested
    @DisplayName("Summary Endpoint")
    class SummaryEndpointTests {

        @Test
        @DisplayName("Should return metrics summary")
        void shouldReturnMetricsSummary() {
            // Given: Metrics are available
            PerformanceMetrics.MetricsSummary mockSummary = PerformanceMetrics.MetricsSummary.builder()
                    .totalDetections(100.0)
                    .totalResolutions(50.0)
                    .totalRemediationsSuccess(45.0)
                    .totalRemediationsFailure(5.0)
                    .activeIssues(10L)
                    .aiDiagnosisRequests(25.0)
                    .policyViolations(3.0)
                    .avgDetectionLatencyMs(123.45)
                    .avgRemediationLatencyMs(456.78)
                    .build();
            when(performanceMetrics.getSummary()).thenReturn(mockSummary);

            webTestClient.get()
                    .uri("/api/metrics/summary")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.totalDetections").isEqualTo(100.0)
                    .jsonPath("$.activeIssues").isEqualTo(10);
        }

        @Test
        @DisplayName("Should include all summary fields")
        void shouldIncludeAllFields() {
            PerformanceMetrics.MetricsSummary mockSummary = PerformanceMetrics.MetricsSummary.builder()
                    .totalDetections(42.0)
                    .totalRemediationsSuccess(15.0)
                    .activeIssues(5L)
                    .build();
            when(performanceMetrics.getSummary()).thenReturn(mockSummary);

            webTestClient.get()
                    .uri("/api/metrics/summary")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.totalDetections").isNumber()
                    .jsonPath("$.totalRemediationsSuccess").isNumber()
                    .jsonPath("$.activeIssues").isNumber();
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle null metrics gracefully")
        void shouldHandleNullMetrics() {
            when(performanceMetrics.getSummary()).thenReturn(null);

            webTestClient.get()
                    .uri("/api/metrics/summary")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk();
        }

        @Test
        @DisplayName("Should handle metrics service exception")
        void shouldHandleServiceException() {
            when(performanceMetrics.getSummary())
                    .thenThrow(new RuntimeException("Metrics unavailable"));

            webTestClient.get()
                    .uri("/api/metrics/summary")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().is5xxServerError();
        }
    }
}
