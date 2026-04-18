import { Space, Badge, Avatar, Dropdown, Typography } from 'antd';
import {
  BellOutlined,
  UserOutlined,
  SettingOutlined,
  LogoutOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { useSystemHealth, useIssues } from '../../hooks/useMetrics';
import type { MenuProps } from 'antd';
import type { DiagnosticIssue } from '../../types';

const { Text } = Typography;

/**
 * Application header with status indicators and user menu
 */
export const AppHeader: React.FC = () => {
  const { data: health } = useSystemHealth();
  const { data: issues = [] } = useIssues();

  // Filter critical issues for notifications
  const criticalIssues = issues.filter((issue: DiagnosticIssue) => issue.severity === 'CRITICAL');

  const notificationItems: MenuProps['items'] = criticalIssues.length > 0
    ? criticalIssues.map((issue: DiagnosticIssue) => ({
        key: `issue-${issue.id}`,
        icon: <WarningOutlined style={{ color: '#ff4d4f' }} />,
        label: (
          <div style={{ maxWidth: 300, whiteSpace: 'normal' }}>
            <Text strong>{issue.type}</Text>
            <br />
            <Text type="secondary" style={{ fontSize: '12px' }}>
              {issue.details}
            </Text>
          </div>
        ),
      }))
    : [
        {
          key: 'no-notifications',
          label: <Text type="secondary">No critical issues</Text>,
          disabled: true,
        },
      ];

  const userMenuItems: MenuProps['items'] = [
    {
      key: 'settings',
      icon: <SettingOutlined />,
      label: 'Settings',
    },
    {
      type: 'divider',
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: 'Logout',
      danger: true,
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

        {/* Notifications */}
        <Dropdown menu={{ items: notificationItems }} placement="bottomRight" trigger={['click']}>
          <Badge count={criticalIssues.length} offset={[-5, 5]} color="#ff4d4f">
            <BellOutlined style={{ fontSize: 20, cursor: 'pointer' }} />
          </Badge>
        </Dropdown>

        {/* User Menu */}
        <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
          <Space style={{ cursor: 'pointer' }}>
            <Avatar icon={<UserOutlined />} />
            <Text>Admin</Text>
          </Space>
        </Dropdown>
      </Space>
    </div>
  );
};
