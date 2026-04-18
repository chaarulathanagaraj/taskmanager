package com.aios.backend;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Main Spring Boot application for AIOS Backend.
 * 
 * <p>
 * Provides REST API endpoints for system monitoring and diagnostics,
 * receiving data from the AIOS agent and serving it to the dashboard frontend.
 * 
 * <p>
 * Features:
 * <ul>
 * <li>REST API for metrics, issues, and actions</li>
 * <li>WebSocket support for real-time updates</li>
 * <li>JPA persistence with PostgreSQL/H2</li>
 * <li>OpenAPI documentation (Swagger UI)</li>
 * <li>Scheduled cleanup tasks</li>
 * <li>AI-powered diagnosis via LangChain4j agents</li>
 * </ul>
 * 
 * @author AIOS Team
 * @since 1.0
 */
@SpringBootApplication
@ComponentScan(basePackages = { "com.aios.backend", "com.aios.ai" })
@EntityScan(basePackages = { "com.aios.backend.model" })
@EnableJpaRepositories(basePackages = { "com.aios.backend.repository" })
@EnableScheduling
@EnableAsync
@Slf4j
public class BackendApplication {

    public static void main(String[] args) {
        try {
            ConfigurableApplicationContext context = SpringApplication.run(BackendApplication.class, args);
            Environment env = context.getEnvironment();
            logApplicationStartup(env);
        } catch (Exception e) {
            log.error("Failed to start AIOS Backend", e);
            System.exit(1);
        }
    }

    /**
     * Log application startup information.
     * Displays URLs for accessing the application and API documentation.
     */
    private static void logApplicationStartup(Environment env) {
        String protocol = "http";
        if (env.getProperty("server.ssl.key-store") != null) {
            protocol = "https";
        }

        String serverPort = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "");
        String hostAddress = "localhost";

        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.warn("Unable to determine host address", e);
        }

        log.info("\n----------------------------------------------------------\n\t" +
                "Application '{}' is running!\n\t" +
                "Local: \t\t{}://localhost:{}{}\n\t" +
                "External: \t{}://{}:{}{}\n\t" +
                "API Docs: \t{}://localhost:{}{}/swagger-ui.html\n\t" +
                "WebSocket: \t{}://localhost:{}{}/ws\n\t" +
                "Profile(s): {}\n" +
                "----------------------------------------------------------",
                env.getProperty("spring.application.name", "AIOS Backend"),
                protocol,
                serverPort,
                contextPath,
                protocol,
                hostAddress,
                serverPort,
                contextPath,
                protocol,
                serverPort,
                contextPath,
                protocol,
                serverPort,
                contextPath,
                env.getActiveProfiles().length == 0
                        ? env.getDefaultProfiles()
                        : env.getActiveProfiles());
    }
}
