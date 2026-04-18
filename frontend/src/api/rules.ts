import apiClient from './client';

export interface RuleMatch {
  ruleId: string;
  ruleName: string;
  rationale: string;
  score: number;
}

export interface RuleEvaluation {
  issueId: number;
  issueType: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  confidence: number;
  matchedRules: RuleMatch[];
  recommendedAction: string;
  recommendationReason: string;
  protectedProcess: boolean;
  policyBlocked: boolean;
  requiresApproval: boolean;
  autoRemediationEligible: boolean;
  policyReason: string | null;
  evaluatedAt: string;
}

export interface RuleExecutionPreview {
  primaryAction: string;
  actionDescription: string;
  target: {
    pid: number;
    processName: string;
    description: string;
    memoryUsageMB: number;
    threadCount: number;
    isCriticalProcess: boolean;
  };
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  confidence: number;
  steps: Array<{
    order: number;
    safetyLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
    description: string;
    details: string;
    optional: boolean;
    estimatedSeconds: number;
  }>;
  estimatedSeconds: number;
  warnings: Array<{
    severity: 'INFO' | 'WARNING' | 'CRITICAL';
    message: string;
    mitigation: string;
  }>;
  expectedOutcome: string;
  fallbackAction: string | null;
  fallbackDescription: string;
  approvalRequired: boolean;
  canRollback: boolean;
  issueId: number;
}

export interface BulkAutomationOutcome {
  issueId: number;
  processName: string;
  affectedPid: number;
  issueType: string;
  action?: string;
  status: 'RESOLVED' | 'SIMULATED' | 'FAILED' | 'ERROR' | 'SKIPPED_PROTECTED';
  message: string;
}

export interface BulkAutomationResult {
  totalActive: number;
  automated: number;
  resolved: number;
  skippedProtected: number;
  failed: number;
  outcomes: BulkAutomationOutcome[];
}

export const rulesApi = {
  /**
   * Get deterministic rule evaluation for one issue.
   */
  getEvaluation: async (issueId: number): Promise<RuleEvaluation> => {
    const response = await apiClient.get<RuleEvaluation>(`/rules/evaluation/${issueId}`);
    return response.data;
  },

  /**
   * Get deterministic rule evaluations for all active issues.
   */
  getActiveEvaluations: async (): Promise<RuleEvaluation[]> => {
    const response = await apiClient.get<RuleEvaluation[]>('/rules/evaluation/active');
    return response.data;
  },

  /**
   * Get execution preview for an issue
   */
  getPreview: async (issueId: number): Promise<RuleExecutionPreview> => {
    const response = await apiClient.get<RuleExecutionPreview>(`/rules/preview/${issueId}`);
    return response.data;
  },

  /**
   * Check if preview is available for an issue
   */
  isPreviewAvailable: async (issueId: number): Promise<boolean> => {
    const response = await apiClient.get<boolean>(`/rules/preview/${issueId}/available`);
    return response.data;
  },

  /**
   * Execute remediation action
   */
  execute: async (issueId: number, actionType: string, isDryRun: boolean = false) => {
    const response = await apiClient.post(`/rules/execute`, { issueId, actionType, dryRun: isDryRun });
    return response.data;
  },

  /**
   * Request approval for remediation action
   */
  requestApproval: async (issueId: number, actionType: string, comment: string = '') => {
    // Uses the execute endpoint which handles critical actions by routing to approvals
    const response = await apiClient.post(`/rules/execute`, { issueId, actionType, dryRun: false, comment });
    return response.data;
  },

  /**
   * Automate all active issues except protected processes.
   */
  automateAllSafeIssues: async (): Promise<BulkAutomationResult> => {
    const response = await apiClient.post<BulkAutomationResult>('/rules/automate-all');
    return response.data;
  },
};
