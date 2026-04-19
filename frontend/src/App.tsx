import { useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ToastContainer } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
import { ThemeProvider, useTheme } from './components/ThemeProvider';
import ErrorBoundary, { PageErrorBoundary } from './components/ErrorBoundary';
import { MainLayout, AppSidebar, AppHeader, AppFooter, AppBreadcrumb } from './components/Layout';
import Dashboard from './pages/Dashboard';
import IssuesPage from './pages/IssuesPage';
import SettingsPage from './pages/SettingsPage';
import LogsPage from './pages/LogsPage';
import MetricsPage from './pages/MetricsPage';
import ProcessesPage from './pages/ProcessesPage';
import NotFoundPage from './pages/NotFoundPage';

// Create React Query client
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 2,
      staleTime: 5000,
    },
  },
});

/**
 * Main application component with layout
 */
function AppContent() {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  return (
    <MainLayout
      sidebar={<AppSidebar />}
      header={<AppHeader />}
      footer={<AppFooter />}
      sidebarCollapsed={sidebarCollapsed}
      onSidebarCollapse={setSidebarCollapsed}
    >
      <AppBreadcrumb />
      <Routes>
        <Route path="/" element={<Dashboard />} />
        <Route path="/issues" element={<IssuesPage />} />
        <Route path="/actions" element={<Navigate to="/" replace />} />
        <Route path="/ai-history" element={<Navigate to="/" replace />} />
        <Route path="/metrics" element={<MetricsPage />} />
        <Route path="/processes" element={<ProcessesPage />} />
        <Route path="/logs" element={<LogsPage />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/about" element={<Navigate to="/settings" replace />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </MainLayout>
  );
}

/**
 * Inner component that uses theme context
 */
function ToastWithTheme() {
  const { isDark } = useTheme();
  return (
    <ToastContainer
      position="top-right"
      autoClose={5000}
      hideProgressBar={false}
      newestOnTop
      closeOnClick
      rtl={false}
      pauseOnFocusLoss
      draggable
      pauseOnHover
      theme={isDark ? 'dark' : 'light'}
    />
  );
}

function App() {
  return (
    <ErrorBoundary>
      <ThemeProvider>
        <QueryClientProvider client={queryClient}>
          <BrowserRouter>
            <PageErrorBoundary>
              <AppContent />
            </PageErrorBoundary>
            <ToastWithTheme />
          </BrowserRouter>
        </QueryClientProvider>
      </ThemeProvider>
    </ErrorBoundary>
  );
}

export default App;
