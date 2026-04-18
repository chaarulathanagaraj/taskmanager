import { Space, Typography, Divider } from 'antd';
import { GithubOutlined, HeartOutlined } from '@ant-design/icons';

const { Text, Link } = Typography;

/**
 * Application footer
 */
export const AppFooter: React.FC = () => {
  const currentYear = new Date().getFullYear();

  return (
    <Space separator={<Divider orientation="vertical" />} style={{ width: '100%', justifyContent: 'center' }}>
      <Text>
        AIOS - AI Operating System ©{currentYear}
      </Text>
      
      <Link href="https://github.com" target="_blank">
        <Space>
          <GithubOutlined />
          GitHub
        </Space>
      </Link>
      
      <Text type="secondary">
        Made with <HeartOutlined style={{ color: '#ff4d4f' }} /> by AIOS Team
      </Text>
      
      <Text type="secondary">
        Version 1.0.0
      </Text>
    </Space>
  );
};
