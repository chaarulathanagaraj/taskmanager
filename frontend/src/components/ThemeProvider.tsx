import React, { createContext, useContext, useState, useEffect } from 'react';
import type { ReactNode } from 'react';
import { ConfigProvider, theme as antdTheme, App } from 'antd';
import { lightTheme, darkTheme } from '../config/theme';

type ThemeMode = 'light' | 'dark' | 'system';

interface ThemeContextType {
  themeMode: ThemeMode;
  isDark: boolean;
  setThemeMode: (mode: ThemeMode) => void;
  toggleTheme: () => void;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

/**
 * Hook to access the theme context.
 */
export const useTheme = (): ThemeContextType => {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useTheme must be used within a ThemeProvider');
  }
  return context;
};

interface ThemeProviderProps {
  children: ReactNode;
}

/**
 * Theme provider component with dark mode support.
 * 
 * Features:
 * - Light/Dark/System theme modes
 * - Persists preference to localStorage
 * - Automatic system preference detection
 * - Ant Design ConfigProvider integration
 */
export const ThemeProvider: React.FC<ThemeProviderProps> = ({ children }) => {
  const [themeMode, setThemeModeState] = useState<ThemeMode>(() => {
    return 'light'; // Permanently forcing our new premium light mode!
  });

  const [systemPrefersDark, setSystemPrefersDark] = useState<boolean>(() => { 
    return false; // Ignore system dark mode, we want this specific customized layout
  });

  // Listen for system theme changes (disabled to force light theme)
  useEffect(() => {
  }, []);

  // Persist theme preference
  const setThemeMode = (mode: ThemeMode) => {
    setThemeModeState('light');
  };

  const toggleTheme = () => {
    // Disabled dark mode toggle
  };
  
  // Determine if dark mode is active
  const isDark = false;

  // Select theme config
  const themeConfig = lightTheme;
  useEffect(() => {
    document.body.classList.toggle('dark-mode', isDark);
    document.documentElement.setAttribute('data-theme', isDark ? 'dark' : 'light');
  }, [isDark]);

  return (
    <ThemeContext.Provider value={{ themeMode, isDark, setThemeMode, toggleTheme }}>
      <ConfigProvider
        theme={{
          ...themeConfig,
          algorithm: isDark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
        }}
      >
        <App>
          {children}
        </App>
      </ConfigProvider>
    </ThemeContext.Provider>
  );
};

export default ThemeProvider;
