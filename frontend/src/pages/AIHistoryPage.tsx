import React from 'react';
import { Timeline, Card, Tag, Space, Progress, Empty, Spin, Button, Typography, Descriptions } from 'antd';
import { BulbOutlined, CheckCircleOutlined, CloseCircleOutlined, ClockCircleOutlined, ReloadOutlined } from '@ant-design/icons';
import { useDiagnosisHistory } from '../hooks/useDiagnosis';
import { AIAnalysisCard } from '../components/AIAnalysisCard';
import type { CompleteDiagnosisReport } from '../types';

const { Text } = Typography;

/**
 * Page displaying AI diagnosis history timeline
 */
const AIHistoryPage: React.FC = () => {
  const { data: diagnoses, isLoading, error, refetch } = useDiagnosisHistory();

  const formatTimestamp = (timestamp: string) => {
    const date = new Date(timestamp);
    return date.toLocaleString();
  };

  const getTimelineColor = (report: CompleteDiagnosisReport) => {
    if (!report.success) return 'red';
    if (report.confidence >= 0.8) return 'green';
    if (report.confidence >= 0.6) return 'blue';
    return 'orange';
  };

  const getTimelineIcon = (report: CompleteDiagnosisReport) => {
    if (!report.success) return <CloseCircleOutlined />;
    if (report.confidence >= 0.8) return <CheckCircleOutlined />;
    return <BulbOutlined />;
  };

  if (isLoading) {
    return (
      <div style={{ textAlign: 'center', padding: '100px' }}>
        <Spin size="large" description="Loading diagnosis history..." />
      </div>
    );
  }

  if (error) {
    return (
      <Card>
        <Empty
          description="Failed to load diagnosis history"
          image={Empty.PRESENTED_IMAGE_SIMPLE}
        >
          <Button type="primary" onClick={() => refetch()}>
            Retry
          </Button>
        </Empty>
      </Card>
    );
  }

  if (!diagnoses || diagnoses.length === 0) {
    return (
      <Card>
        <Empty
          description="No AI diagnoses yet"
          image={Empty.PRESENTED_IMAGE_SIMPLE}
        >
          <Text type="secondary">
            AI diagnoses will appear here after analyzing issues
          </Text>
        </Empty>
      </Card>
    );
  }

  return (
    <div>
      <Card
        title={
          <Space>
            <BulbOutlined style={{ color: '#1890ff' }} />
            <span>AI Diagnosis History</span>
          </Space>
        }
        extra={
          <Button
            icon={<ReloadOutlined />}
            onClick={() => refetch()}
            loading={isLoading}
          >
            Refresh
          </Button>
        }
      >
        <div style={{ marginBottom: 16 }}>
          <Text type="secondary">
            Showing {diagnoses.length} diagnosis {diagnoses.length === 1 ? 'record' : 'records'}
          </Text>
        </div>

        <Timeline
          mode="left"
          items={diagnoses.map((report, index) => ({
            key: index,
            color: getTimelineColor(report),
            dot: getTimelineIcon(report),
            label: (
              <div style={{ width: 150 }}>
                <Text type="secondary">
                  <ClockCircleOutlined style={{ marginRight: 4 }} />
                  {formatTimestamp(report.timestamp)}
                </Text>
              </div>
            ),
            children: (
              <DiagnosisTimelineItem report={report} />
            ),
          }))}
        />
      </Card>
    </div>
  );
};

/**
 * Individual diagnosis timeline item
 */
interface DiagnosisTimelineItemProps {
  report: CompleteDiagnosisReport;
}

const DiagnosisTimelineItem: React.FC<DiagnosisTimelineItemProps> = ({ report }) => {
  const [expanded, setExpanded] = React.useState(false);

  return (
    <Card
      size="small"
      style={{ marginBottom: 16 }}
      title={
        <Space>
          {report.success ? (
            <CheckCircleOutlined style={{ color: '#52c41a' }} />
          ) : (
            <CloseCircleOutlined style={{ color: '#ff4d4f' }} />
          )}
          <span>
            {report.analyzedProcessName} (PID: {report.analyzedPid})
          </span>
        </Space>
      }
      extra={
        <Space>
          <Tag color={report.success ? 'green' : 'red'}>
            {report.success ? 'Success' : 'Failed'}
          </Tag>
          <Tag color="blue">{report.processingTimeMs}ms</Tag>
        </Space>
      }
    >
      {/* Summary */}
      <Descriptions size="small" column={2}>
        <Descriptions.Item label="Issue ID">
          #{report.issueId}
        </Descriptions.Item>
        <Descriptions.Item label="Confidence">
          <Progress
            percent={Math.round(report.confidence * 100)}
            size="small"
            style={{ width: 100 }}
            status={report.confidence >= 0.8 ? 'success' : 'normal'}
          />
        </Descriptions.Item>
        {report.analysis && (
          <>
            <Descriptions.Item label="Agent">
              <Tag>{report.analysis.agentName}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Action">
              <Tag color="purple">
                {report.analysis.recommendedAction?.replace(/_/g, ' ')}
              </Tag>
            </Descriptions.Item>
          </>
        )}
      </Descriptions>

      {/* Root Cause Summary */}
      {report.analysis?.rootCause && (
        <div style={{ marginTop: 12, padding: 12, backgroundColor: '#f5f5f5', borderRadius: 4 }}>
          <Text strong>Root Cause: </Text>
          <Text>{report.analysis.rootCause}</Text>
        </div>
      )}

      {/* Safety Status */}
      {report.safetyValidation && (
        <div style={{ marginTop: 12 }}>
          <Space>
            <Text strong>Safety:</Text>
            <Tag color={report.safetyValidation.safe ? 'green' : 'red'}>
              {report.safetyValidation.safe ? 'SAFE' : 'UNSAFE'}
            </Tag>
            <Progress
              percent={Math.round(report.safetyValidation.safetyScore * 100)}
              size="small"
              style={{ width: 80 }}
            />
            {report.safetyValidation.approvalRequired && (
              <Tag color="orange">Approval Required</Tag>
            )}
          </Space>
        </div>
      )}

      {/* Expand/Collapse */}
      <div style={{ marginTop: 12 }}>
        <Button
          type="link"
          size="small"
          onClick={() => setExpanded(!expanded)}
        >
          {expanded ? 'Hide Details' : 'Show Details'}
        </Button>
      </div>

      {/* Expanded Details */}
      {expanded && report.analysis && (
        <div style={{ marginTop: 16 }}>
          <AIAnalysisCard analysis={report.analysis} />

          {/* Remediation Plan */}
          {report.remediationPlan && (
            <Card size="small" title="Remediation Plan" style={{ marginTop: 16 }}>
              <Descriptions size="small" column={1}>
                <Descriptions.Item label="Primary Action">
                  <Tag color="purple">
                    {report.remediationPlan.primaryAction?.replace(/_/g, ' ')}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="Risk Level">
                  <Tag color={
                    report.remediationPlan.riskLevel === 'CRITICAL' ? 'red' :
                    report.remediationPlan.riskLevel === 'HIGH' ? 'orange' :
                    'blue'
                  }>
                    {report.remediationPlan.riskLevel}
                  </Tag>
                </Descriptions.Item>
                {report.remediationPlan.steps && report.remediationPlan.steps.length > 0 && (
                  <Descriptions.Item label="Steps">
                    <ol style={{ margin: 0, paddingLeft: 20 }}>
                      {report.remediationPlan.steps.map((step, i) => (
                        <li key={i}>
                          {step.description}
                          {step.optional && <Tag style={{ marginLeft: 8, fontSize: 12 }}>Optional</Tag>}
                        </li>
                      ))}
                    </ol>
                  </Descriptions.Item>
                )}
              </Descriptions>

              {report.remediationPlan.warnings && report.remediationPlan.warnings.length > 0 && (
                <div style={{ marginTop: 12 }}>
                  <Text strong>Warnings:</Text>
                  <ul style={{ color: '#faad14', margin: '8px 0' }}>
                    {report.remediationPlan.warnings.map((w, i) => (
                      <li key={i}>{w}</li>
                    ))}
                  </ul>
                </div>
              )}
            </Card>
          )}

          {/* Safety Violations */}
          {report.safetyValidation?.violations && report.safetyValidation.violations.length > 0 && (
            <Card size="small" title="Safety Violations" style={{ marginTop: 16 }}>
              {report.safetyValidation.violations.map((v, i) => (
                <div key={i} style={{ marginBottom: 8 }}>
                  <Space>
                    <Tag color={v.blocking ? 'red' : 'orange'}>
                      {v.blocking ? 'BLOCKING' : 'WARNING'}
                    </Tag>
                    <Text strong>[{v.rule}]</Text>
                    <Text>{v.description}</Text>
                  </Space>
                </div>
              ))}
            </Card>
          )}
        </div>
      )}
    </Card>
  );
};

export default AIHistoryPage;
