import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { WS_BASE_URL, WS_RECONNECT } from '../config/env';
import type { MetricSnapshot, DiagnosticIssue, RemediationAction, IssueResolutionSummary, ExecutionUpdateMessage } from '../types';

/**
 * WebSocket client for real-time updates from backend
 */
export class WebSocketClient {
  private client: Client | null = null;
  private connected = false;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = WS_RECONNECT.MAX_ATTEMPTS;
  private reconnectDelay = WS_RECONNECT.DELAY;

  constructor(private baseUrl: string = WS_BASE_URL) {}

  /**
   * Connect to WebSocket server
   */
  connect(onConnected?: () => void, onError?: (error: any) => void): void {
    if (this.connected) {
      console.log('[WebSocket] Already connected');
      return;
    }

    this.client = new Client({
      webSocketFactory: () => new SockJS(`${this.baseUrl}/ws`) as any,
      debug: (str) => {
        console.log('[WebSocket Debug]', str);
      },
      reconnectDelay: this.reconnectDelay,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        console.log('[WebSocket] Connected successfully');
        this.connected = true;
        this.reconnectAttempts = 0;
        onConnected?.();
      },
      onStompError: (frame) => {
        console.error('[WebSocket] STOMP error:', frame.headers['message']);
        console.error('[WebSocket] Details:', frame.body);
        onError?.(frame);
      },
      onWebSocketClose: () => {
        console.log('[WebSocket] Connection closed');
        this.connected = false;
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
          this.reconnectAttempts++;
          console.log(
            `[WebSocket] Reconnect attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts}`
          );
        }
      },
    });

    this.client.activate();
  }

  /**
   * Disconnect from WebSocket server
   */
  disconnect(): void {
    if (this.client) {
      this.client.deactivate();
      this.connected = false;
      console.log('[WebSocket] Disconnected');
    }
  }

  /**
   * Subscribe to metric updates
   */
  subscribeToMetrics(callback: (metric: MetricSnapshot) => void): () => void {
    if (!this.client || !this.connected) {
      console.warn('[WebSocket] Not connected, cannot subscribe to metrics');
      return () => {};
    }

    const subscription = this.client.subscribe('/topic/metrics', (message) => {
      try {
        const metric: MetricSnapshot = JSON.parse(message.body);
        callback(metric);
      } catch (error) {
        console.error('[WebSocket] Failed to parse metric:', error);
      }
    });

    console.log('[WebSocket] Subscribed to /topic/metrics');

    return () => {
      subscription.unsubscribe();
      console.log('[WebSocket] Unsubscribed from /topic/metrics');
    };
  }

  /**
   * Subscribe to issue updates
   */
  subscribeToIssues(callback: (issue: DiagnosticIssue) => void): () => void {
    if (!this.client || !this.connected) {
      console.warn('[WebSocket] Not connected, cannot subscribe to issues');
      return () => {};
    }

    const subscription = this.client.subscribe('/topic/issues', (message) => {
      try {
        const issue: DiagnosticIssue = JSON.parse(message.body);
        callback(issue);
      } catch (error) {
        console.error('[WebSocket] Failed to parse issue:', error);
      }
    });

    console.log('[WebSocket] Subscribed to /topic/issues');

    return () => {
      subscription.unsubscribe();
      console.log('[WebSocket] Unsubscribed from /topic/issues');
    };
  }

  /**
   * Subscribe to action updates
   */
  subscribeToActions(callback: (action: RemediationAction) => void): () => void {
    if (!this.client || !this.connected) {
      console.warn('[WebSocket] Not connected, cannot subscribe to actions');
      return () => {};
    }

    const subscription = this.client.subscribe('/topic/actions', (message) => {
      try {
        const action: RemediationAction = JSON.parse(message.body);
        callback(action);
      } catch (error) {
        console.error('[WebSocket] Failed to parse action:', error);
      }
    });

    console.log('[WebSocket] Subscribed to /topic/actions');

    return () => {
      subscription.unsubscribe();
      console.log('[WebSocket] Unsubscribed from /topic/actions');
    };
  }

  /**
   * Subscribe to issue resolution updates.
   */
  subscribeToResolvedIssues(callback: (resolution: IssueResolutionSummary) => void): () => void {
    if (!this.client || !this.connected) {
      console.warn('[WebSocket] Not connected, cannot subscribe to resolved issues');
      return () => {};
    }

    const subscription = this.client.subscribe('/topic/issues/resolved', (message) => {
      try {
        const resolution: IssueResolutionSummary = JSON.parse(message.body);
        callback(resolution);
      } catch (error) {
        console.error('[WebSocket] Failed to parse issue resolution:', error);
      }
    });

    console.log('[WebSocket] Subscribed to /topic/issues/resolved');

    return () => {
      subscription.unsubscribe();
      console.log('[WebSocket] Unsubscribed from /topic/issues/resolved');
    };
  }

  /**
   * Subscribe to remediation execution updates.
   */
  subscribeToExecutionUpdates(callback: (update: ExecutionUpdateMessage) => void): () => void {
    if (!this.client || !this.connected) {
      console.warn('[WebSocket] Not connected, cannot subscribe to execution updates');
      return () => {};
    }

    const subscription = this.client.subscribe('/topic/executions', (message) => {
      try {
        const update: ExecutionUpdateMessage = JSON.parse(message.body);
        callback(update);
      } catch (error) {
        console.error('[WebSocket] Failed to parse execution update:', error);
      }
    });

    console.log('[WebSocket] Subscribed to /topic/executions');

    return () => {
      subscription.unsubscribe();
      console.log('[WebSocket] Unsubscribed from /topic/executions');
    };
  }

  /**
   * Check if WebSocket is connected
   */
  isConnected(): boolean {
    return this.connected;
  }

  /**
   * Get the underlying STOMP client
   */
  getClient(): Client | null {
    return this.client;
  }
}

/**
 * Singleton WebSocket client instance
 */
export const wsClient = new WebSocketClient();
