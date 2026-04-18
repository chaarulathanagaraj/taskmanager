package com.aios.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket configuration for real-time updates.
 * 
 * <p>Enables STOMP over WebSocket for bidirectional communication
 * between the backend and frontend dashboard.
 * 
 * <p>Supported channels:
 * <ul>
 *   <li>/topic/metrics - Real-time system metrics updates</li>
 *   <li>/topic/issues - New issue notifications</li>
 *   <li>/topic/actions - Action execution updates</li>
 *   <li>/topic/alerts - Critical alerts</li>
 * </ul>
 * 
 * <p>Example usage from frontend:
 * <pre>{@code
 * const stompClient = new StompJs.Client({
 *   brokerURL: 'ws://localhost:8080/ws',
 *   onConnect: () => {
 *     stompClient.subscribe('/topic/metrics', (message) => {
 *       console.log('Received:', JSON.parse(message.body));
 *     });
 *   }
 * });
 * stompClient.activate();
 * }</pre>
 * 
 * @author AIOS Team
 * @since 1.0
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Configure message broker options.
     * 
     * <p>Sets up:
     * <ul>
     *   <li>Simple in-memory broker for /topic destinations</li>
     *   <li>Application destination prefix /app for client messages</li>
     * </ul>
     * 
     * @param config the message broker registry
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple in-memory message broker for pub/sub
        // Messages sent to /topic/* will be broadcast to all subscribers
        config.enableSimpleBroker("/topic");
        
        // Messages sent from client to /app/* will be routed to @MessageMapping methods
        config.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Register STOMP endpoints that clients connect to.
     * 
     * <p>Registers /ws endpoint with:
     * <ul>
     *   <li>SockJS fallback for browsers without WebSocket support</li>
     *   <li>CORS allowed from all origins (configure for production)</li>
     * </ul>
     * 
     * @param registry the STOMP endpoint registry
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // Allow all origins (configure for production)
                .withSockJS();  // Enable SockJS fallback for browsers without WebSocket
    }
}
