import { Row, Col, Card, Spin, Alert } from 'antd';
import { useDashboard } from '../hooks/useMetrics';
import { MetricsCard } from '../components/MetricsCard';
import { MetricsChart } from '../components/MetricsChart';
import { ProcessTable } from '../components/ProcessTable';

/**
 * Main dashboard page displaying system overview
 */
const Dashboard: React.FC = () => {
  const { data, isLoading, error } = useDashboard();

  if (isLoading) {
    return (
      <div style={{ textAlign: 'center', padding: '100px' }}>
        <Spin size="large" description="Loading dashboard..." />
      </div>
    );
  }

  if (error) {
    return (
      <Alert
        title="Failed to load dashboard"
        description="Unable to connect to backend server. Please ensure the backend is running on port 8080."
        type="error"
        showIcon
      />
    );
  }

  return (
    <div>
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} md={6}>
          <MetricsCard
            title="CPU Usage"
            value={data?.cpuUsage || 0}
            suffix="%"
            threshold={80}
          />
        </Col>
        <Col xs={24} sm={12} md={6}>
          <MetricsCard
            title="Memory"
            value={data?.memoryPercent || 0}
            suffix="%"
            threshold={85}
          />
        </Col>
        <Col xs={24} sm={12} md={6}>
          <MetricsCard
            title="Disk I/O"
            value={data?.diskIO || 0}
            suffix="MB/s"
            threshold={100}
          />
        </Col>
        <Col xs={24} sm={12} md={6}>
          <MetricsCard
            title="Network"
            value={data?.networkIO || 0}
            suffix="MB/s"
            threshold={50}
          />
        </Col>
      </Row>

      <Card title="System Trends" style={{ marginTop: 24 }}>
        <MetricsChart />
      </Card>

      <Card title="Top Processes" style={{ marginTop: 24 }}>
        <ProcessTable processes={data?.topProcesses || []} />
      </Card>

      <Row gutter={[16, 16]} style={{ marginTop: 24 }}>
        <Col xs={24} md={12}>
          <Card title="Active Issues">
            <p>Total: {data?.activeIssues?.length || 0}</p>
            {/* Issue list will be added */}
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card title="Recent Actions">
            <p>Total: {data?.recentActions?.length || 0}</p>
            {/* Action list will be added */}
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default Dashboard;
