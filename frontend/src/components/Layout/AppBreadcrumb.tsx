import { Breadcrumb } from 'antd';
import { Link, useLocation } from 'react-router-dom';
import { HomeOutlined } from '@ant-design/icons';

/**
 * Breadcrumb navigation component
 */
export const AppBreadcrumb: React.FC = () => {
  const location = useLocation();
  const pathSnippets = location.pathname.split('/').filter((i) => i);

  const breadcrumbNameMap: Record<string, string> = {
    '/': 'Home',
    '/issues': 'Issues',
    '/actions': 'Actions',
    '/ai-history': 'AI History',
    '/settings': 'Settings',
    '/metrics': 'Metrics',
    '/processes': 'Processes',
    '/logs': 'Logs',
    '/about': 'About',
  };

  const breadcrumbItems = [
    {
      title: (
        <Link to="/">
          <HomeOutlined />
        </Link>
      ),
    },
    ...pathSnippets.map((_, index) => {
      const url = `/${pathSnippets.slice(0, index + 1).join('/')}`;
      const title = breadcrumbNameMap[url] || pathSnippets[index];
      
      return {
        title: index === pathSnippets.length - 1 ? (
          title
        ) : (
          <Link to={url}>{title}</Link>
        ),
      };
    }),
  ];

  if (pathSnippets.length === 0) {
    return null;
  }

  return <Breadcrumb items={breadcrumbItems} style={{ marginBottom: 16 }} />;
};
