import { Card, Progress, Space, Tag, Typography } from 'antd';
import type { ExecutionUpdateMessage } from '../types';

const { Text } = Typography;

interface ExecutionProgressCardProps {
  update: ExecutionUpdateMessage;
}

/**
 * Displays live remediation progress and the concrete steps being executed.
 */
export const ExecutionProgressCard: React.FC<ExecutionProgressCardProps> = ({ update }) => {
  const progressValue = update.totalSteps && update.stepIndex
    ? Math.min(100, Math.round((update.stepIndex / update.totalSteps) * 100))
    : undefined;

  return (
    <Card size="small" bordered style={{ width: '100%' }}>
      <Space direction="vertical" size="small" style={{ width: '100%' }}>
        <Space wrap>
          <Tag color="blue">{update.status}</Tag>
          {update.stepIndex && update.totalSteps ? (
            <Tag color="geekblue">Step {update.stepIndex}/{update.totalSteps}</Tag>
          ) : null}
        </Space>

        <Text>{update.message}</Text>

        {typeof progressValue === 'number' ? (
          <Progress percent={progressValue} status={update.status === 'FAILED' ? 'exception' : 'active'} />
        ) : null}

        {update.steps?.length ? (
          <div>
            <Text strong>Execution steps</Text>
            <div>
              {update.steps.map((step, index) => (
                <div key={`${update.executionId}-${index}`}>
                  {index + 1}. {step}
                </div>
              ))}
            </div>
          </div>
        ) : null}

        {update.verificationMessage ? (
          <Text type="secondary">Verification: {update.verificationMessage}</Text>
        ) : null}
      </Space>
    </Card>
  );
};