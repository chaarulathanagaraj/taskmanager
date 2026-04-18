package com.aios.cli;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CLI application for testing AIOS components.
 * Provides interactive commands for MCP tools and AI diagnosis.
 */
@SpringBootApplication(scanBasePackages = {"com.aios.cli", "com.aios.ai"})
public class CliApplication {

    public static void main(String[] args) {
        SpringApplication.run(CliApplication.class, args);
    }
}
