/**
 * Custom React Hooks for AIOS Frontend
 * 
 * Central export file for all custom hooks
 */

// Data fetching hooks (REST API with React Query)
export {
  useMetrics,
  useIssues,
  useHighPriorityIssues,
  useActions,
  useDashboard,
  useSystemHealth,
  useActionStats,
} from './useMetrics';

// WebSocket hooks (Real-time streaming)
export {
  useWebSocket,
  useMetricsStream,
  useIssuesStream,
  useActionsStream,
} from './useWebSocket';

// Notification hooks
export { useNotifications } from './useNotifications';

// Process filtering hooks
export {
  useProcessFilter,
  useProcessFilterState,
} from './useProcessFilter';

// Pagination hooks
export { usePagination } from './usePagination';

// Common utility hooks
export {
  useDebounce,
  usePrevious,
  useLocalStorage,
  useInterval,
  useIsMounted,
  useWindowSize,
  useDocumentTitle,
} from './useCommon';

// AI Diagnosis hooks
export {
  useDiagnosis,
  useAutoDiagnosis,
  useNeedsDiagnosis,
  useDiagnosisHistory,
} from './useDiagnosis';
