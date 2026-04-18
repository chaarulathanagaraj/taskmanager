import React from 'react';
import { Switch, Dropdown, Space, Button, Tooltip } from 'antd';
import { 
  BulbOutlined, 
  BulbFilled, 
  SettingOutlined,
  CheckOutlined,
} from '@ant-design/icons';
import { useTheme } from './ThemeProvider';

/**
 * Simple theme toggle switch.
 */
export const ThemeToggle: React.FC = () => {
  const { isDark, toggleTheme } = useTheme();

  return (
    <Tooltip title={isDark ? 'Switch to light mode' : 'Switch to dark mode'}>
      <Switch
        checked={isDark}
        onChange={toggleTheme}
        checkedChildren={<BulbFilled />}
        unCheckedChildren={<BulbOutlined />}
      />
    </Tooltip>
  );
};

/**
 * Theme mode selector dropdown.
 */
export const ThemeModeSelector: React.FC = () => {
  const { themeMode, setThemeMode, isDark } = useTheme();

  const menuItems = [
    {
      key: 'light',
      label: (
        <Space>
          <BulbOutlined />
          Light
          {themeMode === 'light' && <CheckOutlined />}
        </Space>
      ),
      onClick: () => setThemeMode('light'),
    },
    {
      key: 'dark',
      label: (
        <Space>
          <BulbFilled />
          Dark
          {themeMode === 'dark' && <CheckOutlined />}
        </Space>
      ),
      onClick: () => setThemeMode('dark'),
    },
    {
      key: 'system',
      label: (
        <Space>
          <SettingOutlined />
          System
          {themeMode === 'system' && <CheckOutlined />}
        </Space>
      ),
      onClick: () => setThemeMode('system'),
    },
  ];

  return (
    <Dropdown menu={{ items: menuItems }} trigger={['click']}>
      <Button icon={isDark ? <BulbFilled /> : <BulbOutlined />}>
        Theme
      </Button>
    </Dropdown>
  );
};

/**
 * Icon-only theme toggle button.
 */
export const ThemeIconButton: React.FC = () => {
  const { isDark, toggleTheme } = useTheme();

  return (
    <Tooltip title={isDark ? 'Switch to light mode' : 'Switch to dark mode'}>
      <Button
        type="text"
        icon={isDark ? <BulbFilled /> : <BulbOutlined />}
        onClick={toggleTheme}
        style={{ fontSize: 18 }}
      />
    </Tooltip>
  );
};

export default ThemeToggle;
