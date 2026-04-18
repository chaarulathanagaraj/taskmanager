package com.aios.cli.commands;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

/**
 * General CLI commands.
 */
@ShellComponent
public class GeneralCommands {

    @ShellMethod(key = "about", value = "Show information about AIOS CLI")
    public String about() {
        return """
                ╔══════════════════════════════════════════════════════════════╗
                ║                    AIOS CLI v1.0.0                           ║
                ║         AI-Powered Windows System Monitor                    ║
                ╚══════════════════════════════════════════════════════════════╝
                
                Command-line interface for testing AIOS components.
                
                Available command groups:
                  mcp-*       - MCP tool testing (mcp-process, mcp-top, mcp-io, etc.)
                  diagnose    - Full AI diagnosis on issues
                  analyze-*   - Individual agent analysis (analyze-memory, analyze-threads, analyze-io)
                  plan-*      - Remediation planning commands
                  validate-*  - Safety validation commands
                  test-*      - Predefined test scenarios
                
                Type 'help' for a list of all commands.
                """;
    }

    @ShellMethod(key = "status", value = "Check connection status to MCP and backend servers")
    public String status() {
        StringBuilder sb = new StringBuilder();
        sb.append("Service Status:\n\n");
        
        // Simple check - in production you'd actually ping the services
        sb.append("  MCP Server (localhost:8081):     ").append(checkService("http://localhost:8081")).append("\n");
        sb.append("  Backend (localhost:8080):        ").append(checkService("http://localhost:8080")).append("\n");
        
        return sb.toString();
    }

    private String checkService(String url) {
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) 
                new java.net.URL(url + "/actuator/health").openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            int code = conn.getResponseCode();
            conn.disconnect();
            return code == 200 ? "✓ ONLINE" : "⚠ DEGRADED (HTTP " + code + ")";
        } catch (Exception e) {
            return "✗ OFFLINE";
        }
    }
}
