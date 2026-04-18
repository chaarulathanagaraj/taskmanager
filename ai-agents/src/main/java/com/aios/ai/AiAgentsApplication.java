package com.aios.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for AIOS AI Agents
 */
@SpringBootApplication
public class AiAgentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAgentsApplication.class, args);
        
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║   AIOS AI Agents Started              ║");
        System.out.println("╚════════════════════════════════════════╝");
    }
}
