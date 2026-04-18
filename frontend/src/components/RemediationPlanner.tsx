import React from 'react';
import { Alert, Button, Card, Tag, Space, Steps, Tooltip, Progress, Typography, Divider } from 'antd';
import { 
  CheckCircleOutlined, 
  WarningOutlined, 
  ThunderboltOutlined,
  SafetyOutlined,
  ClockCircleOutlined,
  RocketOutlined
} from '@ant-design/icons';
import type { RemediationPlan, SafetyValidation, AiAnalysisResult } from '../types';

const { Text, Paragraph } = Typography;

interface RemediationPlannerProps {
  /** AI analysis result containing confidence */
  analysis: AiAnalysisResult;
  /** Remediation plan from AI */
  remediationPlan: RemediationPlan;
  /** Safety validation result */
  safetyValidation: SafetyValidation;
  /** Overall diagnosis confidence */
  confidence: number;
  /** Callback when user wants to execute remediation */
  onExecute?: () => void;
  /** Callback when user wants to request approval */
  onRequestApproval?: () => void;
  /** Whether execution is in progress */
  executing?: boolean;
}

/**
 * Confidence thresholds for remediation decisions
 */
const CONFIDENCE_THRESHOLDS = {
  AUTO_APPROVE: 0.8,  // Can auto-execute without approval
  ALLOW_EXECUTE: 0.7, // Can execute with manual trigger
  REQUIRES_REVIEW: 0.5, // Requires manual review
};

/**
 * Get confidence level styling and messaging
 */
const getConfidenceLevel = (confidence: number): {
  color: string;
  status: 'success' | 'processing' | 'default' | 'error' | 'warning';
  label: string;
  canAutoExecute: boolean;
  canExecute: boolean;
} => {
  if (confidence >= CONFIDENCE_THRESHOLDS.AUTO_APPROVE) {
    return {
      color: 'green',
      status: 'success',
      label: 'High Confidence',
      canAutoExecute: true,
      canExecute: true,
    };
  }
  if (confidence >= CONFIDENCE_THRESHOLDS.ALLOW_EXECUTE) {
    return {
      color: 'blue',
      status: 'processing',
      label: 'Good Confidence',
      canAutoExecute: false,
      canExecute: true,
    };
  }
  if (confidence >= CONFIDENCE_THRESHOLDS.REQUIRES_REVIEW) {
    return {
      color: 'orange',
      status: 'warning',
      label: 'Moderate Confidence',
      canAutoExecute: false,
      canExecute: false,
    };
  }
  return {
    color: 'red',
    status: 'error',
    label: 'Low Confidence',
    canAutoExecute: false,
    canExecute: false,
  };
};

/**
 * RemediationPlanner component
 * Displays AI recommendation with confidence-based execution controls
 */
export const RemediationPlanner: React.FC<RemediationPlannerProps> = ({
  analysis,
  remediationPlan,
  safetyValidation,
  confidence,
  onExecute,
  onRequestApproval,
  executing = false,
}) => {
  const confidenceLevel = getConfidenceLevel(confidence);
  const canAutoExecute = confidenceLevel.canAutoExecute && safetyValidation.safe && !safetyValidation.approvalRequired;
  
  // Determine button state and label
  const getButtonConfig = () => {
    if (!safetyValidation.safe) {
      return {
        disabled: true,
        label: 'Unsafe - Execution Blocked',
        type: 'default' as const,
        icon: <WarningOutlined />,
      };
    }
    
    if (safetyValidation.approvalRequired) {
      return {
        disabled: true,
        label: 'Requires Admin Approval',
        type: 'default' as const,
        icon: <SafetyOutlined />,
      };
    }
    
    if (confidence < CONFIDENCE_THRESHOLDS.ALLOW_EXECUTE) {
      return {
        disabled: true,
        label: 'Low Confidence - Requires Review',
        type: 'default' as const,
        icon: <ClockCircleOutlined />,
      };
    }
    
    if (canAutoExecute) {
      return {
        disabled: false,
        label: 'Auto Execute',
        type: 'primary' as const,
        icon: <RocketOutlined />,
      };
    }
    
    return {
      disabled: false,
      label: 'Execute Remediation',
      type: 'primary' as const,
      icon: <ThunderboltOutlined />,
    };
  };

  const buttonConfig = getButtonConfig();

  return (
    <Card
      title={
        <Space>
          <ThunderboltOutlined />
          <span>Remediation Planner</span>
          <Tag color={confidenceLevel.color}>{confidenceLevel.label}</Tag>
        </Space>
      }
      size="small"
    >
      {/* AI Auto-Approval Recommendation Alert */}
      {confidence >= CONFIDENCE_THRESHOLDS.AUTO_APPROVE && safetyValidation.safe && (
        <Alert
          message="AI recommends auto-approval"
          description="High confidence diagnosis suggests safe remediation. This action can be executed automatically."
          type="success"
          showIcon
          icon={<CheckCircleOutlined />}
          style={{ marginBottom: 16 }}
        />
      )}

      {/* Moderate Confidence Warning */}
      {confidence >= CONFIDENCE_THRESHOLDS.REQUIRES_REVIEW && 
       confidence < CONFIDENCE_THRESHOLDS.ALLOW_EXECUTE && (
        <Alert
          message="Manual review recommended"
          description="AI confidence is moderate. Please review the diagnosis before proceeding."
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
        />
      )}

      {/* Low Confidence Alert */}
      {confidence < CONFIDENCE_THRESHOLDS.REQUIRES_REVIEW && (
        <Alert
          message="Low confidence - execution blocked"
          description="AI confidence is too low for automatic remediation. Manual investigation is required."
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
        />
      )}

      {/* Confidence Meter */}
      <div style={{ marginBottom: 16 }}>
        <Space style={{ width: '100%', justifyContent: 'space-between' }}>
          <Text strong>AI Confidence</Text>
          <Text type="secondary">{Math.round(confidence * 100)}%</Text>
        </Space>
        <Progress
          percent={Math.round(confidence * 100)}
          status={confidenceLevel.color === 'green' ? 'success' : confidenceLevel.color === 'red' ? 'exception' : 'normal'}
          strokeColor={confidenceLevel.color}
          showInfo={false}
        />
        <Space style={{ marginTop: 4 }}>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Auto-execute threshold: {CONFIDENCE_THRESHOLDS.AUTO_APPROVE * 100}%
          </Text>
          <Text type="secondary" style={{ fontSize: 12 }}>|</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Manual execute: {CONFIDENCE_THRESHOLDS.ALLOW_EXECUTE * 100}%
          </Text>
        </Space>
      </div>

      <Divider style={{ margin: '12px 0' }} />

      {/* Recommended Action */}
      <div style={{ marginBottom: 16 }}>
        <Text strong>Recommended Action</Text>
        <div style={{ marginTop: 8 }}>
          <Tag color="purple" style={{ fontSize: 14, padding: '4px 12px' }}>
            {remediationPlan.primaryAction?.replace(/_/g, ' ')}
          </Tag>
          {remediationPlan.fallbackAction && (
            <Tooltip title="Fallback action if primary fails">
              <Tag color="default" style={{ marginLeft: 8 }}>
                Fallback: {remediationPlan.fallbackAction.replace(/_/g, ' ')}
              </Tag>
            </Tooltip>
          )}
        </div>
        <Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0 }}>
          {analysis.recommendedAction || 'Execute the recommended action to resolve this issue.'}
        </Paragraph>
      </div>

      {/* Execution Steps */}
      {remediationPlan.steps && remediationPlan.steps.length > 0 && (
        <div style={{ marginBottom: 16 }}>
          <Text strong>Execution Steps</Text>
          <Steps
            direction="vertical"
            size="small"
            current={-1}
            style={{ marginTop: 8 }}
            items={remediationPlan.steps.map((step) => ({
              title: step.action,
              description: step.description,
              status: 'wait' as const,
              icon: step.optional ? (
                <Tooltip title="Optional step">
                  <ClockCircleOutlined style={{ color: '#8c8c8c' }} />
                </Tooltip>
              ) : undefined,
            }))}
          />
        </div>
      )}

      {/* Risk Level */}
      <div style={{ marginBottom: 16 }}>
        <Space>
          <Text strong>Risk Level:</Text>
          <Tag color={
            remediationPlan.riskLevel === 'CRITICAL' ? 'purple' :
            remediationPlan.riskLevel === 'HIGH' ? 'red' :
            remediationPlan.riskLevel === 'MEDIUM' ? 'orange' : 'green'
          }>
            {remediationPlan.riskLevel}
          </Tag>
        </Space>
      </div>

      {/* Warnings */}
      {remediationPlan.warnings && remediationPlan.warnings.length > 0 && (
        <Alert
          message="Warnings"
          description={
            <ul style={{ margin: 0, paddingLeft: 20 }}>
              {remediationPlan.warnings.map((warning, i) => (
                <li key={i}>{warning}</li>
              ))}
            </ul>
          }
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
        />
      )}

      <Divider style={{ margin: '12px 0' }} />

      {/* Action Buttons */}
      <Space>
        <Tooltip title={
          buttonConfig.disabled 
            ? 'Cannot execute: ' + buttonConfig.label
            : canAutoExecute 
              ? 'High confidence allows automatic execution'
              : 'Click to execute remediation'
        }>
          <Button
            type={buttonConfig.type}
            icon={buttonConfig.icon}
            disabled={buttonConfig.disabled}
            loading={executing}
            onClick={onExecute}
            size="large"
          >
            {executing ? 'Executing...' : buttonConfig.label}
          </Button>
        </Tooltip>

        {/* Request Approval Button - shown when approval is required */}
        {(safetyValidation.approvalRequired || confidence < CONFIDENCE_THRESHOLDS.ALLOW_EXECUTE) && (
          <Button
            icon={<SafetyOutlined />}
            onClick={onRequestApproval}
          >
            Request Approval
          </Button>
        )}
      </Space>

      {/* Safety Summary */}
      <div style={{ marginTop: 16, padding: 12, background: '#f5f5f5', borderRadius: 4 }}>
        <Space>
          <SafetyOutlined style={{ color: safetyValidation.safe ? '#52c41a' : '#ff4d4f' }} />
          <Text>
            Safety Score: <Text strong>{Math.round(safetyValidation.safetyScore * 100)}%</Text>
          </Text>
          <Tag color={safetyValidation.safe ? 'green' : 'red'}>
            {safetyValidation.safe ? 'SAFE' : 'UNSAFE'}
          </Tag>
        </Space>
      </div>
    </Card>
  );
};

export default RemediationPlanner;
