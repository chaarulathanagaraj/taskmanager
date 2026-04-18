package com.aios.mcp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Filter for API key authentication.
 * Validates the X-API-Key header for protected MCP endpoints.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    // Paths that don't require authentication
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/actuator/health",
            "/actuator/info",
            "/api/mcp/tools" // Tool listing is public for discovery
    );

    @Value("${aios.mcp.api-key:}")
    private String configuredApiKey;

    @Value("${aios.mcp.auth.enabled:true}")
    private boolean authEnabled;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip authentication for public paths
        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip if auth is disabled
        if (!authEnabled || configuredApiKey.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Only protect MCP endpoints
        if (!path.startsWith("/api/mcp")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Validate API key
        String providedApiKey = request.getHeader(API_KEY_HEADER);

        if (providedApiKey == null || providedApiKey.isEmpty()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Missing API key\",\"message\":\"X-API-Key header is required\"}");
            return;
        }

        if (!configuredApiKey.equals(providedApiKey)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Invalid API key\",\"message\":\"The provided API key is not valid\"}");
            return;
        }

        // API key is valid, proceed
        filterChain.doFilter(request, response);
    }

    private boolean isPublicPath(String path) {
        // Exact match
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        // GET requests to /api/mcp/tools are public (listing tools)
        // POST requests (execute) require auth
        return false;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        // Allow GET requests to tool listing endpoints without auth
        if ("GET".equalsIgnoreCase(method) && path.equals("/api/mcp/tools")) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && path.equals("/api/mcp/categories")) {
            return true;
        }

        return false;
    }
}
