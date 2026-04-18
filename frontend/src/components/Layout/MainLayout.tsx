import { Layout as AntLayout, theme } from 'antd';
import type { ReactNode } from 'react';

const { Header, Content, Footer, Sider } = AntLayout;

interface MainLayoutProps {
  header?: ReactNode;
  sidebar?: ReactNode;
  footer?: ReactNode;
  children: ReactNode;
  sidebarCollapsed?: boolean;
  onSidebarCollapse?: (collapsed: boolean) => void;
}

/**
 * Main application layout component
 */
export const MainLayout: React.FC<MainLayoutProps> = ({
  header,
  sidebar,
  footer,
  children,
  sidebarCollapsed = false,
  onSidebarCollapse,
}) => {
  const { token } = theme.useToken();

  return (
    <AntLayout style={{ minHeight: '100vh', background: token.colorBgLayout }}>
      {sidebar && (
        <Sider
          collapsible
          collapsed={sidebarCollapsed}
          onCollapse={onSidebarCollapse}
          width={260}
          style={{
            overflow: 'auto',
            height: '100vh',
            position: 'fixed',
            left: 0,
            top: 0,
            bottom: 0,
            boxShadow: '2px 0 8px 0 rgba(0,0,0,0.15)',
            zIndex: 10,
          }}
        >
          {sidebar}
        </Sider>
      )}

      <AntLayout style={{ marginLeft: sidebar ? (sidebarCollapsed ? 80 : 260) : 0, background: 'transparent' }}>
        {header && (
          <Header
            style={{
              padding: '0 24px',
              background: token.colorBgContainer,
              position: 'sticky',
              top: 0,
              zIndex: 5,
              width: '100%',
              display: 'flex',
              alignItems: 'center',
              boxShadow: '0 1px 2px 0 rgba(0,0,0,0.03)',
            }}
          >
            {header}
          </Header>
        )}

        <Content
          style={{
            margin: '24px',
            minHeight: 280,
          }}
        >
          {children}
        </Content>

        {footer && (
          <Footer style={{ textAlign: 'center', background: 'transparent', color: token.colorTextSecondary }}>
            {footer}
          </Footer>
        )}
      </AntLayout>
    </AntLayout>
  );
};
