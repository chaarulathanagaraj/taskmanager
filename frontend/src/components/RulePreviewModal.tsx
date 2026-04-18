import React from 'react';
import { Modal, Space, Tag, Timeline, Alert, Descriptions, Progress, Typography, Divider } from 'antd';
import {
  CheckCircleOutlined,
  WarningOutlined,
  CloseCircleOutlined,
  ClockCircleOutlined,
  SafetyOutlined,
  ThunderboltOutlined,
  RollbackOutlined,
} from '@ant-design/icons';
import type { RuleExecutionPreview } from '../api/rules';

const { Title, Text, Paragraph } = Typography;

interface RulePreviewModalProps {
  visible: boolean;
  preview: RuleExecutionPreview | null;
  loading: boolean;
  onApprove: () => void;
  onCancel: () => void;
  onDryRun?: () => void;
}

/**
 * Modal showing what a rule execution will do before it runs.
 * Safety-first approach: always show this before automation.
 */
export const RulePreviewModal: React.FC<RulePreviewModalProps> = ({
  visible,
  preview,
  loading,
  onApprove,
  onCancel,
  onDryRun,
}) => {
  if (!preview) return null;

  const getRiskColor = (riskLevel: string) => {
    switch (riskLevel) {
      case 'LOW': return 'success';
      case 'MEDIUM': return 'warning';
      case 'HIGH': return 'warning';
      case 'CRITICAL': return 'error';
      default: return 'default';
    }
  };

  const getSafetyIcon = (safetyLevel: string) => {
    switch (safetyLevel) {
      case 'LOW': return <CheckCircleOutlined style={{ color: '#52c41a' }} />;
      case 'MEDIUM': return <WarningOutlined style={{ color: '#faad14' }} />;
      case 'HIGH': return <WarningOutlined style={{ color: '#fa8c16' }} />;
      case 'CRITICAL': return <CloseCircleOutlined style={{ color: '#ff4d4f' }} />;
      default: return <CheckCircleOutlined />;
    }
  };

  const getSeverityIcon = (severity: string) => {
    switch (severity) {
      case 'INFO': return <CheckCircleOutlined style={{ color: '#1890ff' }} />;
      case 'WARNING': return <WarningOutlined style={{ color: '#faad14' }} />;
      case 'CRITICAL': return <CloseCircleOutlined style={{ color: '#ff4d4f' }} />;
      default: return <CheckCircleOutlined />;
    }
  };

  const confidencePercent = Math.round(preview.confidence * 100);
  const isCriticalProcess = preview.target.isCriticalProcess;

  return (
    <Modal
      title={
        <Space>
          <SafetyOutlined style={{ fontSize: 20 }} />
          <span>Rule Execution Preview</span>
        </Space>
      }
      open={visible}
      onCancel={onCancel}
      width={800}
      footer={[
        <button
          key="cancel"
          className="ant-btn"
          onClick={onCancel}
        >
          Cancel
        </button>,
        onDryRun && (
          <button
            key="dryrun"
            className="ant-btn"
            onClick={onDryRun}
          >
            <ThunderboltOutlined /> Dry Run
          </button>
        ),
        <button
          key="approve"
          className="ant-btn ant-btn-primary"
          onClick={onApprove}
          disabled={loading}
          style={{
            backgroundColor: preview.riskLevel === 'CRITICAL' ? '#ff4d4f' : undefined,
            borderColor: preview.riskLevel === 'CRITICAL' ? '#ff4d4f' : undefined,
          }}
        >
          {preview.approvalRequired ? 'Approve & Execute' : 'Execute'}
        </button>,
      ]}
    >
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        {/* Critical Process Warning */}
        {isCriticalProcess && (
          <Alert
            message="Critical System Process"
            description="This is a critical system process. Terminating it may cause system instability."
            type="error"
            showIcon
            icon={<CloseCircleOutlined />}
          />
        )}

        {/* Primary Action */}
        <div>
          <Title level={5}>
            <ThunderboltOutlined /> Primary Action
          </Title>
          <Alert
            message={preview.primaryAction.replace(/_/g, ' ')}
            description={preview.actionDescription}
            type="info"
            showIcon
          />
        </div>

        {/* Target & Risk Info */}
        <Descriptions bordered size="small" column={2}>
          <Descriptions.Item label="Target" span={2}>
            <Text strong>{preview.target.description}</Text>
            {preview.target.isCriticalProcess && (
              <Tag color="red" style={{ marginLeft: 8 }}>CRITICAL</Tag>
            )}
          </Descriptions.Item>
          <Descriptions.Item label="Risk Level">
            <Tag color={getRiskColor(preview.riskLevel)}>
              {preview.riskLevel}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="Confidence">
            <Space>
              <Progress
                type="circle"
                percent={confidencePercent}
                width={40}
                strokeColor={confidencePercent > 70 ? '#52c41a' : confidencePercent > 50 ? '#faad14' : '#ff4d4f'}
              />
              <Text>{confidencePercent}%</Text>
            </Space>
          </Descriptions.Item>
          <Descriptions.Item label="Estimated Time">
            <Space>
              <ClockCircleOutlined />
              <Text>{preview.estimatedSeconds} seconds</Text>
            </Space>
          </Descriptions.Item>
          <Descriptions.Item label="Rollback">
            {preview.canRollback ? (
              <Tag color="success" icon={<RollbackOutlined />}>Available</Tag>
            ) : (
              <Tag color="default">Not Available</Tag>
            )}
          </Descriptions.Item>
        </Descriptions>

        {/* Execution Steps */}
        <div>
          <Title level={5}>Execution Steps</Title>
          <Timeline
            items={preview.steps.map((step) => ({
              dot: getSafetyIcon(step.safetyLevel),
              children: (
                <div>
                  <div>
                    <Text strong>{step.description}</Text>
                    {step.optional && <Tag color="blue" style={{ marginLeft: 8 }}>Optional</Tag>}
                  </div>
                  {step.details && (
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {step.details}
                    </Text>
                  )}
                  <div style={{ marginTop: 4 }}>
                    <Text type="secondary" style={{ fontSize: 11 }}>
                      <ClockCircleOutlined /> {step.estimatedSeconds}s
                    </Text>
                  </div>
                </div>
              ),
            }))}
          />
        </div>

        {/* Warnings */}
        {preview.warnings.length > 0 && (
          <div>
            <Title level={5}>
              <WarningOutlined /> Warnings
            </Title>
            <Space direction="vertical" style={{ width: '100%' }}>
              {preview.warnings.map((warning, index) => (
                <Alert
                  key={index}
                  message={warning.message}
                  description={warning.mitigation}
                  type={warning.severity === 'CRITICAL' ? 'error' : warning.severity === 'WARNING' ? 'warning' : 'info'}
                  showIcon
                  icon={getSeverityIcon(warning.severity)}
                />
              ))}
            </Space>
          </div>
        )}

        <Divider />

        {/* Expected Outcome */}
        <div>
          <Title level={5}>Expected Outcome</Title>
          <Paragraph>
            <CheckCircleOutlined style={{ color: '#52c41a', marginRight: 8 }} />
            {preview.expectedOutcome}
          </Paragraph>
        </div>

        {/* Fallback Action */}
        {preview.fallbackAction && (
          <div>
            <Title level={5}>Fallback Plan</Title>
            <Alert
              message={preview.fallbackAction.replace(/_/g, ' ')}
              description={preview.fallbackDescription}
              type="info"
              showIcon
            />
          </div>
        )}
      </Space>
    </Modal>
  );
};
