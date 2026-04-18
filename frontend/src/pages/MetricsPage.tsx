import { Card, Row, Col, Statistic, Space, Tag, Spin, Alert, Tooltip } from 'antd';
import { 
  CloudServerOutlined, 
  HddOutlined,
  ThunderboltOutlined,
  InfoCircleOutlined,
  QuestionCircleOutlined,
} from '@ant-design/icons';
import { MetricsChart } from '../components/MetricsChart';
import { SystemHealthSuggestions } from '../components/SystemHealthSuggestions';
import { useMetrics, useDashboard } from '../hooks/useMetrics';
import { useEffect, useState } from 'react';

/**
 * System Metrics page displaying real-time performance metrics
 */
const MetricsPage: React.FC = () => {
  const { data: metrics, isLoading: metricsLoading, error: metricsError } = useMetrics(10);
  const { data: dashboard, isLoading: dashboardLoading, error: dashboardError } = useDashboard();
  
  // Track last updated time dynamically
  const [lastUpdated, setLastUpdated] = useState<Date>(new Date());

  useEffect(() => {
    if (metrics && metrics.length > 0) {
      setLastUpdated(new Date(metrics[0].timestamp));
    }
  }, [metrics]);

  const isLoading = metricsLoading || dashboardLoading;
  const error = metricsError || dashboardError;

  if (isLoading) {
    return (
      <div style={{ textAlign: 'center', padding: '100px' }}>
        <Spin size="large" tip="Loading metrics..." />
      </div>
    );
  }

  if (error) {
    return (
      <Alert
        message="Failed to load metrics"
        description="Unable to connect to backend server. Please ensure the backend is running on port 8080."
        type="error"
        showIcon
      />
    );
  }

  // Get the latest metric snapshot
  const latestMetric = metrics && metrics.length > 0 ? metrics[0] : null;

  // Helper to determine status color
  const networkIO = dashboard?.networkIO || 0;
  const processCount = 0 || 0;
  const activeIssuesCount = dashboard?.activeIssues?.length || 0;
  const getStatusColor = (value: number, thresholds: { warning: number; danger: number }) => {
    if (value >= thresholds.danger) return '#ff4d4f';
    if (value >= thresholds.warning) return '#faad14';
    return '#52c41a';
  };

  const cpuPercent = dashboard?.cpuUsage || 0;
  const memoryPercent = dashboard?.memoryPercent || 0;
  const diskIO = dashboard?.diskIO || 0;

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      {/* Current Stats Row */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} md={6}>
          <Card hoverable>
            <Statistic
              title="CPU Usage"
              value={cpuPercent.toFixed(2)}
              suffix="%"
              prefix={<ThunderboltOutlined />}
              valueStyle={{ color: getStatusColor(cpuPercent, { warning: 70, danger: 90 }) }}
            />
            <Tag 
              color={getStatusColor(cpuPercent, { warning: 70, danger: 90 })}
              style={{ marginTop: 8 }}
            >
              {cpuPercent >= 90 ? 'Critical' : cpuPercent >= 70 ? 'Warning' : 'Normal'}
            </Tag>
          </Card>
        </Col>

        <Col xs={24} sm={12} md={6}>
          <Card hoverable>
            <Statistic
              title="Memory Usage"
              value={memoryPercent.toFixed(2)}
              suffix="%"
              prefix={<CloudServerOutlined />}
              valueStyle={{ color: getStatusColor(memoryPercent, { warning: 80, danger: 95 }) }}
            />
            <Tag 
              color={getStatusColor(memoryPercent, { warning: 80, danger: 95 })}
              style={{ marginTop: 8 }}
            >
              {memoryPercent >= 95 ? 'Critical' : memoryPercent >= 80 ? 'Warning' : 'Normal'}
            </Tag>
          </Card>
        </Col>

        <Col xs={24} sm={12} md={6}>
          <Card hoverable>
            <Statistic
              title="Disk I/O"
              value={diskIO.toFixed(2)}
              suffix="MB/s"
              prefix={<HddOutlined />}
              valueStyle={{ color: getStatusColor(diskIO, { warning: 100, danger: 200 }) }}
            />
            <Tag 
              color={getStatusColor(diskIO, { warning: 100, danger: 200 })}
              style={{ marginTop: 8 }}
            >
              {diskIO >= 200 ? 'High' : diskIO >= 100 ? 'Moderate' : 'Normal'}
            </Tag>
          </Card>
        </Col>
      </Row>

      {/* Memory Details Row */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12}>
          <Card title="Memory Details">
            <Space direction="vertical" style={{ width: '100%' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Total Memory:</span>
                <strong>{((latestMetric?.memoryTotal || 0) / 1024 / 1024 / 1024).toFixed(2)} GB</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Used Memory:</span>
                <strong>{((latestMetric?.memoryUsed || 0) / 1024 / 1024 / 1024).toFixed(2)} GB</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Memory Percent:</span>
                <strong>{memoryPercent.toFixed(2)}%</strong>
              </div>
            </Space>
          </Card>
        </Col>

        <Col xs={24} sm={12}>
          <Card title="System Info">
            <Space direction="vertical" style={{ width: '100%' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Active Issues:</span>
                <strong style={{ color: activeIssuesCount > 0 ? '#ff4d4f' : '#52c41a' }}>
                  {activeIssuesCount}
                </strong>
              </div>
            </Space>
          </Card>
        </Col>
      </Row>

      {/* AI Health Suggestions */}
      <SystemHealthSuggestions
        cpuUsage={cpuPercent}
        memoryPercent={memoryPercent}
        diskIO={diskIO}
        networkIO={networkIO}
        processCount={processCount}
        activeIssuesCount={activeIssuesCount}
      />

      {/* Metrics Chart */}
      <Card title="Performance Trends (Last 10 Minutes)">
        <MetricsChart />
      </Card>
    </Space>
  );
};

export default MetricsPage;

