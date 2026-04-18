import { useState, useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Select, Space, Tag, Card, Progress, Button, Empty, Spin, Modal, Row, Col, Typography } from 'antd';
import { WarningOutlined, BulbOutlined, CheckCircleOutlined } from '@ant-design/icons';
import { toast } from 'react-toastify';
import { useIssues } from '../hooks/useMetrics';
import { useDiagnosis } from '../hooks/useDiagnosis';
import { issuesApi, settingsApi } from '../api/client';
import { AIAnalysisCard } from '../components/AIAnalysisCard';
import { RemediationPlanner } from '../components/RemediationPlanner';
import { RulePreviewModal } from '../components/RulePreviewModal';
import { rulesApi } from '../api/rules';
import type { DiagnosticIssue, Severity, CompleteDiagnosisReport } from '../types';
import type { RuleExecutionPreview, BulkAutomationResult, BulkAutomationOutcome } from '../api/rules';

const { Text } = Typography;

interface IssueProgress {
  analyzed: boolean;
  evaluated: boolean;
  previewed: boolean;
  executed: boolean;
}

interface FriendlyProcessInfo {
  displayName: string;
  category: string;
  location: string;
  priorityHint: string;
}

/**
 * Page displaying all diagnostic issues
 */
const IssuesPage: React.FC = () => {
  const { data: issues, isLoading, error } = useIssues();
  const [filter, setFilter] = useState<string>('all');
    const [diagnosisReport, setDiagnosisReport] = useState<CompleteDiagnosisReport | null>(null);
  const [activeDiagnosisIssue, setActiveDiagnosisIssue] = useState<DiagnosticIssue | null>(null);
  const [modalVisible, setModalVisible] = useState(false);
  const [executing, setExecuting] = useState(false);
  const [automatingAll, setAutomatingAll] = useState(false);
  const [selectedSafeIssueIds, setSelectedSafeIssueIds] = useState<number[]>([]);
  const [resolvingIssueId, setResolvingIssueId] = useState<number | null>(null);
  const [protectedPatterns, setProtectedPatterns] = useState<string[]>([]);
  const [runtimeSettings, setRuntimeSettings] = useState({
    dryRunMode: true,
    autoRemediation: false,
  });
  const [issueProgress, setIssueProgress] = useState<Record<number, IssueProgress>>({});
  const [rulePreviewsMap, setRulePreviewsMap] = useState<Record<number, RuleExecutionPreview>>({});
  
  // Rule preview state
  const [previewModalVisible, setPreviewModalVisible] = useState(false);
  const [rulePreview] = useState<RuleExecutionPreview | null>(null);
  const [loadingPreview] = useState(false);

  const diagnosisMutation = useDiagnosis();
  const queryClient = useQueryClient();

  const getIssueProgress = (issueId: number): IssueProgress => {
    return issueProgress[issueId] || {
      analyzed: false,
      evaluated: false,
      previewed: false,
      executed: false,
    };
  };

  const updateIssueProgress = (issueId: number, patch: Partial<IssueProgress>) => {
    setIssueProgress((prev) => ({
      ...prev,
      [issueId]: {
        ...getIssueProgress(issueId),
        ...patch,
      },
    }));
  };

  const filteredIssues = issues?.filter((i) =>
    filter === 'all' || i.type === filter
  ) || [];

  const toReadableIssueType = (type: string): string =>
    type
      .replace(/_/g, ' ')
      .toLowerCase()
      .replace(/\b\w/g, (ch) => ch.toUpperCase());

  const getFriendlyProcessInfo = (processName: string): FriendlyProcessInfo => {
    const raw = (processName || 'Unknown Process').trim();
    const lower = raw.toLowerCase();

    if (lower.includes('copilot') || lower.includes('github-copilot')) {
      return {
        displayName: 'GitHub Copilot',
        category: 'Developer Tool',
        location: 'VS Code Extension Host',
        priorityHint: 'Medium',
      };
    }
    if (lower === 'code.exe' || lower.includes('code - insiders')) {
      return {
        displayName: 'Visual Studio Code',
        category: 'Code Editor',
        location: 'User Application (Development)',
        priorityHint: 'High',
      };
    }
    if (lower === 'devenv.exe') {
      return {
        displayName: 'Visual Studio',
        category: 'IDE',
        location: 'User Application (Development)',
        priorityHint: 'High',
      };
    }
    if (lower === 'chrome.exe') {
      return {
        displayName: 'Google Chrome',
        category: 'Browser',
        location: 'User Application',
        priorityHint: 'Medium',
      };
    }
    if (lower === 'msedge.exe') {
      return {
        displayName: 'Microsoft Edge',
        category: 'Browser',
        location: 'User Application',
        priorityHint: 'Medium',
      };
    }
    if (lower === 'explorer.exe') {
      return {
        displayName: 'Windows Explorer',
        category: 'System Shell',
        location: 'Windows Core Shell',
        priorityHint: 'Critical',
      };
    }
    if (lower === 'java.exe' || lower === 'javaw.exe') {
      return {
        displayName: 'Java Runtime',
        category: 'Runtime',
        location: 'Application Runtime Layer',
        priorityHint: 'Medium',
      };
    }
    if (lower === 'node.exe') {
      return {
        displayName: 'Node.js Runtime',
        category: 'Runtime',
        location: 'Application Runtime Layer',
        priorityHint: 'Medium',
      };
    }
    if (lower === 'powershell.exe' || lower === 'pwsh.exe') {
      return {
        displayName: 'PowerShell',
        category: 'Shell',
        location: 'System Command Shell',
        priorityHint: 'Medium',
      };
    }
    if (lower === 'python.exe') {
      return {
        displayName: 'Python',
        category: 'Runtime',
        location: 'Application Runtime Layer',
        priorityHint: 'Medium',
      };
    }

    if (['system', 'csrss.exe', 'winlogon.exe', 'services.exe', 'lsass.exe', 'smss.exe', 'svchost.exe', 'wininit.exe', 'dwm.exe'].includes(lower)) {
      return {
        displayName: raw,
        category: 'System Process',
        location: 'Windows Core Services',
        priorityHint: 'Critical',
      };
    }

    return {
      displayName: raw.replace(/\.exe$/i, ''),
      category: 'Application',
      location: 'User Application',
      priorityHint: 'Medium',
    };
  };

  const globToRegex = (pattern: string): RegExp => {
    const escaped = pattern
      .replace(/[.+^${}()|[\]\\]/g, '\\$&')
      .replace(/\*/g, '.*')
      .replace(/\?/g, '.');
    return new RegExp(`^${escaped}$`, 'i');
  };

  const isIssueProtected = (issue: DiagnosticIssue): boolean => {
    const name = (issue.processName || '').trim();
    if (!name || protectedPatterns.length === 0) return false;
    return protectedPatterns.some((pattern) => globToRegex(pattern).test(name));
  };

  const selectableIssues = filteredIssues.filter((i) => Boolean(i.id));

  const severityColor: Record<Severity, string> = {
    LOW: 'blue',
    MEDIUM: 'orange',
    HIGH: 'red',
    CRITICAL: 'purple',
  };

  const getDurationMinutes = (issue: DiagnosticIssue): number => {
    const start = new Date(issue.detectedAt).getTime();
    const end = issue.lastSeenAt ? new Date(issue.lastSeenAt).getTime() : Date.now();
    return Math.max(0, Math.round((end - start) / 60000));
  };

  const isCooldownOrAlreadyResolved = (result: any): boolean => {
    const message = String(result?.message || '').toLowerCase();
    return result?.status === 'CANCELLED'
      && (message.includes('lock active') || message.includes('already resolved'));
  };

  const markIssueResolved = async (issueId: number): Promise<boolean> => {
    try {
      await issuesApi.resolveIssue(issueId);
      return true;
    } catch (firstError) {
      console.warn('First resolve attempt failed, retrying once', firstError);

      try {
        await issuesApi.resolveIssue(issueId);
        return true;
      } catch (secondError) {
        console.error('Failed to auto-mark issue resolved after remediation', secondError);
        return false;
      }
    }
  };

  const refreshRuntimeSettings = async () => {
    const response = await settingsApi.get();
    const nextSettings = {
      dryRunMode: response.data?.dryRunMode ?? true,
      autoRemediation: response.data?.autoRemediation ?? false,
    };
    setRuntimeSettings(nextSettings);
    setProtectedPatterns(response.data?.protectedProcesses || []);
    return nextSettings;
  };

  const handleResolve = async (issue: DiagnosticIssue) => {
    if (!issue.id) return;

    setResolvingIssueId(issue.id);
    try {
      // Get evaluation for recommended action
      const preview = await rulesApi.getPreview(issue.id);
      setRulePreviewsMap(prev => ({...prev, [issue.id!]: preview}));
      updateIssueProgress(issue.id, { evaluated: true });

      const isLowConfidence = issue.confidence < 0.8;
      const requiresPermission = isLowConfidence || preview.target.isCriticalProcess || (preview.riskLevel === 'HIGH' || preview.riskLevel === 'CRITICAL');

      const executeAction = async () => {
        try {
          const latestSettings = await refreshRuntimeSettings();
          const effectiveDryRun = latestSettings.dryRunMode;

          if (effectiveDryRun) {
            toast.info('Global dry-run is enabled in Settings. Running simulation only.', { autoClose: 5000 });
          } else if (!latestSettings.autoRemediation) {
            toast.warning('Auto-remediation is disabled in Settings. Run Dry Run or request approval.', {
              autoClose: 5000,
            });
            return;
          }

          toast.info('Executing remediation...', { autoClose: 2000 });
          const result: any = await rulesApi.execute(issue.id!, preview.primaryAction, effectiveDryRun);
          
          if (result?.status === 'PENDING') {
            toast.info('Execution request is pending approval. The issue stays active until approved and completed.', {
              autoClose: 6000,
            });
            return;
          }

          if (result && result.success === false) {
            if (isCooldownOrAlreadyResolved(result)) {
              toast.info(result.message || 'This issue was already handled recently.', { autoClose: 5000 });
            } else {
              toast.warning(`Execution failed or blocked: ${result.message || 'Safety Policy'}`, { autoClose: 5000 });
            }
            return;
          }

          updateIssueProgress(issue.id!, { executed: true });

          if (effectiveDryRun) {
            toast.info('Simulation completed. Issue stays active because dry-run does not make real changes.', {
              autoClose: 6000,
            });
            queryClient.invalidateQueries({ queryKey: ['issues'] });
            queryClient.invalidateQueries({ queryKey: ['dashboard'] });
            return;
          }

          const resolved = await markIssueResolved(issue.id!);
          toast.info(
            resolved
              ? 'Remediation executed and issue marked as resolved.'
              : (result?.message || 'Remediation executed. Resolution sync failed, but automation completed.'),
            { autoClose: 5000 }
          );
          queryClient.invalidateQueries({ queryKey: ['issues'] });
          queryClient.invalidateQueries({ queryKey: ['dashboard'] });
        } catch (error: any) {
          toast.error(`Execution failed: ${error?.response?.data?.message || error.message}`);
          console.error(error);
        }
      };

      if (requiresPermission) {
        Modal.confirm({
          title: 'Permission Required for Remediation',
          content: `Confidence is low (${Math.round(issue.confidence * 100)}%) or the process is protected. Do you want to proceed with: ${preview.primaryAction.replace(/_/g, ' ')}?`,
          okText: 'Yes, Execute',
          cancelText: 'Cancel',
          onOk: executeAction,
        });
      } else {
        await executeAction();
      }
    } catch (error) {
      toast.error('Failed to resolve issue.');
      console.error(error);
    } finally {
      setResolvingIssueId(null);
    }
  };

  const handleCloseModal = () => {
    setModalVisible(false);
    setDiagnosisReport(null);
    setActiveDiagnosisIssue(null);
  };

  const handleExecuteRemediation = async () => {
    if (!diagnosisReport) return;
    
    setExecuting(true);
    try {
      const result: any = await rulesApi.execute(
        diagnosisReport.issueId,
        diagnosisReport.remediationPlan?.primaryAction || 'UNKNOWN_ACTION',
        (await refreshRuntimeSettings()).dryRunMode
      );
      
      // Only proceed if execution was successful
      if (result?.success === true) {
        const resolved = await markIssueResolved(diagnosisReport.issueId);
        toast.info(
          resolved
            ? 'Remediation executed successfully and issue marked as resolved.'
            : 'Remediation executed successfully. Failed to mark resolved, but action was completed.',
          { autoClose: 5000 }
        );
      } else if (result && result.success === false && isCooldownOrAlreadyResolved(result)) {
        toast.info(result.message || 'This issue was already handled recently.', { autoClose: 5000 });
      } else {
        toast.error(
          result?.message || 'Remediation execution failed. Issue not marked as resolved.'
        );
      }
      queryClient.invalidateQueries({ queryKey: ['issues'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      handleCloseModal();
    } catch (error) {
      toast.error('Failed to execute remediation');
      console.error('Execution error:', error);
    } finally {
      setExecuting(false);
    }
  };

  const handleRequestApproval = async () => {
    if (!diagnosisReport) return;

    try {
      await rulesApi.requestApproval(
        diagnosisReport.issueId,
        diagnosisReport.remediationPlan?.primaryAction || 'UNKNOWN_ACTION',
        'User requested approval from dashboard'
      );
      toast.info('Approval request submitted. An administrator will review.', { autoClose: 5000 });
    } catch (error) {
       toast.error('Failed to request approval.');
    }
  };

  const handleApproveAndExecute = async () => {
    if (!rulePreview) return;

    const latestSettings = await refreshRuntimeSettings();
    const effectiveDryRun = latestSettings.dryRunMode;
    if (effectiveDryRun) {
      toast.info('Global dry-run is enabled in Settings. Running simulation only.', { autoClose: 5000 });
    }
    
    toast.info('Executing remediation plan...', { autoClose: 3000 });
    setPreviewModalVisible(false);
    
    try {
      const result: any = await rulesApi.execute(
        rulePreview.issueId,
        rulePreview.primaryAction,
        effectiveDryRun
      );
      updateIssueProgress(rulePreview.issueId, { executed: true });
      
      // Only proceed if execution was successful
      if (result?.success === true) {
        const resolved = await markIssueResolved(rulePreview.issueId);
        toast.info(
          resolved
            ? 'Remediation executed successfully and issue marked as resolved.'
            : 'Remediation executed successfully. Failed to mark resolved, but action was completed.',
          { autoClose: 5000 }
        );
      } else if (result && result.success === false && isCooldownOrAlreadyResolved(result)) {
        toast.info(result.message || 'This issue was already handled recently.', { autoClose: 5000 });
      } else {
        toast.error(
          result?.message || 'Remediation execution failed. Issue not marked as resolved.'
        );
      }
      queryClient.invalidateQueries({ queryKey: ['issues'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    } catch (error) {
       toast.error('Execution failed');
       console.error(error);
    }
  };

  const handleDryRun = async () => {
    if (!rulePreview) return;
    toast.info('Running in dry-run mode (simulation only)', { autoClose: 5000 });
    setPreviewModalVisible(false);

    try {
      await rulesApi.execute(
        rulePreview.issueId,
        rulePreview.primaryAction,
        true
      );
      toast.success('Dry run simulation completed successfully!');
    } catch (error) {
      toast.error('Simulation failed');
    }
  };

  const showBulkAutomationSummary = (result: BulkAutomationResult) => {
    const protectedOutcomes = result.outcomes.filter((o) => o.status === 'SKIPPED_PROTECTED');
    const resolvedOutcomes = result.outcomes.filter((o) => o.status === 'RESOLVED');
    const simulatedOutcomes = result.outcomes.filter((o) => o.status === 'SIMULATED');
    const failedOutcomes = result.outcomes.filter((o) => o.status === 'FAILED' || o.status === 'ERROR');

    Modal.info({
      title: 'Automation Summary',
      width: 980,
      content: (
        <div>
          <p><strong>Total active:</strong> {result.totalActive}</p>
          <p><strong>Automated:</strong> {result.automated}</p>
          <p><strong>Resolved:</strong> {result.resolved}</p>
          <p><strong>Simulated (dry-run):</strong> {simulatedOutcomes.length}</p>
          <p><strong>Skipped protected:</strong> {result.skippedProtected}</p>
          <p><strong>Failed:</strong> {result.failed}</p>

          <Row gutter={16}>
            <Col span={12}>
              <Card size="small" title={`Protected (Manual) - ${protectedOutcomes.length}`}>
                {protectedOutcomes.length === 0 ? (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="None" />
                ) : (
                  <ul style={{ margin: 0, paddingLeft: 20 }}>
                    {protectedOutcomes.map((item) => (
                      <li key={item.issueId}>
                        #{item.issueId} {item.processName} (PID {item.affectedPid})
                      </li>
                    ))}
                  </ul>
                )}
              </Card>
            </Col>
            <Col span={12}>
              <Card size="small" title={`Selected Processes - Resolved ${resolvedOutcomes.length}, Failed ${failedOutcomes.length}`}>
                {resolvedOutcomes.length > 0 && (
                  <div style={{ marginBottom: 8 }}>
                    <strong>Resolved</strong>
                    <ul style={{ margin: 0, paddingLeft: 20 }}>
                      {resolvedOutcomes.map((item) => (
                        <li key={item.issueId}>
                          #{item.issueId} {item.processName} ({item.action || 'ACTION'})
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
                {simulatedOutcomes.length > 0 && (
                  <div style={{ marginBottom: 8 }}>
                    <strong>Simulated (Dry Run)</strong>
                    <ul style={{ margin: 0, paddingLeft: 20 }}>
                      {simulatedOutcomes.map((item) => (
                        <li key={item.issueId}>
                          #{item.issueId} {item.processName}: {item.message}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
                {failedOutcomes.length > 0 && (
                  <div>
                    <strong>Auto-Failed</strong>
                    <ul style={{ margin: 0, paddingLeft: 20 }}>
                      {failedOutcomes.map((item) => (
                        <li key={item.issueId}>
                          #{item.issueId} {item.processName}: {item.message}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
                {resolvedOutcomes.length === 0 && failedOutcomes.length === 0 && (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No automatable outcomes" />
                )}
              </Card>
            </Col>
          </Row>
        </div>
      ),
    });
  };

  const handleAutomateAll = async () => {
    if (!issues || issues.length === 0) {
      toast.info('No active issues to automate.');
      return;
    }

    if (selectedSafeIssueIds.length === 0) {
      toast.info('Choose at least one process to automate.');
      return;
    }

    const selectedIssues = filteredIssues.filter((i) => i.id && selectedSafeIssueIds.includes(i.id));
    if (selectedIssues.length === 0) {
      toast.warning('Selected issues are no longer available in the current list.');
      return;
    }

    Modal.confirm({
      title: 'Automate Selected Processes',
      content: `This will run remediation for ${selectedIssues.length} selected process issue(s).`,
      okText: 'Run Automation',
      cancelText: 'Cancel',
      onOk: async () => {
        setAutomatingAll(true);
        try {
          const latestSettings = await refreshRuntimeSettings();
          if (!latestSettings.autoRemediation) {
            toast.warning('Auto-remediation is disabled in Settings. Enable it before bulk automation.', {
              autoClose: 5000,
            });
            return;
          }

          if (latestSettings.dryRunMode) {
            toast.info('Global dry-run is enabled. Bulk automation will be simulated only.', { autoClose: 5000 });
          }

          const outcomes: BulkAutomationOutcome[] = await Promise.all(selectedIssues.map(async (issue): Promise<BulkAutomationOutcome> => {
            try {
              if (isIssueProtected(issue)) {
                return {
                  issueId: issue.id!,
                  processName: issue.processName,
                  affectedPid: issue.affectedPid,
                  issueType: issue.type,
                  status: 'SKIPPED_PROTECTED',
                  message: 'Not safe for automation due to protected-process policy',
                };
              }

              const preview = await rulesApi.getPreview(issue.id!);
              const execution: any = await rulesApi.execute(issue.id!, preview.primaryAction, latestSettings.dryRunMode);

              if (execution?.status === 'PENDING') {
                return {
                  issueId: issue.id!,
                  processName: issue.processName,
                  affectedPid: issue.affectedPid,
                  issueType: issue.type,
                  action: preview.primaryAction,
                  status: 'FAILED',
                  message: execution.message || 'Approval required before execution can continue',
                };
              }

              if (!execution || execution.success === false) {
                return {
                  issueId: issue.id!,
                  processName: issue.processName,
                  affectedPid: issue.affectedPid,
                  issueType: issue.type,
                  action: preview.primaryAction,
                  status: 'FAILED',
                  message: execution?.message || 'Execution failed',
                };
              }

              if (latestSettings.dryRunMode) {
                return {
                  issueId: issue.id!,
                  processName: issue.processName,
                  affectedPid: issue.affectedPid,
                  issueType: issue.type,
                  action: preview.primaryAction,
                  status: 'SIMULATED',
                  message: 'Dry-run simulation completed',
                };
              }

              const resolved = await markIssueResolved(issue.id!);
              const status: 'RESOLVED' | 'FAILED' = resolved ? 'RESOLVED' : 'FAILED';
              return {
                issueId: issue.id!,
                processName: issue.processName,
                affectedPid: issue.affectedPid,
                issueType: issue.type,
                action: preview.primaryAction,
                status,
                message: resolved ? 'Remediation executed and issue marked resolved' : 'Executed but resolve API failed',
              };
            } catch (e: any) {
              return {
                issueId: issue.id!,
                processName: issue.processName,
                affectedPid: issue.affectedPid,
                issueType: issue.type,
                status: 'ERROR',
                message: e?.response?.data?.message || e?.message || 'Automation error',
              };
            }
          }));

          const result: BulkAutomationResult = {
            totalActive: selectedIssues.length,
            automated: outcomes.filter((o) => o.status === 'RESOLVED' || o.status === 'SIMULATED').length,
            resolved: outcomes.filter((o) => o.status === 'RESOLVED').length,
            skippedProtected: outcomes.filter((o) => o.status === 'SKIPPED_PROTECTED').length,
            failed: outcomes.filter((o) => o.status === 'FAILED' || o.status === 'ERROR').length,
            outcomes,
          };

          const simulated = outcomes.filter((o) => o.status === 'SIMULATED').length;
          toast.success(`Automation complete. Resolved: ${result.resolved}, Simulated: ${simulated}, Not Safe Skipped: ${result.skippedProtected}, Failed: ${result.failed}.`);
          showBulkAutomationSummary(result);
          setSelectedSafeIssueIds([]);
          queryClient.invalidateQueries({ queryKey: ['issues'] });
          queryClient.invalidateQueries({ queryKey: ['dashboard'] });
        } catch (error: any) {
          toast.error(`Bulk automation failed: ${error?.response?.data?.message || error.message}`);
        } finally {
          setAutomatingAll(false);
        }
      },
    });
  };

  useEffect(() => {
    if (!issues) return;
    // We moved notifications to AppHeader to avoid side popups

    // Auto-fetch rule evaluations for active issues
    const fetchEvaluationsAndDiagnose = async () => { try { // Auto diagnosis
        for (const issue of issues) {
          if (issue.status === "ACTIVE" && issue.id && issue.confidence < 0.8 && !(issueProgress[issue.id] && issueProgress[issue.id].analyzed)) {
             try {
                const report = await diagnosisMutation.mutateAsync(issue.id);
                if (report.success) {
                  updateIssueProgress(issue.id, { analyzed: true });
                }
             } catch (err) {}
          }
        }
      } catch (err) {
        console.error("Failed to init dashboard info", err);
      }
    };
    fetchEvaluationsAndDiagnose();
  }, [issues]);

  useEffect(() => {
    const loadProtectedPatterns = async () => {
      try {
        await refreshRuntimeSettings();
      } catch (error) {
        console.error('Failed to load protected process patterns', error);
      }
    };

    loadProtectedPatterns();
  }, []);
  if (isLoading) {
    return (
      <div style={{ textAlign: 'center', padding: '100px' }}>
        <Spin size="large" tip="Loading issues..." />
      </div>
    );
  }

  if (error) {
    return <Empty description="Failed to load issues" />;
  }

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Select value={filter} onChange={setFilter} style={{ width: 250 }}>
          <Select.Option value="all">All Issues</Select.Option>
          <Select.Option value="MEMORY_LEAK">Memory Leaks</Select.Option>
          <Select.Option value="THREAD_EXPLOSION">Thread Issues</Select.Option>
          <Select.Option value="HUNG_PROCESS">Hung Processes</Select.Option>
          <Select.Option value="IO_BOTTLENECK">I/O Bottlenecks</Select.Option>
          <Select.Option value="RESOURCE_HOG">Resource Hogs</Select.Option>
        </Select>
        <Button
          type="primary"
          loading={automatingAll}
          onClick={handleAutomateAll}
        >
          {automatingAll ? 'Automating...' : 'Automate Selected Processes'}
        </Button>
      </Space>

      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={24}>
          <Card size="small" title={`Choose Processes For Automation (${selectableIssues.length})`}>
            <Space direction="vertical" style={{ width: '100%', marginBottom: 10 }}>
              <Text type="secondary">Select one or more processes. Each process shows whether it is safe or not safe.</Text>
              <Select
                mode="multiple"
                style={{ width: '100%' }}
                placeholder="Choose one or more processes"
                value={selectedSafeIssueIds}
                onChange={(values) => setSelectedSafeIssueIds(values as number[])}
                options={selectableIssues.map((issue) => {
                  const safe = !isIssueProtected(issue);
                  const processName = getFriendlyProcessInfo(issue.processName).displayName;
                  const safetyLabel = safe ? 'SAFE' : 'NOT SAFE';
                  return {
                    value: issue.id!,
                    label: `${processName} (PID ${issue.affectedPid}) - ${toReadableIssueType(issue.type)} [${safetyLabel}]`,
                  };
                })}
              />
            </Space>
            {selectableIssues.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No processes available in current filter" />
            ) : (
              <ul style={{ margin: 0, paddingLeft: 20 }}>
                {selectableIssues.map((issue) => {
                  const safe = !isIssueProtected(issue);
                  return (
                    <li key={issue.id || `${issue.affectedPid}-${issue.type}`}>
                      {getFriendlyProcessInfo(issue.processName).displayName} (PID {issue.affectedPid}) - {toReadableIssueType(issue.type)}{' '}
                      <Tag color={safe ? 'green' : 'red'} style={{ marginLeft: 8 }}>
                        {safe ? 'Safe' : 'Not Safe'}
                      </Tag>
                    </li>
                  );
                })}
              </ul>
            )}
          </Card>
        </Col>
      </Row>

      {filteredIssues.length === 0 ? (
        <Empty description="No issues found" />
      ) : (
        <Row gutter={[16, 16]}>
          {filteredIssues.map((issue: DiagnosticIssue) => {
            const preview = issue.id ? rulePreviewsMap[issue.id] : undefined;
            const process = getFriendlyProcessInfo(issue.processName);

            return (
              <Col xs={24} lg={12} key={issue.id || `${issue.affectedPid}-${issue.type}`}>
                <Card
                  style={{ height: '100%', borderRadius: 12 }}
                  title={
                    <Space>
                      <WarningOutlined />
                      <span>{toReadableIssueType(issue.type)}</span>
                      <Tag color={severityColor[issue.severity]}>{issue.severity}</Tag>
                    </Space>
                  }
                >
                  <Row gutter={16}>
                    <Col xs={24} md={12}>
                      <Space direction="vertical" size={6} style={{ width: '100%' }}>
                        <div>
                          <Text strong>App:</Text> {process.displayName}
                          <br />
                          <Text type="secondary">{process.category}</Text>
                        </div>
                        <div>
                          <Text strong>Process Location:</Text> {process.location}
                        </div>
                        <div>
                          <Text strong>Priority:</Text> {process.priorityHint}
                        </div>
                        <div>
                          <Text strong>PID:</Text> {issue.affectedPid}
                        </div>
                        <div>
                          <Text strong>Detected:</Text> {new Date(issue.detectedAt).toLocaleString()}
                        </div>
                        <Space wrap>
                          <Tag color={issue.status === 'NEW' ? 'blue' : issue.status === 'ACTIVE' ? 'green' : issue.status === 'IGNORED' ? 'default' : 'orange'}>
                            Status: {issue.status || 'ACTIVE'}
                          </Tag>
                          <Tag color="cyan">Detected: {issue.occurrenceCount || 1} times</Tag>
                          <Tag color="geekblue">Duration: {getDurationMinutes(issue)} min</Tag>
                          <Tag color="purple">Stability: {issue.stabilityScore ?? Math.round(issue.confidence * 100)}%</Tag>
                          {issue.remediationTaken && <Tag color="volcano">Remediation Applied</Tag>}
                        </Space>
                      </Space>
                    </Col>

                    <Col xs={24} md={12}>
                      <Space direction="vertical" size={8} style={{ width: '100%' }}>
                        <div>
                          <Text strong>Confidence</Text>
                          <Progress
                            percent={Math.round(issue.confidence * 100)}
                            status={issue.confidence > 0.8 ? 'success' : 'normal'}
                          />
                        </div>

                      </Space>
                    </Col>
                  </Row>

                  <div style={{ marginTop: 12 }}>
                    <Text strong>Issue Details</Text>
                    <div>{issue.details}</div>
                  </div>

                  {preview && (
                    <Card size="small" style={{ marginTop: 12 }} title="Execution Readiness">
                      <Space wrap>
                        <Tag color="blue">Rule Confidence {Math.round(preview.confidence * 100)}%</Tag>
                        <Tag color={preview.riskLevel === 'CRITICAL' ? 'red' : 'green'}>
                          {preview.riskLevel === 'CRITICAL' ? 'Policy Blocked' : 'Policy OK'}
                        </Tag>
                        <Tag color={(preview.riskLevel === 'HIGH' || preview.riskLevel === 'CRITICAL') ? 'orange' : 'green'}>
                          {(preview.riskLevel === 'HIGH' || preview.riskLevel === 'CRITICAL') ? 'Approval Required' : 'No Approval Needed'}
                        </Tag>
                        <Tag color="purple">Action {preview.primaryAction.replace(/_/g, ' ')}</Tag>
                      </Space>
                    </Card>
                  )}

                  <Space style={{ marginTop: 12 }} wrap>
                    <Button
                      type="primary"
                      danger
                      icon={<CheckCircleOutlined />}
                      loading={resolvingIssueId === issue.id}
                      onClick={() => handleResolve(issue)}
                    >
                      {resolvingIssueId === issue.id ? 'Resolving...' : 'Resolve With Action'}
                    </Button>
                  </Space>
                </Card>
              </Col>
            );
          })}
        </Row>
      )}

      {/* AI Diagnosis Result Modal */}
      <Modal
        title={
          <Space>
            <BulbOutlined style={{ color: '#1890ff' }} />
            AI Diagnosis Report
          </Space>
        }
        open={modalVisible}
        onCancel={handleCloseModal}
        footer={[
          <Button key="close" onClick={handleCloseModal}>
            Close
          </Button>,
        ]}
        width={900}
      >
        {diagnosisReport && (
          <div>
            {/* Success/Failure Status */}
            <Card size="small" style={{ marginBottom: 16 }}>
              <Space>
                <CheckCircleOutlined
                  style={{ color: diagnosisReport.success ? '#52c41a' : '#ff4d4f', fontSize: 24 }}
                />
                <div>
                  <div style={{ fontWeight: 'bold' }}>
                    {diagnosisReport.success ? 'Diagnosis Successful' : 'Diagnosis Failed'}
                  </div>
                  <div style={{ color: '#666' }}>
                    {diagnosisReport.message} • Processing time: {diagnosisReport.processingTimeMs}ms
                  </div>
                </div>
              </Space>
            </Card>

            {/* AI Analysis Card */}
            {diagnosisReport.analysis && (
              <AIAnalysisCard
                analysis={diagnosisReport.analysis}
                issueType={activeDiagnosisIssue?.type}
              />
            )}

            {/* Remediation Planner with AI confidence-based controls */}
            {diagnosisReport.remediationPlan && diagnosisReport.safetyValidation && diagnosisReport.analysis && (
              <div style={{ marginTop: 16 }}>
                <RemediationPlanner
                  analysis={diagnosisReport.analysis}
                  remediationPlan={diagnosisReport.remediationPlan}
                  safetyValidation={diagnosisReport.safetyValidation}
                  confidence={diagnosisReport.confidence}
                  onExecute={handleExecuteRemediation}
                  onRequestApproval={handleRequestApproval}
                  executing={executing}
                />
              </div>
            )}
          </div>
        )}
      </Modal>

      {/* Rule Execution Preview Modal */}
      <RulePreviewModal
        visible={previewModalVisible}
        preview={rulePreview}
        loading={loadingPreview}
        onApprove={handleApproveAndExecute}
        onCancel={() => setPreviewModalVisible(false)}
        onDryRun={handleDryRun}
      />
    </div>
  );
};

export default IssuesPage;











