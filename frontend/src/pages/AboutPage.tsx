import { Card, Descriptions, Space, Tag, Typography, Divider } from 'antd';
import { InfoCircleOutlined, ApiOutlined, DatabaseOutlined } from '@ant-design/icons';
import { useSystemHealth } from '../hooks/useMetrics';

const { Title, Paragraph, Text } = Typography;

/**
 * About page with system information
 */
const AboutPage: React.FC = () => {
  const { data: health } = useSystemHealth();

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card>
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Space>
            <InfoCircleOutlined style={{ fontSize: 32, color: '#1890ff' }} />
            <Title level={2} style={{ margin: 0 }}>
              AIOS Monitor
            </Title>
          </Space>
          
          <Paragraph>
            <Text strong>AI Operating System Monitor</Text> - An intelligent system monitoring and
            remediation platform that automatically detects and resolves performance issues.
          </Paragraph>

          <Paragraph>
            AIOS combines real-time system monitoring with AI-powered diagnostics to identify
            memory leaks, thread explosions, hung processes, I/O bottlenecks, and resource hogs.
            The system can automatically apply remediation actions to restore optimal performance.
          </Paragraph>
        </Space>
      </Card>

      <Card title="System Information">
        <Descriptions bordered column={2}>
          <Descriptions.Item label="Version">1.0.0</Descriptions.Item>
          <Descriptions.Item label="Build">2024.03.02</Descriptions.Item>
          <Descriptions.Item label="Backend Status">
            <Tag color={health === 'HEALTHY' || health === 'UP' ? 'success' : 'error'}>
              {health || 'Unknown'}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="Environment">Production</Descriptions.Item>
          <Descriptions.Item label="Backend URL">
            <Text code>http://localhost:8080</Text>
          </Descriptions.Item>
          <Descriptions.Item label="WebSocket">
            <Tag color="processing">Connected</Tag>
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title={<Space><ApiOutlined />Technology Stack</Space>}>
        <Divider>Frontend</Divider>
        <Space wrap>
          <Tag color="blue">React 19</Tag>
          <Tag color="blue">TypeScript</Tag>
          <Tag color="blue">Ant Design</Tag>
          <Tag color="blue">React Query</Tag>
          <Tag color="blue">React Router</Tag>
          <Tag color="blue">Recharts</Tag>
          <Tag color="blue">Vite</Tag>
        </Space>

        <Divider>Backend</Divider>
        <Space wrap>
          <Tag color="green">Spring Boot 3</Tag>
          <Tag color="green">Java 21</Tag>
          <Tag color="green">WebFlux</Tag>
          <Tag color="green">WebSocket</Tag>
          <Tag color="green">JPA</Tag>
          <Tag color="green">H2/PostgreSQL</Tag>
        </Space>

        <Divider>Agent</Divider>
        <Space wrap>
          <Tag color="orange">Spring Boot</Tag>
          <Tag color="orange">OSHI</Tag>
          <Tag color="orange">JNA</Tag>
          <Tag color="orange">Resilience4j</Tag>
        </Space>
      </Card>

      <Card title={<Space><DatabaseOutlined />Features</Space>}>
        <Space direction="vertical">
          <Text>✅ Real-time system metrics monitoring (CPU, Memory, Disk, Network)</Text>
          <Text>✅ Process-level resource tracking</Text>
          <Text>✅ AI-powered issue detection (5 detector types)</Text>
          <Text>✅ Automatic remediation actions</Text>
          <Text>✅ WebSocket live updates</Text>
          <Text>✅ Historical data analysis</Text>
          <Text>✅ Dry-run mode for testing</Text>
          <Text>✅ Protected process safeguards</Text>
          <Text>✅ Configurable confidence thresholds</Text>
          <Text>✅ Action audit trail</Text>
        </Space>
      </Card>

      <Card>
        <Paragraph type="secondary">
          © {new Date().getFullYear()} AIOS Team. All rights reserved.
        </Paragraph>
      </Card>
    </Space>
  );
};

export default AboutPage;
