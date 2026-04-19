import { Menu } from 'antd';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  DashboardOutlined,
  WarningOutlined,
  SettingOutlined,
  LineChartOutlined,
  FileTextOutlined,
} from '@ant-design/icons';
import type { MenuProps } from 'antd';

type MenuItem = Required<MenuProps>['items'][number];

/**
 * Application sidebar navigation menu
 */
export const AppSidebar: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();

  const menuItems: MenuItem[] = [
    {
      key: '/',
      icon: <DashboardOutlined />,
      label: 'Dashboard',
    },
    {
      key: '/issues',
      icon: <WarningOutlined />,
      label: 'Issues',
    },
    {
      key: 'monitoring',
      icon: <LineChartOutlined />,
      label: 'Monitoring',
      children: [
        {
          key: '/metrics',
          label: 'System Metrics',
        },
        {
          key: '/processes',
          label: 'Processes',
        },
        {
          key: '/logs',
          icon: <FileTextOutlined />,
          label: 'Logs',
        },
      ],
    },
    {
      key: '/settings',
      icon: <SettingOutlined />,
      label: 'Settings',
    },
  ];

  const handleMenuClick: MenuProps['onClick'] = (e) => {
    navigate(e.key);
  };

  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div
        style={{
          height: 64,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#ffffff',
          fontSize: 22,
          fontWeight: 800,
          letterSpacing: '0.5px',
          borderBottom: '1px solid rgba(255,255,255,0.05)',
          background: 'transparent',
          padding: '0 16px',
        }}
      >
        AIOS
      </div>

      <Menu
        theme="dark"
        mode="inline"
        selectedKeys={[location.pathname]}
        items={menuItems}
        onClick={handleMenuClick}
        style={{ flex: 1, borderRight: 0, padding: '16px 8px' }}
      />
    </div>
  );
};
