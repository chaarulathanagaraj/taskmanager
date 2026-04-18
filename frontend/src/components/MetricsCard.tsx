import { Card, Statistic } from 'antd';
import { ArrowUpOutlined, ArrowDownOutlined } from '@ant-design/icons';

interface MetricsCardProps {
  title: string;
  value: number;
  suffix: string;
  threshold: number;
  precision?: number;
}

/**
 * Card component displaying a single metric with color-coded threshold indicators
 */
export const MetricsCard: React.FC<MetricsCardProps> = ({
  title,
  value,
  suffix,
  threshold,
  precision = 2,
}) => {
  // Determine color based on threshold
  const getColor = () => {
    if (value > threshold) return '#ff4d4f'; // Red
    if (value > threshold * 0.7) return '#faad14'; // Orange
    return '#52c41a'; // Green
  };

  const color = getColor();
  const isHigh = value > threshold;

  return (
    <Card>
      <Statistic
        title={title}
        value={value}
        precision={precision}
        suffix={suffix}
        valueStyle={{ color }}
        prefix={isHigh ? <ArrowUpOutlined /> : <ArrowDownOutlined />}
      />
    </Card>
  );
};
