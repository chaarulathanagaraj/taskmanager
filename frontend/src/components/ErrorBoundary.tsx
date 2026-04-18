import React, { Component } from 'react';
import type { ErrorInfo, ReactNode } from 'react';
import { Result, Button, Typography, Card } from 'antd';
import { ReloadOutlined, BugOutlined } from '@ant-design/icons';

const { Text, Paragraph } = Typography;

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
  onError?: (error: Error, errorInfo: ErrorInfo) => void;
}

interface State {
  hasError: boolean;
  error: Error | null;
  errorInfo: ErrorInfo | null;
}

/**
 * Error boundary component for graceful error handling.
 * 
 * Features:
 * - Catches JavaScript errors in child components
 * - Displays user-friendly error message
 * - Optional error reporting callback
 * - Retry functionality
 */
class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
    error: null,
    errorInfo: null,
  };

  public static getDerivedStateFromError(error: Error): Partial<State> {
    return { hasError: true, error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('ErrorBoundary caught an error:', error, errorInfo);
    this.setState({ errorInfo });
    
    // Call optional error handler
    this.props.onError?.(error, errorInfo);

    // Log to console in development
    if (import.meta.env.DEV) {
      console.group('Error Boundary Details');
      console.error('Error:', error);
      console.error('Error Info:', errorInfo);
      console.error('Component Stack:', errorInfo.componentStack);
      console.groupEnd();
    }
  }

  private handleRetry = () => {
    this.setState({ hasError: false, error: null, errorInfo: null });
  };

  private handleReload = () => {
    window.location.reload();
  };

  public render() {
    if (this.state.hasError) {
      // Use custom fallback if provided
      if (this.props.fallback) {
        return this.props.fallback;
      }

      // Default error UI
      return (
        <div style={{ padding: 24 }}>
          <Result
            status="error"
            title="Something went wrong"
            subTitle="We're sorry, but something unexpected happened. Please try again."
            extra={[
              <Button 
                key="retry" 
                type="primary" 
                icon={<ReloadOutlined />}
                onClick={this.handleRetry}
              >
                Try Again
              </Button>,
              <Button 
                key="reload"
                onClick={this.handleReload}
              >
                Reload Page
              </Button>,
            ]}
          >
            {import.meta.env.DEV && this.state.error && (
              <Card 
                size="small" 
                title={
                  <span>
                    <BugOutlined style={{ marginRight: 8 }} />
                    Error Details (Development Only)
                  </span>
                }
                style={{ marginTop: 16, textAlign: 'left' }}
              >
                <Paragraph>
                  <Text strong>Error:</Text>
                </Paragraph>
                <Paragraph code copyable>
                  {this.state.error.message}
                </Paragraph>
                
                {this.state.errorInfo && (
                  <>
                    <Paragraph>
                      <Text strong>Component Stack:</Text>
                    </Paragraph>
                    <Paragraph 
                      code 
                      style={{ 
                        maxHeight: 200, 
                        overflow: 'auto',
                        fontSize: 12,
                        whiteSpace: 'pre-wrap',
                      }}
                    >
                      {this.state.errorInfo.componentStack}
                    </Paragraph>
                  </>
                )}
              </Card>
            )}
          </Result>
        </div>
      );
    }

    return this.props.children;
  }
}

/**
 * Page-level error boundary with full-screen error display.
 */
export const PageErrorBoundary: React.FC<{ children: ReactNode }> = ({ children }) => (
  <ErrorBoundary
    fallback={
      <div 
        style={{ 
          minHeight: '100vh', 
          display: 'flex', 
          alignItems: 'center', 
          justifyContent: 'center',
          background: '#f0f2f5',
        }}
      >
        <Result
          status="500"
          title="Page Error"
          subTitle="This page encountered an error. Please try refreshing."
          extra={
            <Button 
              type="primary" 
              onClick={() => window.location.reload()}
            >
              Refresh Page
            </Button>
          }
        />
      </div>
    }
  >
    {children}
  </ErrorBoundary>
);

/**
 * Component-level error boundary with compact error display.
 */
export const ComponentErrorBoundary: React.FC<{ 
  children: ReactNode;
  name?: string;
}> = ({ children, name }) => (
  <ErrorBoundary
    fallback={
      <Card 
        size="small" 
        style={{ 
          textAlign: 'center', 
          background: '#fff2f0', 
          borderColor: '#ffccc7' 
        }}
      >
        <Text type="danger">
          {name ? `${name} failed to load` : 'Component failed to load'}
        </Text>
        <br />
        <Button 
          size="small" 
          type="link"
          onClick={() => window.location.reload()}
        >
          Reload
        </Button>
      </Card>
    }
  >
    {children}
  </ErrorBoundary>
);

export default ErrorBoundary;
