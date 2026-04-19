import { createElement, useEffect, useRef } from 'react';
import { notification } from 'antd';
import { useExecutionUpdatesStream, useIssuesStream, useResolvedIssuesStream } from './useWebSocket';
import type { DiagnosticIssue, IssueResolutionSummary, ExecutionUpdateMessage } from '../types';
import { ExecutionProgressCard, ResolutionSummaryCard } from '../components';

/**
 * Hook to show desktop notifications for critical issues
 */
export const useNotifications = (enabled: boolean = true) => {
  const notifiedIssues = useRef(new Set<number>());
  const notifiedResolutions = useRef(new Set<number>());
  const latestExecutionUpdates = useRef(new Map<number, ExecutionUpdateMessage>());

  useIssuesStream((issue: DiagnosticIssue) => {
    if (!enabled || !issue.id || notifiedIssues.current.has(issue.id)) {
      return;
    }

    // Mark as notified
    notifiedIssues.current.add(issue.id);

    // Show notification based on severity
    const config = {
      message: `${issue.severity} Issue Detected`,
      description: `${issue.processName} (PID ${issue.affectedPid}): ${issue.details}`,
      duration: getSeverityDuration(issue.severity),
    };

    switch (issue.severity) {
      case 'CRITICAL':
        notification.error(config);
        // Request browser notification for critical issues
        if ('Notification' in window && Notification.permission === 'granted') {
          new Notification('High-priority issue', {
            body: `${issue.processName}: ${issue.details}`,
            icon: '/favicon.ico',
          });
        }
        break;
      case 'HIGH':
        notification.warning(config);
        break;
      case 'MEDIUM':
        notification.info(config);
        break;
      case 'LOW':
        // Don't show notifications for low severity
        break;
    }
  });

  useResolvedIssuesStream((resolution: IssueResolutionSummary) => {
    if (!enabled || !resolution.issueId || notifiedResolutions.current.has(resolution.issueId)) {
      return;
    }

    notifiedResolutions.current.add(resolution.issueId);

    notification.open({
      key: `resolution-${resolution.issueId}`,
      message: `Resolution summary for issue #${resolution.issueId}`,
      description: createElement(ResolutionSummaryCard, { resolution }),
      duration: 0,
      placement: 'topRight',
      style: { width: 640 },
    });
  });

  useExecutionUpdatesStream((update: ExecutionUpdateMessage) => {
    if (!enabled || !update.executionId) {
      return;
    }

    latestExecutionUpdates.current.set(update.executionId, update);

    notification.open({
      key: `execution-${update.executionId}`,
      message: `Remediation progress #${update.executionId}`,
      description: createElement(ExecutionProgressCard, { update }),
      duration: 0,
      placement: 'topRight',
      style: { width: 640 },
    });
  });

  // Request notification permission on mount
  useEffect(() => {
    if (enabled && 'Notification' in window && Notification.permission === 'default') {
      Notification.requestPermission();
    }
  }, [enabled]);
};

/**
 * Get notification duration based on severity
 */
function getSeverityDuration(severity: string): number {
  switch (severity) {
    case 'CRITICAL':
      return 0; // Don't auto-close critical notifications
    case 'HIGH':
      return 10;
    case 'MEDIUM':
      return 6;
    case 'LOW':
      return 4;
    default:
      return 4.5;
  }
}
