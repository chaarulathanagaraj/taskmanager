import { useState } from 'react';
import { Card, Input, Space, Tag, Spin, Alert, Row, Col, Statistic, Tooltip, Typography } from 'antd';
import { SearchOutlined, AppstoreOutlined, InfoCircleOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { ProcessTable } from '../components/ProcessTable';
import { processesApi } from '../api/client';
import type { ProcessInfo } from '../types';

const { Search } = Input;
const { Text } = Typography;

/**
 * Processes page displaying all running system processes
 */
const ProcessesPage: React.FC = () => {
  const [searchText, setSearchText] = useState('');

  // Fetch all processes from the new API
  const { data: processData, isLoading, error } = useQuery({
    queryKey: ['processes'],
    queryFn: () => processesApi.getAll('cpu', 1000).then((res) => res.data),
    refetchInterval: 5000, // Refresh every 5 seconds
  });

  if (isLoading) {
    return (
      <div style={{ textAlign: 'center', padding: '100px' }}>
        <Spin size="large" tip="Loading processes..." />
      </div>
    );
  }

  if (error) {
    return (
      <Alert
        message="Failed to load processes"
        description="Unable to connect to backend server. Please ensure the backend is running on port 8080."
        type="error"
        showIcon
      />
    );
  }

  const processes: ProcessInfo[] = processData?.processes || [];
  const stats = processData?.statistics || {
    totalCpu: 0,
    totalMemoryGB: 0,
    totalThreads: 0,
    averageCpu: 0,
    averageMemoryMB: 0,
  };
  const totalSystemProcesses = processData?.totalSystemProcesses || 0;

  // Filter processes based on search text
  const filteredProcesses = processes.filter((process: ProcessInfo) =>
    process.name.toLowerCase().includes(searchText.toLowerCase()) ||
    process.pid.toString().includes(searchText)
  );

  // Get top resource consumers
  const topCpuProcesses = [...processes]
    .filter(p => p.cpuPercent > 50)
    .sort((a, b) => b.cpuPercent - a.cpuPercent)
    .slice(0, 5);

  const topMemoryProcesses = [...processes]
    .filter(p => p.memoryBytes > 500 * 1024 * 1024)
    .sort((a, b) => b.memoryBytes - a.memoryBytes)
    .slice(0, 5);

  const topThreadProcesses = [...processes]
    .filter(p => p.threadCount > 100)
    .sort((a, b) => b.threadCount - a.threadCount)
    .slice(0, 5);

  // Sort processes (already sorted by backend, but filter affects order)
  const sortedProcesses = [...filteredProcesses];

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      {/* Statistics Row */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={8}>
          <Card hoverable>
            <Statistic
              title="Total Processes"
              value={totalSystemProcesses}
              prefix={<AppstoreOutlined />}
            />
            <div style={{ marginTop: 8 }}>
              <Tag color="blue">showing {processes.length}</Tag>
            </div>
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card hoverable>
            <Statistic
              title={
                <Space>
                  <span>Total CPU Usage</span>
                  <Tooltip title="Sum of CPU usage across all processes. Can exceed 100% on multi-core systems (e.g., 400% on 4-core CPU).">
                    <InfoCircleOutlined style={{ color: '#1890ff' }} />
                  </Tooltip>
                </Space>
              }
              value={stats.totalCpu.toFixed(2)}
              suffix="%"
              valueStyle={{
                color: stats.totalCpu > 90 ? '#ff4d4f' : stats.totalCpu > 70 ? '#faad14' : '#52c41a'
              }}
            />
            <div style={{ marginTop: 8, fontSize: '12px', color: '#666' }}>
              <Text type="secondary">Cumulative across all processes</Text>
            </div>
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card hoverable>
            <Statistic
              title={
                <Space>
                  <span>Total Memory Used</span>
                  <Tooltip title="Total memory consumed by all running processes shown">
                    <InfoCircleOutlined style={{ color: '#1890ff' }} />
                  </Tooltip>
                </Space>
              }
              value={stats.totalMemoryGB.toFixed(2)}
              suffix="GB"
            />
            <div style={{ marginTop: 8, fontSize: '12px', color: '#666' }}>
              <Text type="secondary">By {processes.length} processes</Text>
            </div>
          </Card>
        </Col>
      </Row>

      {/* Process Table with Search and Filters */}
      <Card
        title={
          <Space>
            <AppstoreOutlined />
            <span>Running Processes</span>
            <Tag color="blue">{filteredProcesses.length} processes</Tag>
          </Space>
        }
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Search
            placeholder="Search by process name or PID..."
            allowClear
            enterButton={<SearchOutlined />}
            size="large"
            onChange={(e) => setSearchText(e.target.value)}
            onSearch={setSearchText}
          />

          <ProcessTable
            processes={sortedProcesses}
            loading={isLoading}
          />
        </Space>
      </Card>

      {/* Additional Process Statistics */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12}>
          <Card title="Process Statistics">
            <Space direction="vertical" style={{ width: '100%' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Showing Processes:</span>
                <strong>{processes.length} of {totalSystemProcesses}</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Filtered Processes:</span>
                <strong>{filteredProcesses.length}</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Average CPU:</span>
                <strong>{stats.averageCpu.toFixed(2)}%</strong>
              </div>
            </Space>
          </Card>
        </Col>
        <Col xs={24} sm={12}>
          <Card title="Top Resource Consumers">
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              {/* High CPU Processes */}
              <div>
                <div style={{ marginBottom: 8 }}>
                  <Tag color="red">High CPU (&gt; 50%)</Tag>
                  <strong> {topCpuProcesses.length} processes</strong>
                </div>
                {topCpuProcesses.length > 0 ? (
                  <div style={{ paddingLeft: 8, borderLeft: '3px solid #ff4d4f' }}>
                    {topCpuProcesses.map((p, idx) => (
                      <div key={p.pid} style={{ fontSize: '12px', marginBottom: 4 }}>
                        {idx + 1}. <strong>{p.name}</strong> (PID: {p.pid}) - {p.cpuPercent.toFixed(2)}%
                      </div>
                    ))}
                  </div>
                ) : (
                  <Text type="secondary" style={{ fontSize: '12px' }}>No high CPU processes</Text>
                )}
              </div>

              {/* High Memory Processes */}
              <div>
                <div style={{ marginBottom: 8 }}>
                  <Tag color="orange">High Memory (&gt; 500 MB)</Tag>
                  <strong> {topMemoryProcesses.length} processes</strong>
                </div>
                {topMemoryProcesses.length > 0 ? (
                  <div style={{ paddingLeft: 8, borderLeft: '3px solid #faad14' }}>
                    {topMemoryProcesses.map((p, idx) => (
                      <div key={p.pid} style={{ fontSize: '12px', marginBottom: 4 }}>
                        {idx + 1}. <strong>{p.name}</strong> (PID: {p.pid}) - {(p.memoryBytes / 1024 / 1024).toFixed(0)} MB
                      </div>
                    ))}
                  </div>
                ) : (
                  <Text type="secondary" style={{ fontSize: '12px' }}>No high memory processes</Text>
                )}
              </div>

              {/* Many Threads Processes */}
              <div>
                <div style={{ marginBottom: 8 }}>
                  <Tag color="yellow">Many Threads (&gt; 100)</Tag>
                  <strong> {topThreadProcesses.length} processes</strong>
                </div>
                {topThreadProcesses.length > 0 ? (
                  <div style={{ paddingLeft: 8, borderLeft: '3px solid #fadb14' }}>
                    {topThreadProcesses.map((p, idx) => (
                      <div key={p.pid} style={{ fontSize: '12px', marginBottom: 4 }}>
                        {idx + 1}. <strong>{p.name}</strong> (PID: {p.pid}) - {p.threadCount} threads
                      </div>
                    ))}
                  </div>
                ) : (
                  <Text type="secondary" style={{ fontSize: '12px' }}>No high thread count processes</Text>
                )}
              </div>
            </Space>
          </Card>
        </Col>
      </Row>
    </Space>
  );
};

export default ProcessesPage;
