package com.aios.backend.controller;

import com.aios.backend.service.LogService;
import com.aios.shared.dto.LogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Integration tests for LogController.
 * 
 * Tests log viewing endpoints with filtering, search, and pagination.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@DisplayName("LogController Integration Tests")
class LogControllerTest {

    private WebTestClient webTestClient;

    private LogService logService;

        @BeforeEach
        void setUp() {
                logService = org.mockito.Mockito.mock(LogService.class);
                LogController controller = new LogController(logService);
                webTestClient = WebTestClient.bindToController(controller).build();
        }

    private List<LogEntry> createSampleLogs() {
        return List.of(
                LogEntry.builder()
                        .timestamp(Instant.now().minusSeconds(60))
                        .level("INFO")
                        .logger("com.aios.backend.Application")
                        .message("Application started")
                        .thread("main")
                        .build(),
                LogEntry.builder()
                        .timestamp(Instant.now().minusSeconds(30))
                        .level("WARN")
                        .logger("com.aios.backend.service.IssueService")
                        .message("High memory usage detected")
                        .thread("monitor-1")
                        .build(),
                LogEntry.builder()
                        .timestamp(Instant.now())
                        .level("ERROR")
                        .logger("com.aios.backend.service.DiagnosisService")
                        .message("Failed to analyze issue")
                        .thread("worker-2")
                        .stackTrace("java.lang.RuntimeException: Analysis failed")
                        .build());
    }

    @Nested
    @DisplayName("Get Logs")
    class GetLogsTests {

        @Test
        @DisplayName("Should return log entries")
        void shouldReturnLogs() {
            // Given: Logs are available
            when(logService.getLogs(any(), any(), anyInt())).thenReturn(createSampleLogs());

            webTestClient.get()
                    .uri("/api/logs")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(LogEntry.class)
                    .hasSize(3);
        }

        @Test
        @DisplayName("Should filter by log level")
        void shouldFilterByLevel() {
            // Given: Only error logs returned when filtering
            List<LogEntry> errorLogs = List.of(
                    LogEntry.builder()
                            .timestamp(Instant.now())
                            .level("ERROR")
                            .logger("test")
                            .message("Error message")
                            .build());
            when(logService.getLogs(any(), any(), anyInt())).thenReturn(errorLogs);

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/logs")
                            .queryParam("level", "ERROR")
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(LogEntry.class)
                    .hasSize(1)
                    .value(logs -> logs
                            .forEach(log -> org.junit.jupiter.api.Assertions.assertEquals("ERROR", log.getLevel())));
        }

        @Test
        @DisplayName("Should support search parameter")
        void shouldSupportSearch() {
            // Given: Search returns specific logs
            List<LogEntry> searchResults = List.of(
                    LogEntry.builder()
                            .timestamp(Instant.now())
                            .level("WARN")
                            .logger("test")
                            .message("Memory usage warning")
                            .build());
            when(logService.getLogs(any(), any(), anyInt())).thenReturn(searchResults);

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/logs")
                            .queryParam("search", "memory")
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(LogEntry.class);
        }

        @Test
        @DisplayName("Should support limit parameter")
        void shouldSupportLimit() {
            // Given: Limited logs returned
            when(logService.getLogs(any(), any(), anyInt())).thenReturn(createSampleLogs().subList(0, 2));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/logs")
                            .queryParam("limit", 2)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(LogEntry.class)
                    .hasSize(2);
        }

        @Test
        @DisplayName("Should handle empty logs gracefully")
        void shouldHandleEmptyLogs() {
            // Given: No logs available
            when(logService.getLogs(any(), any(), anyInt())).thenReturn(List.of());

            webTestClient.get()
                    .uri("/api/logs")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(LogEntry.class)
                    .hasSize(0);
        }
    }

    @Nested
    @DisplayName("Log Entry Details")
    class LogEntryDetailsTests {

        @Test
        @DisplayName("Should include all log entry fields")
        void shouldIncludeAllFields() {
            // Given: Detailed log entry
            LogEntry detailedLog = LogEntry.builder()
                    .timestamp(Instant.now())
                    .level("ERROR")
                    .logger("com.aios.backend.service.DiagnosisService")
                    .message("Critical failure occurred")
                    .thread("worker-1")
                    .stackTrace(
                            "java.lang.OutOfMemoryError: Java heap space\n\tat com.example.Method.run(Method.java:42)")
                    .build();
            when(logService.getLogs(any(), any(), anyInt())).thenReturn(List.of(detailedLog));

            webTestClient.get()
                    .uri("/api/logs")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[0].timestamp").exists()
                    .jsonPath("$[0].level").isEqualTo("ERROR")
                    .jsonPath("$[0].logger").isEqualTo("com.aios.backend.service.DiagnosisService")
                    .jsonPath("$[0].message").isEqualTo("Critical failure occurred")
                    .jsonPath("$[0].thread").isEqualTo("worker-1")
                    .jsonPath("$[0].stackTrace").exists();
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle service exception gracefully")
        void shouldHandleServiceException() {
            // Given: Service throws exception
            when(logService.getLogs(any(), any(), anyInt()))
                    .thenThrow(new RuntimeException("Log file not accessible"));

            webTestClient.get()
                    .uri("/api/logs")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().is5xxServerError();
        }

        @Test
        @DisplayName("Should handle invalid level parameter")
        void shouldHandleInvalidLevel() {
            // Given: Invalid level returns empty
            when(logService.getLogs(any(), any(), anyInt())).thenReturn(List.of());

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/logs")
                            .queryParam("level", "INVALID_LEVEL")
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(LogEntry.class);
        }
    }
}
