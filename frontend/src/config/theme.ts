import type { ThemeConfig } from 'antd';

/**
 * Light theme configuration for Ant Design
 */
export const lightTheme: ThemeConfig = {
  token: {
    // Primary colors
    colorPrimary: '#2563eb', // Taildwind blue-600
    colorSuccess: '#10b981',
    colorWarning: '#f59e0b',
    colorError: '#ef4444',
    colorInfo: '#3b82f6',

    // Background
    colorBgBase: '#ffffff',
    colorBgLayout: '#f8fafc', // Light slate
    colorBgContainer: '#ffffff',
    colorBgElevated: '#ffffff',

    // Text colors
    colorTextBase: '#0f172a',
    colorText: '#1e293b',
    colorTextSecondary: '#64748b',
    colorTextTertiary: '#94a3b8',

    // Border
    colorBorder: '#e2e8f0',
    colorBorderSecondary: '#f1f5f9',

    // Border radius
    borderRadius: 8,
    borderRadiusLG: 12,
    borderRadiusSM: 6,

    // Font
    fontFamily: `'Plus Jakarta Sans', 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif`,
    fontSize: 14,

    // Control height
    controlHeight: 36,
    controlHeightLG: 44,
    controlHeightSM: 28,

    // Screen breakpoints
    screenXS: 480,
    screenSM: 576,
    screenMD: 768,
    screenLG: 992,
    screenXL: 1200,
    screenXXL: 1600,
  },
  components: {
    Layout: {
      siderBg: '#0f172a', // Dark blue sidebar
      headerBg: '#ffffff', // Clean white header
      bodyBg: '#f8fafc', // Subtle slate background for main area
    },
    Card: {
      borderRadiusLG: 12,
      boxShadowTertiary: '0 1px 3px 0 rgb(0 0 0 / 0.1), 0 1px 2px -1px rgb(0 0 0 / 0.1)',
      colorBorderSecondary: '#e2e8f0',
    },
    Menu: {
      itemBorderRadius: 8,
      itemSelectedBg: '#eff6ff',
      itemHoverBg: '#f1f5f9',
      itemSelectedColor: '#2563eb',
      darkItemBg: '#0f172a', // Background for dark sidebar
      darkSubMenuItemBg: '#0f172a',
      darkItemSelectedBg: '#1e293b', // Active item in sidebar
      darkItemHoverBg: '#1e293b',
      darkItemColor: '#94a3b8', // Unselected text in sidebar
      darkItemSelectedColor: '#ffffff',
      darkItemHoverColor: '#ffffff',
    },
    Table: {
      headerBg: '#f8fafc',
      headerBorderRadius: 8,
      borderColor: '#e2e8f0',
    },
    Button: {
      primaryShadow: '0 4px 6px -1px rgb(37 99 235 / 0.2), 0 2px 4px -2px rgb(37 99 235 / 0.2)',
      defaultShadow: '0 1px 2px 0 rgb(0 0 0 / 0.05)',
      controlHeight: 36,
    },
    Alert: {
      borderRadiusLG: 8,
    },
    Badge: {
      textFontSize: 12,
    },
    Statistic: {
      contentFontSize: 28,
    },
  },
};

/**
 * Dark theme configuration for Ant Design
 */
export const darkTheme: ThemeConfig = {
  token: {
    // Primary colors (slightly lighter for dark mode)
    colorPrimary: '#1890ff',
    colorSuccess: '#52c41a',
    colorWarning: '#faad14',
    colorError: '#ff4d4f',
    colorInfo: '#1890ff',

    // Background (dark)
    colorBgBase: '#141414',
    colorBgLayout: '#000000',
    colorBgContainer: '#1f1f1f',
    colorBgElevated: '#1f1f1f',

    // Text colors (inverted)
    colorTextBase: '#ffffff',
    colorText: 'rgba(255, 255, 255, 0.85)',
    colorTextSecondary: 'rgba(255, 255, 255, 0.65)',
    colorTextTertiary: 'rgba(255, 255, 255, 0.45)',

    // Border (darker)
    colorBorder: '#434343',
    colorBorderSecondary: '#303030',

    // Border radius
    borderRadius: 6,
    borderRadiusLG: 8,
    borderRadiusSM: 4,

    // Font
    fontFamily: `-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif`,
    fontSize: 14,

    // Control height
    controlHeight: 32,
    controlHeightLG: 40,
    controlHeightSM: 24,
  },
  components: {
    Card: {
      borderRadiusLG: 8,
      colorBgContainer: '#1f1f1f',
    },
    Menu: {
      itemBorderRadius: 6,
      itemSelectedBg: '#111b26',
      itemHoverBg: '#262626',
      darkItemBg: '#141414',
      darkSubMenuItemBg: '#000000',
    },
    Table: {
      headerBg: '#1d1d1d',
      headerBorderRadius: 8,
      colorBgContainer: '#1f1f1f',
    },
    Layout: {
      siderBg: '#141414',
      headerBg: '#141414',
      bodyBg: '#000000',
    },
    Alert: {
      borderRadiusLG: 8,
    },
  },
};

/**
 * Responsive breakpoints for media queries
 */
export const breakpoints = {
  xs: 480,
  sm: 576,
  md: 768,
  lg: 992,
  xl: 1200,
  xxl: 1600,
};

/**
 * Status colors for system health
 */
export const statusColors = {
  excellent: '#52c41a', // Green
  good: '#73d13d',      // Light green
  fair: '#faad14',      // Yellow
  poor: '#ff7a45',      // Orange
  critical: '#ff4d4f',  // Red
};

/**
 * Severity colors mapping
 */
export const severityColors = {
  LOW: '#1890ff',      // Blue
  MEDIUM: '#faad14',   // Yellow
  HIGH: '#ff7a45',     // Orange
  CRITICAL: '#ff4d4f', // Red
};
