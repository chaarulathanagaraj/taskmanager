import { Space, Avatar, Typography, Badge, Dropdown } from 'antd';
import { useNavigate } from 'react-router-dom';
import {
  UserOutlined,
  BellOutlined,
  WarningOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
} from '@ant-design/icons';
import { useSystemHealth, useIssues } from '../../hooks/useMetrics';
import type { MenuProps } from 'antd';
import type { DiagnosticIssue } from '../../types';

const { Text } = Typography;

/**
 * Application header with status indicators and user menu
 */
export const AppHeader: React.FC = () => {
  const navigate = useNavigate();
  const { data: health } = useSystemHealth();
  const { data: issues = [] } = useIssues();

  const PRIMARY_NOTIFICATION_TYPES = ['MEMORY_LEAK', 'RESOURCE_HOG'];
  const formatIssueType = (type: string) => type.replace(/_/g, ' ');

  // Keep one notification entry per issue type for primary types,
  // and merge all other issue types into a single "Other Issues" notification.
  const primaryIssuesByType = issues
    .filter((issue) => PRIMARY_NOTIFICATION_TYPES.includes(issue.type || 'UNKNOWN'))
    .reduce<Record<string, DiagnosticIssue[]>>((acc, issue) => {
      const key = issue.type || 'UNKNOWN';
      if (!acc[key]) {
        acc[key] = [];
      }
      acc[key].push(issue);
      return acc;
    }, {});

  const otherIssues = issues.filter((issue) => !PRIMARY_NOTIFICATION_TYPES.includes(issue.type || 'UNKNOWN'));
  const otherHasHighPriority = otherIssues.some((issue) => issue.severity === 'CRITICAL');

  const typeKeys = Object.keys(primaryIssuesByType);

  const primaryNotificationItems: NonNullable<MenuProps['items']> = typeKeys.map((typeKey) => {
    const grouped = primaryIssuesByType[typeKey];
    return {
      key: `type-${typeKey}`,
      icon: <WarningOutlined style={{ color: '#faad14' }} />,
      label: (
        <div style={{ maxWidth: 340, whiteSpace: 'normal' }}>
          <Text strong>{formatIssueType(typeKey)}</Text>
          <br />
          <Text type="secondary" style={{ fontSize: '12px' }}>
            Active: {grouped.length} issue(s)
          </Text>
        </div>
      ),
    };
  });

  const otherNotificationItems: NonNullable<MenuProps['items']> = otherIssues.length > 0
    ? [
      {
        key: 'type-OTHER',
        icon: <WarningOutlined style={{ color: '#faad14' }} />,
        label: (
          <div style={{ maxWidth: 340, whiteSpace: 'normal' }}>
            <Text strong>Other Issues</Text>
            <br />
            <Text type="secondary" style={{ fontSize: '12px' }}>
              Active: {otherIssues.length} issue(s){otherHasHighPriority ? ', includes high-priority items' : ''}
            </Text>
          </div>
        ),
      },
    ]
    : [];

  const activeNotificationCount = primaryNotificationItems.length + otherNotificationItems.length;

  const notificationItems: MenuProps['items'] = activeNotificationCount > 0
    ? [...primaryNotificationItems, ...otherNotificationItems]
    : [
      {
        key: 'no-active-notifications',
        label: <Text type="secondary">No active issue notifications</Text>,
        disabled: true,
      },
    ];

  const isHealthy = health === 'HEALTHY' || health === 'UP';

  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%' }}>
      <Typography.Title level={4} style={{ margin: 0, fontWeight: 700, letterSpacing: '-0.5px' }}>
        System Monitor
      </Typography.Title>

      <Space size="large">
        {/* System Health Status */}
        <Space>
          {isHealthy ? (
            <CheckCircleOutlined style={{ color: '#52c41a', fontSize: 16 }} />
          ) : (
            <CloseCircleOutlined style={{ color: '#ff4d4f', fontSize: 16 }} />
          )}
          <Text type={isHealthy ? 'success' : 'danger'}>
            {health || 'Unknown'}
          </Text>
        </Space>

        <Dropdown menu={{ items: notificationItems }} placement="bottomRight" trigger={['click']}>
          <Badge count={activeNotificationCount} offset={[-5, 5]} color="#faad14">
            <BellOutlined style={{ fontSize: 20, cursor: 'pointer' }} />
          </Badge>
        </Dropdown>

        <Space style={{ cursor: 'pointer' }} onClick={() => navigate('/settings')}>
          <Avatar icon={<UserOutlined />} />
        </Space>
      </Space>
    </div>
  );
};
