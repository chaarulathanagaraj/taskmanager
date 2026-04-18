import React from 'react';
import { Alert, Card, Space, Typography, Tag, Divider } from 'antd';
import { 
  BulbOutlined, 
  WarningOutlined, 
  CheckCircleOutlined,
  InfoCircleOutlined 
} from '@ant-design/icons';

const { Title, Text, Paragraph } = Typography;

interface SystemHealthSuggestionsProps {
  cpuUsage: number;
  memoryPercent: number;
  diskIO: number;
  networkIO: number;
  processCount: number;
  activeIssuesCount: number;
}

/**
 * AI-powered system health suggestions component
 * Provides actionable recommendations based on current system metrics
 */
export const SystemHealthSuggestions: React.FC<SystemHealthSuggestionsProps> = ({
  cpuUsage,
  memoryPercent,
  diskIO,
  networkIO,
  processCount,
  activeIssuesCount,
}) => {
  const suggestions: Array<{
    type: 'success' | 'info' | 'warning' | 'error';
    title: string;
    message: string;
    actions: string[];
  }> = [];

  // CPU Analysis
  if (cpuUsage > 90) {
    suggestions.push({
      type: 'error',
      title: '🔥 Critical CPU Usage',
      message: `CPU usage is at ${cpuUsage.toFixed(1)}%, which is critically high and may cause system instability.`,
      actions: [
        'Identify and terminate resource-heavy processes',
        'Check for runaway processes or infinite loops',
        'Consider upgrading CPU or adding more cores',
        'Review scheduled tasks running during peak hours',
        'Enable CPU throttling for non-critical applications'
      ]
    });
  } else if (cpuUsage > 70) {
    suggestions.push({
      type: 'warning',
      title: '⚠️ High CPU Usage',
      message: `CPU usage is at ${cpuUsage.toFixed(1)}%, which is higher than recommended.`,
      actions: [
        'Monitor top CPU-consuming processes',
        'Close unnecessary applications',
        'Check for background updates or scans',
        'Consider process priority adjustments'
      ]
    });
  } else if (cpuUsage < 10) {
    suggestions.push({
      type: 'success',
      title: '✅ Optimal CPU Performance',
      message: `CPU usage is healthy at ${cpuUsage.toFixed(1)}%. System is running efficiently.`,
      actions: []
    });
  }

  // Memory Analysis
  if (memoryPercent > 95) {
    suggestions.push({
      type: 'error',
      title: '🚨 Critical Memory Pressure',
      message: `Memory usage is at ${memoryPercent.toFixed(1)}%, system may start paging to disk.`,
      actions: [
        'Immediately close memory-intensive applications',
        'Check for memory leaks in running processes',
        'Clear system cache (Windows: ipconfig /flushdns)',
        'Restart services with high memory consumption',
        'Add more RAM if this is a recurring issue',
        'Enable virtual memory/page file settings'
      ]
    });
  } else if (memoryPercent > 80) {
    suggestions.push({
      type: 'warning',
      title: '⚠️ High Memory Usage',
      message: `Memory usage is at ${memoryPercent.toFixed(1)}%. Consider freeing up memory.`,
      actions: [
        'Close unused browser tabs and applications',
        'Review startup programs and disable unnecessary ones',
        'Check for memory-heavy processes in Task Manager',
        'Clear temporary files (%temp% folder)',
        'Run Windows Memory Diagnostic tool'
      ]
    });
  } else if (memoryPercent < 50) {
    suggestions.push({
      type: 'info',
      title: '💡 Memory Status Good',
      message: `Memory usage is comfortable at ${memoryPercent.toFixed(1)}%.`,
      actions: []
    });
  }

  // Disk I/O Analysis
  if (diskIO > 200) {
    suggestions.push({
      type: 'warning',
      title: '💾 High Disk Activity',
      message: `Disk I/O is at ${diskIO.toFixed(1)} MB/s, which may indicate heavy read/write operations.`,
      actions: [
        'Check for disk-intensive processes (database, file indexing)',
        'Consider upgrading to SSD for better performance',
        'Run disk defragmentation (HDD only)',
        'Check disk health with SMART tools',
        'Disable Windows Search indexing if not needed'
      ]
    });
  } else if (diskIO < 1 && processCount > 50) {
    suggestions.push({
      type: 'info',
      title: '📊 Low Disk Activity',
      message: 'Disk I/O is minimal, which is normal for idle systems.',
      actions: []
    });
  }

  // Network I/O Analysis
  if (networkIO > 100) {
    suggestions.push({
      type: 'info',
      title: '🌐 High Network Activity',
      message: `Network I/O is at ${networkIO.toFixed(1)} MB/s.`,
      actions: [
        'Check for large file transfers or downloads',
        'Monitor for unexpected network activity (potential malware)',
        'Review bandwidth usage per application',
        'Consider rate limiting for non-critical traffic'
      ]
    });
  }

  // Process Count Analysis
  if (processCount > 300) {
    suggestions.push({
      type: 'warning',
      title: '🔄 High Process Count',
      message: `System is running ${processCount} processes, which may impact performance.`,
      actions: [
        'Review and close unnecessary applications',
        'Check for duplicate processes or stuck applications',
        'Disable startup programs you don\'t need',
        'Run virus/malware scan to check for unwanted processes',
        'Use Task Manager to identify resource-heavy processes'
      ]
    });
  }

  // Active Issues Analysis
  if (activeIssuesCount > 0) {
    suggestions.push({
      type: 'error',
      title: '🔍 Active Issues Detected',
      message: `${activeIssuesCount} system issues require attention.`,
      actions: [
        'Navigate to Issues page to view details',
        'Enable auto-remediation if not already active',
        'Review issue patterns for recurring problems',
        'Check logs for detailed error information'
      ]
    });
  }

  // Overall Health Assessment
  const overallHealthy = cpuUsage < 70 && memoryPercent < 80 && activeIssuesCount === 0;
  if (overallHealthy && suggestions.length === 0) {
    suggestions.push({
      type: 'success',
      title: '🎉 System Health Excellent',
      message: 'All metrics are within optimal ranges. No action required.',
      actions: [
        'System is performing well',
        'Continue monitoring for any changes',
        'Consider enabling predictive alerts'
      ]
    });
  }

  return (
    <Card 
      title={
        <Space>
          <BulbOutlined style={{ fontSize: '20px', color: '#faad14' }} />
          <span>AI Health Recommendations</span>
          <Tag color="blue">Real-time Analysis</Tag>
        </Space>
      }
      style={{ marginTop: 16 }}
    >
      <Space direction="vertical" style={{ width: '100%' }} size="middle">
        {suggestions.length === 0 && (
          <Alert
            message="No recommendations at this time"
            description="System metrics are within normal ranges."
            type="success"
            showIcon
          />
        )}

        {suggestions.map((suggestion, index) => (
          <Alert
            key={index}
            message={suggestion.title}
            description={
              <Space direction="vertical" style={{ width: '100%' }}>
                <Paragraph style={{ marginBottom: 8 }}>
                  {suggestion.message}
                </Paragraph>
                {suggestion.actions.length > 0 && (
                  <>
                    <Text strong>Recommended Actions:</Text>
                    <ul style={{ marginTop: 8, marginBottom: 0, paddingLeft: 20 }}>
                      {suggestion.actions.map((action, i) => (
                        <li key={i}>{action}</li>
                      ))}
                    </ul>
                  </>
                )}
              </Space>
            }
            type={suggestion.type}
            showIcon
            icon={
              suggestion.type === 'success' ? <CheckCircleOutlined /> :
              suggestion.type === 'warning' ? <WarningOutlined /> :
              suggestion.type === 'error' ? <WarningOutlined /> :
              <InfoCircleOutlined />
            }
          />
        ))}
      </Space>

      <Divider />

      <Space direction="vertical" style={{ width: '100%' }}>
        <Text type="secondary" style={{ fontSize: '12px' }}>
          <InfoCircleOutlined /> These suggestions are generated based on real-time system metrics and best practices.
        </Text>
        <Text type="secondary" style={{ fontSize: '12px' }}>
          📊 Metrics analyzed: CPU ({cpuUsage.toFixed(1)}%), Memory ({memoryPercent.toFixed(1)}%), 
          Disk I/O ({diskIO.toFixed(1)} MB/s), Network ({networkIO.toFixed(1)} MB/s), 
          Processes ({processCount})
        </Text>
      </Space>
    </Card>
  );
};
