package com.aios.backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for HealthController.
 * 
 * Tests health check endpoints for liveness, readiness, and comprehensive
 * status.
 * 
 * @author AIOS Team
 * @since 1.0
 */
@DisplayName("HealthController Integration Tests")
class HealthControllerTest {

    private WebTestClient webTestClient;

    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute("SELECT 1")).thenReturn(true);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("H2");
        when(metaData.getDatabaseProductVersion()).thenReturn("test");

        HealthController controller = new HealthController(dataSource);
        ReflectionTestUtils.setField(controller, "applicationName", "aios-backend-test");
        ReflectionTestUtils.setField(controller, "agentUrl", "http://localhost:18081");
        ReflectionTestUtils.setField(controller, "mcpUrl", "http://localhost:18082");
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Nested
    @DisplayName("Liveness Probe")
    class LivenessProbeTests {

        @Test
        @DisplayName("Should return 200 OK for liveness probe")
        void shouldReturnOkForLiveness() {
            webTestClient.get()
                    .uri("/api/health/live")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.status").isEqualTo("UP");
        }

        @Test
        @DisplayName("Should include timestamp in response")
        void shouldIncludeTimestamp() {
            webTestClient.get()
                    .uri("/api/health/live")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.timestamp").exists();
        }
    }

    @Nested
    @DisplayName("Readiness Probe")
    class ReadinessProbeTests {

        @Test
        @DisplayName("Should return 200 OK when all dependencies ready")
        void shouldReturnOkWhenReady() {
            webTestClient.get()
                    .uri("/api/health/ready")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.status").isEqualTo("READY");
        }
    }

    @Nested
    @DisplayName("Comprehensive Health Check")
    class ComprehensiveHealthTests {

        @Test
        @DisplayName("Should return detailed health status")
        void shouldReturnDetailedStatus() {
            webTestClient.get()
                    .uri("/api/health")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.status").exists()
                    .jsonPath("$.resources").exists();
        }

        @Test
        @DisplayName("Should include system resources in response")
        void shouldIncludeSystemResources() {
            webTestClient.get()
                    .uri("/api/health")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.resources.heapMemoryUsed").exists()
                    .jsonPath("$.resources.heapMemoryTotal").exists()
                    .jsonPath("$.resources.availableProcessors").exists();
        }

        @Test
        @DisplayName("Should include component health statuses")
        void shouldIncludeComponentHealth() {
            webTestClient.get()
                    .uri("/api/health")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.checks").exists();
        }
    }
}
