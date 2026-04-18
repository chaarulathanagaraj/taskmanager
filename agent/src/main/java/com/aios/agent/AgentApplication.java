package com.aios.agent;

import com.aios.agent.config.AgentConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the AIOS Monitoring Agent.
 * 
 * This agent collects system metrics using OSHI, detects issues,
 * and can execute remediation actions on Windows processes.
 * 
 * Key Features:
 * - Real-time system metrics collection (CPU, memory, disk, network)
 * - Process monitoring and analysis
 * - Issue detection (memory leaks, thread explosions, hung processes)
 * - Automated remediation with safety controls
 * - Backend synchronization via REST API
 */
@SpringBootApplication
@EnableScheduling
@Slf4j
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(AgentApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        
        log.info("╔════════════════════════════════════════════════════════╗");
        log.info("║                                                        ║");
        log.info("║           🤖  AIOS MONITORING AGENT  🤖               ║");
        log.info("║                                                        ║");
        log.info("║        Windows System Monitor & Auto-Remediation      ║");
        log.info("║                                                        ║");
        log.info("╚════════════════════════════════════════════════════════╝");
        
        app.run(args);
    }

    /**
     * Display startup configuration information
     */
    @Bean
    public CommandLineRunner startupInfo(AgentConfiguration config) {
        return args -> {
            try {
                // Validate configuration
                config.validate();
                
                log.info("╔════════════════════════════════════════════════════════╗");
                log.info("║               AGENT CONFIGURATION                      ║");
                log.info("╠════════════════════════════════════════════════════════╣");
                log.info("║ Collection Interval:     {} seconds", String.format("%-27s", config.getCollectionIntervalSeconds()));
                log.info("║ Retention Period:        {} minutes", String.format("%-27s", config.getRetentionMinutes()));
                log.info("║ Max Snapshots:           {}", String.format("%-27s", config.getMaxMetricSnapshots()));
                log.info("║ Backend URL:             {}", String.format("%-27s", config.getBackendUrl()));
                log.info("║ Backend Sync:            {}", String.format("%-27s", config.isBackendSyncEnabled() ? "ENABLED" : "DISABLED"));
                log.info("║ Sync Interval:           {} seconds", String.format("%-27s", config.getBackendSyncIntervalSeconds()));
                log.info("╠════════════════════════════════════════════════════════╣");
                log.info("║ Dry-Run Mode:            {}", String.format("%-27s", config.isDryRunMode() ? "ENABLED ✓" : "DISABLED ✗"));
                log.info("║ Detection:               {}", String.format("%-27s", config.isDetectionEnabled() ? "ENABLED" : "DISABLED"));
                log.info("║ Detection Interval:      {} seconds", String.format("%-27s", config.getDetectionIntervalSeconds()));
                log.info("║ Monitored Processes:     {}", String.format("%-27s", config.getMonitoredProcessLimit()));
                log.info("║ Auto-Remediation:        {}", String.format("%-27s", config.isAutoRemediationEnabled() ? "ENABLED" : "DISABLED"));
                log.info("║ Confidence Threshold:    {}%", String.format("%-26s", (int)(config.getAutoRemediationConfidenceThreshold() * 100)));
                log.info("║ Max Concurrent Actions:  {}", String.format("%-27s", config.getMaxConcurrentActions()));
                log.info("╠════════════════════════════════════════════════════════╣");
                log.info("║ Protected Processes:     {} configured", String.format("%-27s", config.getProtectedProcesses().size()));
                
                // Display protected processes (first 5)
                int displayLimit = Math.min(5, config.getProtectedProcesses().size());
                for (int i = 0; i < displayLimit; i++) {
                    String prefix = i == 0 ? "   - " : "   - ";
                    log.info("║ {}{}",prefix, String.format("%-48s", config.getProtectedProcesses().get(i)));
                }
                if (config.getProtectedProcesses().size() > 5) {
                    log.info("║    ... and {} more", String.format("%-41s", config.getProtectedProcesses().size() - 5));
                }
                
                log.info("╚════════════════════════════════════════════════════════╝");
                
                // Safety warnings
                if (!config.isDryRunMode()) {
                    log.warn("⚠️  DRY-RUN MODE IS DISABLED - Remediation actions will be executed!");
                    log.warn("⚠️  Protected processes: {}", config.getProtectedProcesses());
                }
                
                if (config.isAutoRemediationEnabled()) {
                    log.warn("⚠️  AUTO-REMEDIATION ENABLED - Actions will execute automatically above {}% confidence",
                        (int)(config.getAutoRemediationConfidenceThreshold() * 100));
                }
                
                // Startup success
                log.info("✓ Agent started successfully");
                log.info("✓ System monitoring active");
                log.info("✓ Waiting for first metric collection cycle...");
                
            } catch (IllegalStateException e) {
                log.error("❌ Configuration validation failed: {}", e.getMessage());
                log.error("❌ Agent startup aborted");
                System.exit(1);
            } catch (Exception e) {
                log.error("❌ Unexpected error during startup", e);
                System.exit(1);
            }
        };
    }

    /**
     * Display shutdown information
     */
    @Bean
    public CommandLineRunner shutdownHook() {
        return args -> {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("╔════════════════════════════════════════════════════════╗");
                log.info("║          AIOS MONITORING AGENT SHUTTING DOWN          ║");
                log.info("╚════════════════════════════════════════════════════════╝");
                log.info("✓ Agent stopped gracefully");
            }));
        };
    }
}
