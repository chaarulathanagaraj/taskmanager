import { useState, useEffect } from 'react';
import {
  Card,
  Table,
  Select,
  Input,
  Space,
  Tag,
  Button,
  Tooltip,
  Typography,
  Drawer,
  Descriptions,
  Badge,
  Empty,
  Spin,
  Alert,
} from 'antd';
import {
  ReloadOutlined,
  SearchOutlined,
  ExpandOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { logsApi } from '../api/client';
import type { LogEntry } from '../types';
import type { ColumnsType } from 'antd/es/table';

const { Text, Paragraph } = Typography;

/**
 * Get color for log level tag
 */
const getLevelColor = (level: string): string => {
  switch (level.toUpperCase()) {
    case 'ERROR':
      return 'red';
    case 'WARN':
      return 'orange';
    case 'INFO':
      return 'blue';
    case 'DEBUG':
      return 'green';
    case 'TRACE':
      return 'gray';
    default:
      return 'default';
  }
};

/**
 * Format timestamp for display
 */
const formatTimestamp = (timestamp: string): string => {
  const date = new Date(timestamp);
  return date.toLocaleString('en-US', {
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  });
};

/**
 * Logs page for viewing application logs
 */
const LogsPage: React.FC = () => {
  const [level, setLevel] = useState<string>('ALL');
  const [search, setSearch] = useState<string>('');
  const [debouncedSearch, setDebouncedSearch] = useState<string>('');
  const [limit, setLimit] = useState<number>(100);
  const [selectedLog, setSelectedLog] = useState<LogEntry | null>(null);
  const [drawerOpen, setDrawerOpen] = useState<boolean>(false);

  // Debounce search input
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(search);
    }, 300);
    return () => clearTimeout(timer);
  }, [search]);

  // Fetch logs
  const {
    data: logs,
    isLoading,
    isError,
    error,
    refetch,
    isFetching,
  } = useQuery({
    queryKey: ['logs', level, debouncedSearch, limit],
    queryFn: async () => {
      const response = await logsApi.getLogs(level, debouncedSearch, limit);
      return response.data;
    },
    refetchInterval: 10000, // Auto-refresh every 10 seconds
  });

  const handleRowClick = (record: LogEntry) => {
    setSelectedLog(record);
    setDrawerOpen(true);
  };

  const columns: ColumnsType<LogEntry> = [
    {
      title: 'Time',
      dataIndex: 'timestamp',
      key: 'timestamp',
      width: 140,
      render: (val: string) => (
        <Text style={{ fontSize: 12, fontFamily: 'monospace' }}>
          {formatTimestamp(val)}
        </Text>
      ),
    },
    {
      title: 'Level',
      dataIndex: 'level',
      key: 'level',
      width: 80,
      render: (val: string) => (
        <Tag color={getLevelColor(val)} style={{ margin: 0 }}>
          {val}
        </Tag>
      ),
    },
    {
      title: 'Component',
      dataIndex: 'loggerShort',
      key: 'loggerShort',
      width: 180,
      ellipsis: true,
      render: (val: string, record: LogEntry) => (
        <Tooltip title={record.logger}>
          <Text code style={{ fontSize: 12 }}>
            {val}
          </Text>
        </Tooltip>
      ),
    },
    {
      title: 'Thread',
      dataIndex: 'thread',
      key: 'thread',
      width: 120,
      ellipsis: true,
      render: (val: string) => (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {val}
        </Text>
      ),
    },
    {
      title: 'Message',
      dataIndex: 'message',
      key: 'message',
      ellipsis: true,
      render: (val: string, record: LogEntry) => (
        <Space>
          <Text style={{ fontSize: 13 }}>{val}</Text>
          {record.stackTrace && (
            <Tooltip title="Has stack trace">
              <Badge status="error" />
            </Tooltip>
          )}
        </Space>
      ),
    },
    {
      title: '',
      key: 'actions',
      width: 40,
      render: (_, record: LogEntry) => (
        <Tooltip title="View details">
          <Button
            type="text"
            size="small"
            icon={<ExpandOutlined />}
            onClick={() => handleRowClick(record)}
          />
        </Tooltip>
      ),
    },
  ];

  const getRowClassName = (record: LogEntry): string => {
    switch (record.level.toUpperCase()) {
      case 'ERROR':
        return 'log-row-error';
      case 'WARN':
        return 'log-row-warn';
      default:
        return '';
    }
  };

  if (isError) {
    return (
      <Alert
        title="Error Loading Logs"
        description={error instanceof Error ? error.message : 'Failed to fetch logs'}
        type="error"
        showIcon
      />
    );
  }

  return (
    <div>
      <Card
        title={
          <Space>
            <span>Application Logs</span>
            <Badge
              count={logs?.length || 0}
              style={{ backgroundColor: '#52c41a' }}
              showZero
            />
          </Space>
        }
        extra={
          <Button
            icon={<ReloadOutlined spin={isFetching} />}
            onClick={() => refetch()}
          >
            Refresh
          </Button>
        }
      >
        <Space style={{ marginBottom: 16, width: '100%' }} wrap>
          <Select
            value={level}
            onChange={setLevel}
            style={{ width: 130 }}
            options={[
              { value: 'ALL', label: 'All Levels' },
              { value: 'ERROR', label: <Tag color="red">ERROR</Tag> },
              { value: 'WARN', label: <Tag color="orange">WARN</Tag> },
              { value: 'INFO', label: <Tag color="blue">INFO</Tag> },
              { value: 'DEBUG', label: <Tag color="green">DEBUG</Tag> },
              { value: 'TRACE', label: <Tag color="gray">TRACE</Tag> },
            ]}
          />

          <Input
            placeholder="Search logs..."
            prefix={<SearchOutlined />}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            style={{ width: 300 }}
            allowClear
          />

          <Select
            value={limit}
            onChange={setLimit}
            style={{ width: 120 }}
            options={[
              { value: 50, label: 'Last 50' },
              { value: 100, label: 'Last 100' },
              { value: 250, label: 'Last 250' },
              { value: 500, label: 'Last 500' },
            ]}
          />

          <Tooltip title="Logs auto-refresh every 10 seconds">
            <InfoCircleOutlined style={{ color: '#999' }} />
          </Tooltip>
        </Space>

        {isLoading ? (
          <div style={{ textAlign: 'center', padding: 50 }}>
            <Spin size="large" />
          </div>
        ) : logs && logs.length > 0 ? (
          <Table
            columns={columns}
            dataSource={logs}
            rowKey={(record, index) => `${record.timestamp}-${index}`}
            rowClassName={getRowClassName}
            size="small"
            pagination={{
              pageSize: 25,
              showSizeChanger: true,
              pageSizeOptions: ['10', '25', '50', '100'],
              showTotal: (total, range) =>
                `${range[0]}-${range[1]} of ${total} entries`,
            }}
            onRow={(record) => ({
              onClick: () => handleRowClick(record),
              style: { cursor: 'pointer' },
            })}
            scroll={{ x: 900 }}
          />
        ) : (
          <Empty description="No log entries found" />
        )}
      </Card>

      {/* Log Detail Drawer */}
      <Drawer
        title="Log Entry Details"
        placement="right"
        size={600}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
      >
        {selectedLog && (
          <Space direction="vertical" style={{ width: '100%' }} size="large">
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="Timestamp">
                {new Date(selectedLog.timestamp).toLocaleString()}
              </Descriptions.Item>
              <Descriptions.Item label="Level">
                <Tag color={getLevelColor(selectedLog.level)}>
                  {selectedLog.level}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Logger">
                <Text code copyable>
                  {selectedLog.logger}
                </Text>
              </Descriptions.Item>
              <Descriptions.Item label="Thread">
                {selectedLog.thread}
              </Descriptions.Item>
              <Descriptions.Item label="Source">
                <Tag>{selectedLog.source}</Tag>
              </Descriptions.Item>
            </Descriptions>

            <Card title="Message" size="small">
              <Paragraph copyable style={{ marginBottom: 0 }}>
                {selectedLog.message}
              </Paragraph>
            </Card>

            {selectedLog.stackTrace && (
              <Card
                title={
                  <Text type="danger">
                    Stack Trace
                  </Text>
                }
                size="small"
              >
                <pre
                  style={{
                    fontSize: 11,
                    padding: 12,
                    backgroundColor: '#f5f5f5',
                    borderRadius: 4,
                    overflow: 'auto',
                    maxHeight: 400,
                    margin: 0,
                  }}
                >
                  {selectedLog.stackTrace}
                </pre>
              </Card>
            )}
          </Space>
        )}
      </Drawer>

      {/* Custom styles for log row highlighting */}
      <style>{`
        .log-row-error {
          background-color: #fff1f0 !important;
        }
        .log-row-error:hover td {
          background-color: #ffccc7 !important;
        }
        .log-row-warn {
          background-color: #fff7e6 !important;
        }
        .log-row-warn:hover td {
          background-color: #ffe7ba !important;
        }
      `}</style>
    </div>
  );
};

export default LogsPage;
