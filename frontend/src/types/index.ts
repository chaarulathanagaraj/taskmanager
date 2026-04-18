/**
 * Type definitions for AIOS Frontend
 */

export interface MetricSnapshot {
  timestamp: string;
  cpuUsage: number;
  memoryUsed: number;
  memoryTotal: number;
  diskRead: number;
  diskWrite: number;
  networkSent: number;
  networkReceived: number;
}

export interface ProcessInfo {
  pid: number;
  name: string;
  cpuPercent: number;
  memoryBytes: number;
  threadCount: number;
  handleCount: number;
  ioReadBytes: number;
  ioWriteBytes: number;
}

export type IssueType =
  | 'MEMORY_LEAK'
  | 'THREAD_EXPLOSION'
  | 'HUNG_PROCESS'
  | 'IO_BOTTLENECK'
  | 'RESOURCE_HOG';

export type IssueStatus = 'NEW' | 'ACTIVE' | 'RESOLVED' | 'IGNORED';

export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface DiagnosticIssue {
  id?: number;
  issueKey?: string;
  type: IssueType;
  severity: Severity;
  confidence: number;
  affectedPid: number;
  processName: string;
  details: string;
  evidence?: string;
  status?: IssueStatus;
  detectedAt: string;
  lastSeenAt?: string;
  lastUpdatedAt?: string;
  occurrenceCount?: number;
  remediationTaken?: boolean;
  lastRemediationAt?: string;
  stabilityScore?: number;
  resolved?: boolean;
  resolvedAt?: string;
}

export type ActionType =
  | 'KILL_PROCESS'
  | 'REDUCE_PRIORITY'
  | 'TRIM_WORKING_SET'
  | 'SUSPEND_PROCESS'
  | 'RESTART_PROCESS';

export type ActionStatus = 'SUCCESS' | 'FAILED' | 'PENDING';

export type SafetyLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface RemediationAction {
  id?: number;
  actionType: ActionType;
  targetPid: number;
  targetName: string;
  safetyLevel: SafetyLevel;
  status: ActionStatus;
  result: string;
  dryRun: boolean;
  executedAt: string;
}

export interface ExecutionUpdateMessage {
  executionId: number;
  status: string;
  message: string;
  steps?: string[];
  stepIndex?: number | null;
  totalSteps?: number | null;
  verificationMessage?: string | null;
  timestamp: number;
}

/**
 * Resolution summary broadcast when an issue is resolved or ignored.
 */
export interface IssueResolutionSummary {
  issueId: number;
  processName: string;
  affectedPid: number;
  issueType: IssueType;
  severity: Severity;
  status: IssueStatus;
  resolved: boolean;
  remediationTaken: boolean;
  source: 'MANUAL' | 'AUTOMATED' | 'IGNORED';
  message: string;
  resolvedAt?: string;
  actionsTaken: string[];
}

export interface DashboardData {
  currentTime: string;
  cpuUsage: number;
  memoryPercent: number;
  diskIO: number;
  networkIO: number;
  topProcesses: ProcessInfo[];
  activeIssues: DiagnosticIssue[];
  recentActions: RemediationAction[];
  systemHealth: string;
}

/**
 * AI Analysis Result from LangChain4j agents
 */
export interface AiAnalysisResult {
  rootCause: string;
  confidence: number;
  recommendedAction: string;
  reasoning: string;
  evidence: string[];
  alternativeCauses: string[];
  riskAssessment: string;
  agentName: string;
}

/**
 * Remediation step in a plan
 */
export interface RemediationStep {
  order: number;
  description: string;
  action: string;
  optional: boolean;
}

/**
 * Remediation plan from AI
 */
export interface RemediationPlan {
  primaryAction: ActionType;
  targetPid: number;
  targetProcessName: string;
  steps: RemediationStep[];
  riskLevel: SafetyLevel;
  approvalRequired: boolean;
  warnings: string[];
  fallbackAction?: ActionType;
}

/**
 * Safety violation details
 */
export interface SafetyViolation {
  rule: string;
  description: string;
  severity: SafetyLevel;
  blocking: boolean;
}

/**
 * Safety validation result
 */
export interface SafetyValidation {
  safe: boolean;
  approvalRequired: boolean;
  warnings: string[];
  violations: SafetyViolation[];
  safetyScore: number;
  explanation: string;
}

/**
 * Complete diagnosis report from AI orchestrator
 */
export interface CompleteDiagnosisReport {
  success: boolean;
  message: string;
  analysis: AiAnalysisResult;
  remediationPlan: RemediationPlan;
  safetyValidation: SafetyValidation;
  confidence: number;
  timestamp: string;
  processingTimeMs: number;
  issueId: number;
  analyzedPid: number;
  analyzedProcessName: string;
}

/**
 * Agent configuration settings
 */
export interface AgentSettings {
  dryRunMode: boolean;
  autoRemediation: boolean;
  confidenceThreshold: number;
  maxConcurrentActions: number;
  collectionIntervalSeconds: number;
  protectedProcesses: string[];
  notifyOnCritical: boolean;
  aiDiagnosisEnabled: boolean;
}

/**
 * Log entry from backend
 */
export interface LogEntry {
  timestamp: string;
  level: 'TRACE' | 'DEBUG' | 'INFO' | 'WARN' | 'ERROR';
  logger: string;
  loggerShort: string;
  thread: string;
  message: string;
  stackTrace?: string;
  source: 'BACKEND' | 'AGENT' | 'MCP_SERVER' | 'AI_AGENTS';
}
