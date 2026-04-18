import { Card, Progress, Tag, Button, Space, Typography } from 'antd';
import { WarningOutlined, BulbOutlined, LoadingOutlined } from '@ant-design/icons';
import type { DiagnosticIssue } from '../types';

const { Text } = Typography;

interface IssueCardProps {
  issue: DiagnosticIssue;
  onAnalyze?: (issueId: number) => void;
  analyzing?: boolean;
}

/**
 * Card component displaying diagnostic issue details
 */
export const IssueCard: React.FC<IssueCardProps> = ({ issue, onAnalyze, analyzing = false }) => {
  // Severity color mapping
  const severityColors: Record<string, string> = {
    LOW: 'blue',
    MEDIUM: 'orange',
    HIGH: 'red',
    CRITICAL: 'purple',
  };

  // Format issue type for display
  const formatIssueType = (type: string) => {
    return type.replace(/_/g, ' ').replace(/\b\w/g, (char) => char.toUpperCase());
  };

  // Determine confidence status color
  const getConfidenceStatus = (confidence: number) => {
    if (confidence >= 0.8) return 'success';
    if (confidence >= 0.6) return 'normal';
    return 'exception';
  };

  // Check if issue is stale (process likely terminated)
  const isStaleIssue = (detectedAt: string) => {
    const detectedTime = new Date(detectedAt).getTime();
    const now = Date.now();
    const hourInMs = 60 * 60 * 1000;
    return (now - detectedTime) > hourInMs; // Stale if older than 1 hour
  };

  const staleIssue = isStaleIssue(issue.detectedAt);

  return (
    <Card
      title={
        <Space>
          <WarningOutlined style={{ color: '#faad14' }} />
          <span>{formatIssueType(issue.type)}</span>
        </Space>
      }
      extra={
        <Tag color={severityColors[issue.severity] || 'default'}>
          {issue.severity}
        </Tag>
      }
      style={{ marginBottom: 16 }}
    >
      <Space direction="vertical" style={{ width: '100%' }} size="middle">
        <div>
          <Text strong>Process: </Text>
          <Text>
            {issue.processName} (PID: {issue.affectedPid})
          </Text>
        </div>

        <div>
          <Text strong>Detected: </Text>
          <Text>{new Date(issue.detectedAt).toLocaleString()}</Text>
        </div>

        <div>
          <Text strong>Confidence: </Text>
          <Progress
            percent={Math.round(issue.confidence * 100)}
            status={getConfidenceStatus(issue.confidence)}
            style={{ marginTop: 8 }}
          />
        </div>

        {issue.details && (
          <div>
            <Text strong>Details: </Text>
            <div style={{ marginTop: 8, padding: 12, backgroundColor: '#f5f5f5', borderRadius: 4 }}>
              <Text type="secondary">{issue.details}</Text>
            </div>
          </div>
        )}

        {onAnalyze && issue.id && (
          <Button
            type="primary"
            icon={analyzing ? <LoadingOutlined /> : <BulbOutlined />}
            onClick={() => onAnalyze(issue.id!)}
            loading={analyzing}
            disabled={staleIssue}
            block
            title={staleIssue ? 'Cannot analyze: Issue older than 1 hour (process likely terminated)' : 'Run AI diagnosis on this issue'}
          >
            {analyzing ? 'Analyzing...' : staleIssue ? 'Issue Too Old' : 'Ask AI to Analyze'}
          </Button>
        )}
      </Space>
    </Card>
  );
};
