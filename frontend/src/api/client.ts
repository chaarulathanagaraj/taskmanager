import axios from 'axios';
import { API_BASE_URL, REQUEST_TIMEOUT } from '../config/env';
import type {
  MetricSnapshot,
  DiagnosticIssue,
  RemediationAction,
  DashboardData,
  AgentSettings,
  LogEntry,
} from '../types';

/**
 * Axios client configuration for AIOS Backend API
 */
export const apiClient = axios.create({
  baseURL: `${API_BASE_URL}/api`,
  timeout: REQUEST_TIMEOUT,
  headers: {
    'Content-Type': 'application/json',
  },
});

/**
 * Request interceptor for logging
 */
apiClient.interceptors.request.use(
  (config) => {
    console.log(`[API] ${config.method?.toUpperCase()} ${config.url}`);
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

/**
 * Response interceptor for error handling
 */
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    console.error('[API Error]', error.message);
    if (error.response) {
      console.error('Status:', error.response.status);
      console.error('Data:', error.response.data);
    }
    return Promise.reject(error);
  }
);

/**
 * Metrics API endpoints
 */
export const metricsApi = {
  getRecent: (minutes: number = 10) =>
    apiClient.get<MetricSnapshot[]>(`/metrics/recent?minutes=${minutes}`),
  
  getLatest: (limit: number = 100) =>
    apiClient.get<MetricSnapshot[]>(`/metrics/latest?limit=${limit}`),
  
  getRange: (startTime: string, endTime: string) =>
    apiClient.get<MetricSnapshot[]>(`/metrics/range?startTime=${startTime}&endTime=${endTime}`),
  
  getStats: () =>
    apiClient.get(`/metrics/stats`),
};

/**
 * Issues API endpoints
 */
export const issuesApi = {
  getAll: () =>
    apiClient.get<DiagnosticIssue[]>('/issues'),
  
  getActive: () =>
    apiClient.get<DiagnosticIssue[]>('/issues/active'),
  
  getHighPriority: () =>
    apiClient.get<DiagnosticIssue[]>('/issues/high-priority'),
  
  getById: (id: number) =>
    apiClient.get<DiagnosticIssue>(`/issues/${id}`),
  
  getByType: (type: string) =>
    apiClient.get<DiagnosticIssue[]>(`/issues/type/${type}`),
  
  getBySeverity: (severity: string) =>
    apiClient.get<DiagnosticIssue[]>(`/issues/severity/${severity}`),
  
  getByProcess: (pid: number) =>
    apiClient.get<DiagnosticIssue[]>(`/issues/process/${pid}`),
  
  getEligibleForRemediation: (minConfidence: number = 0.85) =>
    apiClient.get<DiagnosticIssue[]>(`/issues/eligible-for-remediation?minConfidence=${minConfidence}`),
  
  resolveIssue: (id: number) =>
    apiClient.put(`/issues/${id}/resolve`),
  
  getActiveCount: () =>
    apiClient.get<number>('/issues/count/active'),
  
  getCountBySeverity: (severity: string) =>
    apiClient.get<number>(`/issues/count/severity/${severity}`),
};

/**
 * Actions API endpoints
 */
export const actionsApi = {
  execute: (data: { actionType: string; targetPid: number; dryRun: boolean }) =>
    apiClient.post(`/actions/execute`, data),

  getAll: () =>
    apiClient.get<RemediationAction[]>('/actions'),
  
  getHistory: (hours: number = 24) =>
    apiClient.get<RemediationAction[]>(`/actions/history?hours=${hours}`),
  
  getById: (id: number) =>
    apiClient.get<RemediationAction>(`/actions/${id}`),
  
  getByType: (type: string) =>
    apiClient.get<RemediationAction[]>(`/actions/type/${type}`),
  
  getByStatus: (status: string) =>
    apiClient.get<RemediationAction[]>(`/actions/status/${status}`),
  
  getByProcess: (pid: number) =>
    apiClient.get<RemediationAction[]>(`/actions/process/${pid}`),
  
  getByIssue: (issueId: number) =>
    apiClient.get<RemediationAction[]>(`/actions/issue/${issueId}`),
  
  getRealActions: () =>
    apiClient.get<RemediationAction[]>('/actions/real'),
  
  getReviewRequired: () =>
    apiClient.get<RemediationAction[]>('/actions/review-required'),
  
  getSuccessRate: (type: string, hours: number = 24) =>
    apiClient.get<number>(`/actions/success-rate/${type}?hours=${hours}`),
  
  getStats: (hours: number = 24) =>
    apiClient.get(`/actions/stats?hours=${hours}`),
};

/**
 * Dashboard API endpoints
 */
export const dashboardApi = {
  getData: () =>
    apiClient.get<DashboardData>('/dashboard'),
  
  getOverview: () =>
    apiClient.get('/dashboard/overview'),
  
  getHealth: () =>
    apiClient.get<string>('/dashboard/health'),
  
  getTrends: (hours: number = 24) =>
    apiClient.get(`/dashboard/trends?hours=${hours}`),
};

/**
 * Settings API endpoints
 */
export const settingsApi = {
  get: () =>
    apiClient.get<AgentSettings>('/settings'),
  
  update: (settings: AgentSettings) =>
    apiClient.post<AgentSettings>('/settings', settings),
  
  reset: () =>
    apiClient.post<AgentSettings>('/settings/reset'),
  
  getProtectedProcesses: () =>
    apiClient.get<string[]>('/settings/protected-processes'),
  
  addProtectedProcess: (processName: string) =>
    apiClient.post<AgentSettings>('/settings/protected-processes', { processName }),
  
  removeProtectedProcess: (processName: string) =>
    apiClient.delete<AgentSettings>(`/settings/protected-processes/${encodeURIComponent(processName)}`),
  
  isProcessProtected: (processName: string) =>
    apiClient.get<{ protected: boolean }>(`/settings/protected-processes/check/${encodeURIComponent(processName)}`),

  getRunningProcesses: () =>
    apiClient.get<Array<{ name: string; pid: number; cpu: number; memory: number }>>('/settings/running-processes'),
};

/**
 * Logs API endpoints
 */
export const logsApi = {
  getLogs: (level: string = 'ALL', search: string = '', limit: number = 100) =>
    apiClient.get<LogEntry[]>(`/logs?level=${level}&search=${encodeURIComponent(search)}&limit=${limit}`),
  
  getLevels: () =>
    apiClient.get<string[]>('/logs/levels'),
  
  getFiles: () =>
    apiClient.get<Array<{ name: string; size: number; lastModified: string }>>('/logs/files'),
};
/**
 * Processes API endpoints
 */
export const processesApi = {
  getAll: (sortBy: string = 'cpu', limit: number = 1000) =>
    apiClient.get(`/processes?sortBy=${sortBy}&limit=${limit}`),
  
  getById: (pid: number) =>
    apiClient.get(`/processes/${pid}`),
};
/**
 * Health API endpoints
 */
export const healthApi = {
  getHealth: () =>
    apiClient.get('/health'),
  
  getLiveness: () =>
    apiClient.get('/health/live'),
  
  getReadiness: () =>
    apiClient.get('/health/ready'),
};

/**
 * Default export for convenience
 */
export default apiClient;
