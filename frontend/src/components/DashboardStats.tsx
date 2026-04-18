import React from 'react';
import { Card, Statistic, Progress, Row, Col, Space, Tag, Typography, Tooltip } from 'antd';
import {
  CheckCircleOutlined,
  WarningOutlined,
  ClockCircleOutlined,
  ThunderboltOutlined,
  HeartOutlined,
  SafetyCertificateOutlined,
  LineChartOutlined,
} from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { statusColors } from '../config/theme';
import { MetricsCardSkeleton } from './LoadingSkeletons';

const { Text } = Typography;

/**
 * Fetch dashboard stats from backend.
 */
const fetchStats = async (endpoint: string) => {
  const response = await apiClient.get(`/dashboard/stats/${endpoint}`);
  return response.data;
};

/**
 * Issues resolved today widget.
 */
export const IssuesResolvedTodayWidget: React.FC = () => {
  const { data, isLoading, error } = useQuery({
    queryKey: ['stats', 'issues-resolved-today'],
    queryFn: () => fetchStats('issues-resolved-today'),
    refetchInterval: 30000, // Refresh every 30 seconds
  });

  if (isLoading) return <MetricsCardSkeleton />;
  if (error) return <Card><Text type="danger">Failed to load</Text></Card>;

  return (
    <Card hoverable>
      <Statistic
        title="Issues Resolved Today"
        value={data || 0}
        prefix={<CheckCircleOutlined style={{ color: statusColors.excellent }} />}
        valueStyle={{ color: statusColors.excellent }}
      />
    </Card>
  );
};

/**
 * Active issues widget.
 */
export const ActiveIssuesWidget: React.FC = () => {
  const { data, isLoading, error } = useQuery({
    queryKey: ['stats', 'active-issues'],
    queryFn: () => fetchStats('active-issues'),
    refetchInterval: 10000, // Refresh every 10 seconds
  });

  if (isLoading) return <MetricsCardSkeleton />;
  if (error) return <Card><Text type="danger">Failed to load</Text></Card>;

  const issueCount = data || 0;
  const color = issueCount === 0 ? statusColors.excellent : 
                issueCount < 3 ? statusColors.fair : statusColors.critical;

  return (
    <Card hoverable>
      <Statistic
        title="Active Issues"
        value={issueCount}
        prefix={<WarningOutlined style={{ color }} />}
        valueStyle={{ color }}
      />
    </Card>
  );
};

/**
 * System health score widget with progress ring.
 */
export const HealthScoreWidget: React.FC = () => {
  const { data, isLoading, error } = useQuery({
    queryKey: ['stats', 'health-breakdown'],
    queryFn: () => fetchStats('health-breakdown'),
    refetchInterval: 30000,
  });

  if (isLoading) return <MetricsCardSkeleton />;
  if (error) return <Card><Text type="danger">Failed to load</Text></Card>;

  const score = data?.overallScore || 0;
  const status = data?.status || 'Unknown';

  const getColor = (score: number) => {
    if (score >= 90) return statusColors.excellent;
    if (score >= 75) return statusColors.good;
    if (score >= 50) return statusColors.fair;
    if (score >= 25) return statusColors.poor;
    return statusColors.critical;
  };

  return (
    <Card hoverable>
      <Space direction="vertical" align="center" style={{ width: '100%' }}>
        <Progress
          type="dashboard"
          percent={score}
          strokeColor={getColor(score)}
          format={(percent) => (
            <div style={{ textAlign: 'center' }}>
              <div style={{ fontSize: 24, fontWeight: 'bold' }}>{percent}</div>
              <div style={{ fontSize: 12, color: '#666' }}>{status}</div>
            </div>
          )}
        />
        <Text strong>
          <HeartOutlined style={{ marginRight: 4 }} />
          System Health
        </Text>
      </Space>
    </Card>
  );
};

/**
 * Time saved widget.
 */
export const TimeSavedWidget: React.FC = () => {
  const { data, isLoading, error } = useQuery({
    queryKey: ['stats', 'time-saved'],
    queryFn: () => fetchStats('time-saved'),
    refetchInterval: 60000, // Refresh every minute
  });

  if (isLoading) return <MetricsCardSkeleton />;
  if (error) return <Card><Text type="danger">Failed to load</Text></Card>;

  return (
    <Card hoverable>
      <Statistic
        title={
          <Tooltip title="Estimated time saved by automated remediation">
            <span>Time Saved</span>
          </Tooltip>
        }
        value={data?.humanReadable || '0 minutes'}
        prefix={<ClockCircleOutlined style={{ color: '#1890ff' }} />}
        valueStyle={{ color: '#1890ff' }}
      />
      <Text type="secondary" style={{ fontSize: 12 }}>
        {data?.issuesResolved || 0} issues auto-resolved
      </Text>
    </Card>
  );
};

/**
 * Remediation success rate widget.
 */
export const RemediationRateWidget: React.FC = () => {
  const { data, isLoading, error } = useQuery({
    queryKey: ['stats', 'remediation-rate'],
    queryFn: () => fetchStats('remediation-rate'),
    refetchInterval: 30000,
  });

  if (isLoading) return <MetricsCardSkeleton />;
  if (error) return <Card><Text type="danger">Failed to load</Text></Card>;

  const rate = data?.successRate || 0;

  return (
    <Card hoverable>
      <Space direction="vertical" style={{ width: '100%' }}>
        <Statistic
          title="Remediation Success Rate"
          value={rate.toFixed(1)}
          suffix="%"
          prefix={<ThunderboltOutlined style={{ color: '#52c41a' }} />}
          valueStyle={{ color: '#52c41a' }}
        />
        <Progress
          percent={rate}
          strokeColor="#52c41a"
          showInfo={false}
          size="small"
        />
        <Space wrap size="small">
          <Tag color="success">{data?.successful || 0} success</Tag>
          <Tag color="error">{data?.failed || 0} failed</Tag>
          <Tag color="warning">{data?.blocked || 0} blocked</Tag>
        </Space>
      </Space>
    </Card>
  );
};

/**
 * Activity summary widget.
 */
export const ActivitySummaryWidget: React.FC = () => {
  const { data, isLoading, error } = useQuery({
    queryKey: ['stats', 'activity-summary'],
    queryFn: () => fetchStats('activity-summary'),
    refetchInterval: 30000,
  });

  if (isLoading) return <MetricsCardSkeleton />;
  if (error) return <Card><Text type="danger">Failed to load</Text></Card>;

  return (
    <Card 
      title={
        <span>
          <LineChartOutlined style={{ marginRight: 8 }} />
          Last 24 Hours
        </span>
      }
      hoverable
      size="small"
    >
      <Row gutter={16}>
        <Col span={8}>
          <Statistic
            title="Detected"
            value={data?.issuesDetected || 0}
            valueStyle={{ fontSize: 18 }}
          />
        </Col>
        <Col span={8}>
          <Statistic
            title="Resolved"
            value={data?.issuesResolved || 0}
            valueStyle={{ fontSize: 18, color: statusColors.excellent }}
          />
        </Col>
        <Col span={8}>
          <Statistic
            title="Actions"
            value={data?.actionsExecuted || 0}
            valueStyle={{ fontSize: 18 }}
          />
        </Col>
      </Row>
    </Card>
  );
};

/**
 * Issues by severity distribution widget.
 */
export const SeverityDistributionWidget: React.FC = () => {
  const { data, isLoading, error } = useQuery({
    queryKey: ['stats', 'issues-by-severity'],
    queryFn: () => fetchStats('issues-by-severity'),
    refetchInterval: 30000,
  });

  if (isLoading) return <MetricsCardSkeleton />;
  if (error) return <Card><Text type="danger">Failed to load</Text></Card>;

  const severities = [
    { key: 'CRITICAL', color: '#ff4d4f', label: 'Critical' },
    { key: 'HIGH', color: '#ff7a45', label: 'High' },
    { key: 'MEDIUM', color: '#faad14', label: 'Medium' },
    { key: 'LOW', color: '#1890ff', label: 'Low' },
  ];

  const total = Object.values(data || {}).reduce((acc: number, val: any) => acc + (val || 0), 0);

  return (
    <Card
      title={
        <span>
          <SafetyCertificateOutlined style={{ marginRight: 8 }} />
          Active Issues by Severity
        </span>
      }
      hoverable
      size="small"
    >
      <Space direction="vertical" style={{ width: '100%' }}>
        {severities.map(({ key, color, label }) => {
          const count = (data?.[key] as number) || 0;
          const percent = total > 0 ? (count / total) * 100 : 0;
          
          return (
            <div key={key}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                <Tag color={color}>{label}</Tag>
                <Text>{count}</Text>
              </div>
              <Progress 
                percent={percent} 
                strokeColor={color} 
                showInfo={false}
                size="small"
              />
            </div>
          );
        })}
      </Space>
    </Card>
  );
};

/**
 * Complete dashboard stats section.
 */
export const DashboardStatsSection: React.FC = () => {
  return (
    <Space direction="vertical" style={{ width: '100%' }} size="large">
      {/* Primary metrics row */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <HealthScoreWidget />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <ActiveIssuesWidget />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <IssuesResolvedTodayWidget />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <TimeSavedWidget />
        </Col>
      </Row>

      {/* Secondary metrics row */}
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <RemediationRateWidget />
        </Col>
        <Col xs={24} lg={12}>
          <ActivitySummaryWidget />
        </Col>
      </Row>

      {/* Severity distribution */}
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <SeverityDistributionWidget />
        </Col>
      </Row>
    </Space>
  );
};

export default {
  IssuesResolvedTodayWidget,
  ActiveIssuesWidget,
  HealthScoreWidget,
  TimeSavedWidget,
  RemediationRateWidget,
  ActivitySummaryWidget,
  SeverityDistributionWidget,
  DashboardStatsSection,
};
