import { useQuery } from '@tanstack/react-query';
import { POLLING_INTERVALS } from '../config/env';
import { metricsApi, issuesApi, actionsApi, dashboardApi } from '../api/client';

/**
 * Hook to fetch recent metrics
 */
export const useMetrics = (minutes: number = 10) => {
  return useQuery({
    queryKey: ['metrics', minutes],
    queryFn: () => metricsApi.getRecent(minutes).then((res) => res.data),
    refetchInterval: POLLING_INTERVALS.METRICS,
  });
};

/**
 * Hook to fetch active issues
 */
export const useIssues = () => {
  return useQuery({
    queryKey: ['issues'],
    queryFn: () => issuesApi.getActive().then((res) => res.data),
    refetchInterval: POLLING_INTERVALS.ISSUES,
  });
};

/**
 * Hook to fetch high priority issues
 */
export const useHighPriorityIssues = () => {
  return useQuery({
    queryKey: ['issues', 'high-priority'],
    queryFn: () => issuesApi.getHighPriority().then((res) => res.data),
    refetchInterval: POLLING_INTERVALS.ISSUES,
  });
};

/**
 * Hook to fetch action history
 */
export const useActions = (hours: number = 24) => {
  return useQuery({
    queryKey: ['actions', hours],
    queryFn: () => actionsApi.getHistory(hours).then((res) => res.data),
    refetchInterval: POLLING_INTERVALS.ACTIONS,
  });
};

/**
 * Hook to fetch complete dashboard data
 */
export const useDashboard = () => {
  return useQuery({
    queryKey: ['dashboard'],
    queryFn: () => dashboardApi.getData().then((res) => res.data),
    refetchInterval: POLLING_INTERVALS.METRICS,
  });
};

/**
 * Hook to fetch system health status
 */
export const useSystemHealth = () => {
  return useQuery({
    queryKey: ['health'],
    queryFn: () => dashboardApi.getHealth().then((res) => res.data),
    refetchInterval: POLLING_INTERVALS.HEALTH,
  });
};

/**
 * Hook to fetch action statistics
 */
export const useActionStats = (hours: number = 24) => {
  return useQuery({
    queryKey: ['action-stats', hours],
    queryFn: () => actionsApi.getStats(hours).then((res) => res.data),
    refetchInterval: POLLING_INTERVALS.HEALTH,
  });
};
