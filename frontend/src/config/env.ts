/**
 * Environment configuration
 */

// API Base URL
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

// WebSocket URL
export const WS_BASE_URL = import.meta.env.VITE_WS_BASE_URL || 'http://localhost:8080';

// API endpoints
export const API_ENDPOINTS = {
  METRICS: `${API_BASE_URL}/api/metrics`,
  ISSUES: `${API_BASE_URL}/api/issues`,
  ACTIONS: `${API_BASE_URL}/api/actions`,
  DASHBOARD: `${API_BASE_URL}/api/dashboard`,
};

// WebSocket topics
export const WS_TOPICS = {
  METRICS: '/topic/metrics',
  ISSUES: '/topic/issues',
  ACTIONS: '/topic/actions',
};

// Polling intervals (milliseconds)
export const POLLING_INTERVALS = {
  METRICS: 10000, // 10 seconds
  ISSUES: 10000, // 10 seconds
  ACTIONS: 10000, // 10 seconds
  HEALTH: 30000, // 30 seconds
};

// Request timeout
export const REQUEST_TIMEOUT = 60000; // 60 seconds (diagnosis can take 30-40s due to MCP tool calls)

// WebSocket reconnect settings
export const WS_RECONNECT = {
  MAX_ATTEMPTS: 5,
  DELAY: 3000, // 3 seconds
};

// Chart settings
export const CHART_CONFIG = {
  METRIC_HISTORY_MINUTES: 10,
  MAX_DATA_POINTS: 100,
};
