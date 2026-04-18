package com.aios.agent.service;

import com.aios.agent.AgentApplication;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonInitException;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Windows Service wrapper for AIOS Monitor Agent.
 * 
 * <p>
 * Uses Apache Commons Daemon (procrun) to run the agent as a Windows service.
 * This class implements the Daemon interface for proper service lifecycle
 * management.
 * 
 * <p>
 * Installation:
 * 
 * <pre>
 * prunsrv //IS//AIOSMonitor ^
 *   --DisplayName="AIOS Monitor" ^
 *   --Description="AI-powered Windows system monitor" ^
 *   --Startup=auto ^
 *   --Jvm=auto ^
 *   --Classpath="path\to\agent.jar" ^
 *   --StartMode=jvm ^
 *   --StartClass=com.aios.agent.service.WindowsService ^
 *   --StartMethod=start ^
 *   --StopMode=jvm ^
 *   --StopClass=com.aios.agent.service.WindowsService ^
 *   --StopMethod=stop ^
 *   --LogPath="path\to\logs" ^
 *   --LogPrefix=aios-service ^
 *   --StdOutput=auto ^
 *   --StdError=auto
 * </pre>
 * 
 * <p>
 * Management commands:
 * <ul>
 * <li>Install: prunsrv //IS//AIOSMonitor ...</li>
 * <li>Start: net start AIOSMonitor</li>
 * <li>Stop: net stop AIOSMonitor</li>
 * <li>Uninstall: prunsrv //DS//AIOSMonitor</li>
 * </ul>
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Slf4j
public class WindowsService implements Daemon {

    private static WindowsService instance;
    private static ConfigurableApplicationContext applicationContext;
    private static final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static String[] arguments;

    /**
     * Main entry point for service mode.
     * Called by procrun when starting as a Windows service.
     * 
     * @param args command line arguments
     */
    public static void main(String[] args) {
        if (args.length > 0) {
            String command = args[0].toLowerCase();
            switch (command) {
                case "start":
                    start(args);
                    break;
                case "stop":
                    stop(args);
                    break;
                case "install":
                    printInstallInstructions();
                    break;
                case "uninstall":
                    printUninstallInstructions();
                    break;
                default:
                    log.info("Usage: WindowsService [start|stop|install|uninstall]");
            }
        } else {
            // Default: start the service
            start(args);
        }
    }

    /**
     * Start the AIOS Monitor service.
     * Called by procrun or from command line.
     * 
     * @param args command line arguments
     */
    public static void start(String[] args) {
        if (running.getAndSet(true)) {
            log.warn("Service is already running");
            return;
        }

        log.info("Starting AIOS Monitor service...");
        arguments = args;

        try {
            // Start Spring Boot application
            applicationContext = SpringApplication.run(AgentApplication.class, args);

            log.info("AIOS Monitor service started successfully");

            // Register shutdown hook for graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutdown hook triggered");
                performShutdown();
            }));

            // Wait for shutdown signal (for service mode)
            try {
                shutdownLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Service interrupted");
            }

        } catch (Exception e) {
            log.error("Failed to start AIOS Monitor service", e);
            running.set(false);
            throw new RuntimeException("Service start failed", e);
        }
    }

    /**
     * Stop the AIOS Monitor service.
     * Called by procrun or from command line.
     * 
     * @param args command line arguments (ignored)
     */
    public static void stop(String[] args) {
        log.info("Stopping AIOS Monitor service...");
        performShutdown();
    }

    /**
     * Perform graceful shutdown of the application.
     */
    private static void performShutdown() {
        if (!running.getAndSet(false)) {
            log.debug("Service already stopped");
            return;
        }

        try {
            if (applicationContext != null && applicationContext.isActive()) {
                log.info("Closing Spring application context...");

                // Give services time to complete pending operations
                try {
                    TimeUnit.SECONDS.sleep(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                applicationContext.close();
                log.info("Spring application context closed");
            }
        } catch (Exception e) {
            log.error("Error during shutdown", e);
        } finally {
            shutdownLatch.countDown();
            log.info("AIOS Monitor service stopped");
        }
    }

    /**
     * Print service installation instructions.
     */
    private static void printInstallInstructions() {
        System.out.println("""
                AIOS Monitor Service Installation
                ==================================

                1. Download Apache Commons Daemon (procrun) from:
                   https://commons.apache.org/proper/commons-daemon/

                2. Extract prunsrv.exe to your installation directory

                3. Run the following command as Administrator:

                   prunsrv //IS//AIOSMonitor ^
                     --DisplayName="AIOS Monitor" ^
                     --Description="AI-powered Windows system monitor" ^
                     --Startup=auto ^
                     --Jvm=auto ^
                     --Classpath="%CD%\\agent.jar" ^
                     --StartMode=jvm ^
                     --StartClass=com.aios.agent.service.WindowsService ^
                     --StartMethod=start ^
                     --StartParams=start ^
                     --StopMode=jvm ^
                     --StopClass=com.aios.agent.service.WindowsService ^
                     --StopMethod=stop ^
                     --StopParams=stop ^
                     --LogPath="%CD%\\logs" ^
                     --LogPrefix=aios-service ^
                     --LogLevel=Info ^
                     --StdOutput=auto ^
                     --StdError=auto

                4. Start the service:
                   net start AIOSMonitor

                5. Verify the service is running:
                   sc query AIOSMonitor
                """);
    }

    /**
     * Print service uninstallation instructions.
     */
    private static void printUninstallInstructions() {
        System.out.println("""
                AIOS Monitor Service Uninstallation
                ====================================

                1. Stop the service (if running):
                   net stop AIOSMonitor

                2. Delete the service:
                   prunsrv //DS//AIOSMonitor

                Or using sc command:
                   sc delete AIOSMonitor
                """);
    }

    // ============================================
    // Daemon Interface Implementation
    // ============================================

    @Override
    public void init(DaemonContext context) throws DaemonInitException {
        log.info("Initializing AIOS Monitor daemon...");
        instance = this;
        arguments = context.getArguments();
    }

    @Override
    public void start() throws Exception {
        log.info("Starting AIOS Monitor daemon...");

        // Start in a separate thread for daemon mode
        Thread serviceThread = new Thread(() -> {
            try {
                WindowsService.start(arguments);
            } catch (Exception e) {
                log.error("Daemon start failed", e);
            }
        }, "aios-service-main");

        serviceThread.setDaemon(false);
        serviceThread.start();
    }

    @Override
    public void stop() throws Exception {
        log.info("Stopping AIOS Monitor daemon...");
        WindowsService.stop(arguments);
    }

    @Override
    public void destroy() {
        log.info("Destroying AIOS Monitor daemon...");
        instance = null;
    }

    // ============================================
    // Service Status Methods
    // ============================================

    /**
     * Check if the service is currently running.
     * 
     * @return true if service is running
     */
    public static boolean isRunning() {
        return running.get();
    }

    /**
     * Get the Spring application context.
     * 
     * @return the application context, or null if not started
     */
    public static ConfigurableApplicationContext getApplicationContext() {
        return applicationContext;
    }
}
