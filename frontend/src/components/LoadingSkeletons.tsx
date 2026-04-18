import React from 'react';
import { Skeleton, Card, Row, Col, Space, List } from 'antd';

/**
 * Loading skeleton for metric cards.
 */
export const MetricsCardSkeleton: React.FC = () => (
  <Card>
    <Skeleton.Input active style={{ width: 80, marginBottom: 8 }} size="small" />
    <Skeleton.Input active style={{ width: 120 }} size="large" />
  </Card>
);

/**
 * Loading skeleton for metrics chart.
 */
export const MetricsChartSkeleton: React.FC = () => (
  <Card>
    <Skeleton.Input active style={{ width: 150, marginBottom: 16 }} size="small" />
    <div style={{ height: 300, display: 'flex', alignItems: 'flex-end', gap: 4 }}>
      {Array.from({ length: 20 }).map((_, i) => (
        <Skeleton.Button
          key={i}
          active
          style={{
            width: '100%',
            maxWidth: 40,
            height: `${Math.random() * 60 + 40}%`,
          }}
        />
      ))}
    </div>
  </Card>
);

/**
 * Loading skeleton for process table.
 */
export const ProcessTableSkeleton: React.FC<{ rows?: number }> = ({ rows = 5 }) => (
  <Card>
    {/* Table header */}
    <div style={{ 
      display: 'flex', 
      gap: 16, 
      padding: '12px 0', 
      borderBottom: '1px solid #f0f0f0',
      marginBottom: 8,
    }}>
      <Skeleton.Input active style={{ width: 60 }} size="small" />
      <Skeleton.Input active style={{ width: 150 }} size="small" />
      <Skeleton.Input active style={{ width: 80 }} size="small" />
      <Skeleton.Input active style={{ width: 80 }} size="small" />
      <Skeleton.Input active style={{ width: 80 }} size="small" />
    </div>
    {/* Table rows */}
    {Array.from({ length: rows }).map((_, i) => (
      <div 
        key={i} 
        style={{ 
          display: 'flex', 
          gap: 16, 
          padding: '12px 0',
          borderBottom: i < rows - 1 ? '1px solid #f0f0f0' : 'none',
        }}
      >
        <Skeleton.Input active style={{ width: 50 }} size="small" />
        <Skeleton.Input active style={{ width: 140 }} size="small" />
        <Skeleton.Input active style={{ width: 70 }} size="small" />
        <Skeleton.Input active style={{ width: 70 }} size="small" />
        <Skeleton.Input active style={{ width: 70 }} size="small" />
      </div>
    ))}
  </Card>
);

/**
 * Loading skeleton for issue list.
 */
export const IssueListSkeleton: React.FC<{ count?: number }> = ({ count = 3 }) => (
  <List
    dataSource={Array.from({ length: count })}
    renderItem={() => (
      <Card style={{ marginBottom: 16 }}>
        <Skeleton active avatar={{ shape: 'square', size: 48 }} paragraph={{ rows: 2 }} />
      </Card>
    )}
  />
);

/**
 * Loading skeleton for dashboard page.
 */
export const DashboardSkeleton: React.FC = () => (
  <Space direction="vertical" style={{ width: '100%' }} size="large">
    {/* Stats row */}
    <Row gutter={[16, 16]}>
      {Array.from({ length: 4 }).map((_, i) => (
        <Col xs={24} sm={12} lg={6} key={i}>
          <MetricsCardSkeleton />
        </Col>
      ))}
    </Row>

    {/* Charts row */}
    <Row gutter={[16, 16]}>
      <Col xs={24} lg={16}>
        <MetricsChartSkeleton />
      </Col>
      <Col xs={24} lg={8}>
        <Card>
          <Skeleton.Input active style={{ width: 120, marginBottom: 16 }} size="small" />
          <Space direction="vertical" style={{ width: '100%' }}>
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} style={{ display: 'flex', justifyContent: 'space-between' }}>
                <Skeleton.Input active style={{ width: 80 }} size="small" />
                <Skeleton.Input active style={{ width: 40 }} size="small" />
              </div>
            ))}
          </Space>
        </Card>
      </Col>
    </Row>

    {/* Table */}
    <ProcessTableSkeleton rows={5} />
  </Space>
);

/**
 * Loading skeleton for action history page.
 */
export const ActionHistorySkeleton: React.FC = () => (
  <Card>
    <Skeleton.Input active style={{ width: 200, marginBottom: 16 }} />
    <ProcessTableSkeleton rows={8} />
  </Card>
);

/**
 * Loading skeleton for settings page.
 */
export const SettingsSkeleton: React.FC = () => (
  <Card>
    <Skeleton.Input active style={{ width: 180, marginBottom: 24 }} />
    <Space direction="vertical" style={{ width: '100%' }} size="large">
      {Array.from({ length: 5 }).map((_, i) => (
        <div key={i} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <Skeleton.Input active style={{ width: 150, marginBottom: 4 }} size="small" />
            <br />
            <Skeleton.Input active style={{ width: 250 }} size="small" />
          </div>
          <Skeleton.Button active style={{ width: 44 }} />
        </div>
      ))}
    </Space>
  </Card>
);

/**
 * Loading skeleton for AI analysis card.
 */
export const AIAnalysisSkeleton: React.FC = () => (
  <Card>
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
        <Skeleton.Input active style={{ width: 120 }} size="small" />
        <Skeleton.Button active style={{ width: 60 }} size="small" />
      </div>
      <Skeleton.Input active style={{ width: '100%' }} size="large" />
      <Skeleton paragraph={{ rows: 3 }} active />
      <div style={{ display: 'flex', gap: 8 }}>
        <Skeleton.Button active style={{ width: 100 }} />
        <Skeleton.Button active style={{ width: 100 }} />
      </div>
    </Space>
  </Card>
);

/**
 * Generic loading skeleton with customizable rows.
 */
export const GenericSkeleton: React.FC<{ 
  rows?: number;
  avatar?: boolean;
  title?: boolean;
}> = ({ rows = 3, avatar = false, title = true }) => (
  <Card>
    <Skeleton 
      active 
      avatar={avatar} 
      title={title} 
      paragraph={{ rows }} 
    />
  </Card>
);

export default {
  MetricsCardSkeleton,
  MetricsChartSkeleton,
  ProcessTableSkeleton,
  IssueListSkeleton,
  DashboardSkeleton,
  ActionHistorySkeleton,
  SettingsSkeleton,
  AIAnalysisSkeleton,
  GenericSkeleton,
};
