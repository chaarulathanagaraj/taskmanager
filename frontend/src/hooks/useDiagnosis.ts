import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import type { CompleteDiagnosisReport } from '../types';

/**
 * Hook for triggering AI diagnosis on an issue
 */
export const useDiagnosis = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (issueId: number) =>
      apiClient.post<CompleteDiagnosisReport>(`/diagnose/${issueId}`).then(res => res.data),
    onSuccess: () => {
      // Invalidate issues to refresh with updated confidence
      queryClient.invalidateQueries({ queryKey: ['issues'] });
    },
  });
};

/**
 * Hook for auto-diagnosis (only triggers if confidence < 0.6)
 */
export const useAutoDiagnosis = () => {
  return useMutation({
    mutationFn: (issueId: number) =>
      apiClient.post<CompleteDiagnosisReport>(`/diagnose/${issueId}/auto`).then(res => res.data),
  });
};

/**
 * Hook to check if diagnosis is needed for an issue
 */
export const useNeedsDiagnosis = (issueId: number | null) => {
  return useQuery({
    queryKey: ['needsDiagnosis', issueId],
    queryFn: () =>
      apiClient.get<{ issueId: number; needsDiagnosis: boolean }>(
        `/diagnose/${issueId}/needs-diagnosis`
      ).then(res => res.data),
    enabled: issueId !== null,
  });
};

/**
 * Hook for diagnosis history
 */
export const useDiagnosisHistory = () => {
  return useQuery({
    queryKey: ['diagnosisHistory'],
    queryFn: () =>
      apiClient.get<CompleteDiagnosisReport[]>('/diagnose/history').then(res => res.data),
    refetchInterval: 30000, // Refresh every 30s
  });
};
