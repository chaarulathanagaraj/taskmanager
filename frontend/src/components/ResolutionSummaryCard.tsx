import { Card, Divider, List, Space, Tag, Typography } from 'antd';
import type { IssueResolutionSummary } from '../types';

const { Text, Paragraph } = Typography;

interface ResolutionSummaryCardProps {
  resolution: IssueResolutionSummary;
}

const severityColors: Record<string, string> = {
  LOW: 'blue',
  MEDIUM: 'orange',
  HIGH: 'red',
  CRITICAL: 'purple',
};

const sourceColors: Record<string, string> = {
  MANUAL: 'green',
  AUTOMATED: 'geekblue',
  IGNORED: 'default',
};

/**
 * Compact card that explains how an issue was resolved and how to verify it manually.
 */
export const ResolutionSummaryCard: React.FC<ResolutionSummaryCardProps> = ({ resolution }) => {
  const verificationSteps = [
    `Open the Issues page and confirm issue #${resolution.issueId} is marked RESOLVED.`,
    `Check the affected process ${resolution.processName} (PID ${resolution.affectedPid}) no longer shows the original warning signs.`,
    'Review the Actions page to confirm the matching remediation action completed successfully.',
    'Check the Logs page for the resolution event and verify there are no new follow-up alerts for the same PID.',
  ];

  return (
    <Card size="small" bordered style={{ width: '100%' }}>
      <Space direction="vertical" size="small" style={{ width: '100%' }}>
        <Space wrap>
          <Tag color={severityColors[resolution.severity] || 'default'}>
            {resolution.severity}
          </Tag>
          <Tag color={sourceColors[resolution.source] || 'default'}>
            {resolution.source}
          </Tag>
          <Tag color={resolution.resolved ? 'green' : 'orange'}>
            {resolution.resolved ? 'Resolved' : 'Pending Review'}
          </Tag>
        </Space>

        <div>
          <Text strong>
            {resolution.processName} (PID {resolution.affectedPid})
          </Text>
          <div>
            <Text type="secondary">
              Issue #{resolution.issueId} {resolution.issueType.replace(/_/g, ' ').toLowerCase()}
            </Text>
          </div>
        </div>

        <Paragraph style={{ marginBottom: 0 }}>
          {resolution.message}
        </Paragraph>

        {resolution.actionsTaken?.length ? (
          <div>
            <Text strong>What the resolving agent did</Text>
            <List
              size="small"
              dataSource={resolution.actionsTaken}
              renderItem={(item) => <List.Item style={{ paddingInline: 0 }}>{item}</List.Item>}
            />
          </div>
        ) : null}

        <Divider style={{ margin: '8px 0' }} />

        <div>
          <Text strong>Manual verification</Text>
          <List
            size="small"
            dataSource={verificationSteps}
            renderItem={(item) => <List.Item style={{ paddingInline: 0 }}>{item}</List.Item>}
          />
        </div>

        {resolution.resolvedAt ? (
          <Text type="secondary">
            Resolved at {new Date(resolution.resolvedAt).toLocaleString()}
          </Text>
        ) : null}
      </Space>
    </Card>
  );
};