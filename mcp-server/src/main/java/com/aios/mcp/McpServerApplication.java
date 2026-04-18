package com.aios.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for AIOS MCP Tool Server
 */
@SpringBootApplication
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
        
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║   AIOS MCP Server Started (Port 8081) ║");
        System.out.println("╚════════════════════════════════════════╝");
    }
}
