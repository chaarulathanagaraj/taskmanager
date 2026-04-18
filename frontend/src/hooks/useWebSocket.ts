import { useEffect, useRef } from 'react';
import { wsClient } from '../api/websocket';
import type { MetricSnapshot, DiagnosticIssue, RemediationAction, IssueResolutionSummary, ExecutionUpdateMessage } from '../types';

/**
 * Hook to establish WebSocket connection
 */
export const useWebSocket = () => {
  const isConnecting = useRef(false);

  useEffect(() => {
    if (!isConnecting.current && !wsClient.isConnected()) {
      isConnecting.current = true;
      wsClient.connect(
        () => {
          console.log('[useWebSocket] Connected');
        },
        (error) => {
          console.error('[useWebSocket] Connection error:', error);
          isConnecting.current = false;
        }
      );
    }

    return () => {
      // Don't disconnect on unmount to allow other components to use it
      // wsClient.disconnect();
    };
  }, []);

  return wsClient;
};

/**
 * Hook to subscribe to real-time metrics
 */
export const useMetricsStream = (callback: (metric: MetricSnapshot) => void) => {
  const ws = useWebSocket();

  useEffect(() => {
    if (!ws.isConnected()) {
      console.log('[useMetricsStream] Waiting for connection...');
      return;
    }

    const unsubscribe = ws.subscribeToMetrics(callback);
    return unsubscribe;
  }, [ws, callback]);
};

/**
 * Hook to subscribe to real-time issues
 */
export const useIssuesStream = (callback: (issue: DiagnosticIssue) => void) => {
  const ws = useWebSocket();

  useEffect(() => {
    if (!ws.isConnected()) {
      console.log('[useIssuesStream] Waiting for connection...');
      return;
    }

    const unsubscribe = ws.subscribeToIssues(callback);
    return unsubscribe;
  }, [ws, callback]);
};

/**
 * Hook to subscribe to real-time actions
 */
export const useActionsStream = (callback: (action: RemediationAction) => void) => {
  const ws = useWebSocket();

  useEffect(() => {
    if (!ws.isConnected()) {
      console.log('[useActionsStream] Waiting for connection...');
      return;
    }

    const unsubscribe = ws.subscribeToActions(callback);
    return unsubscribe;
  }, [ws, callback]);
};

/**
 * Hook to subscribe to issue resolution updates.
 */
export const useResolvedIssuesStream = (callback: (resolution: IssueResolutionSummary) => void) => {
  const ws = useWebSocket();

  useEffect(() => {
    if (!ws.isConnected()) {
      console.log('[useResolvedIssuesStream] Waiting for connection...');
      return;
    }

    const unsubscribe = ws.subscribeToResolvedIssues(callback);
    return unsubscribe;
  }, [ws, callback]);
};

/**
 * Hook to subscribe to remediation execution updates.
 */
export const useExecutionUpdatesStream = (callback: (update: ExecutionUpdateMessage) => void) => {
  const ws = useWebSocket();

  useEffect(() => {
    if (!ws.isConnected()) {
      console.log('[useExecutionUpdatesStream] Waiting for connection...');
      return;
    }

    const unsubscribe = ws.subscribeToExecutionUpdates(callback);
    return unsubscribe;
  }, [ws, callback]);
};
