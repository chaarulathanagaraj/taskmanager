import { Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { ProcessInfo } from '../types';

interface ProcessTableProps {
  processes: ProcessInfo[];
  loading?: boolean;
}

/**
 * Table component displaying process information
 */
export const ProcessTable: React.FC<ProcessTableProps> = ({ processes, loading = false }) => {
  const columns: ColumnsType<ProcessInfo> = [
    {
      title: 'PID',
      dataIndex: 'pid',
      key: 'pid',
      width: 80,
      sorter: (a, b) => a.pid - b.pid,
    },
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      ellipsis: true,
    },
    {
      title: 'CPU %',
      dataIndex: 'cpuPercent',
      key: 'cpu',
      width: 100,
      render: (val: number) => `${val.toFixed(2)}%`,
      sorter: (a, b) => a.cpuPercent - b.cpuPercent,
      defaultSortOrder: 'descend',
    },
    {
      title: 'Memory',
      dataIndex: 'memoryBytes',
      key: 'memory',
      width: 120,
      render: (val: number) => `${(val / 1024 / 1024).toFixed(0)} MB`,
      sorter: (a, b) => a.memoryBytes - b.memoryBytes,
    },
    {
      title: 'Threads',
      dataIndex: 'threadCount',
      key: 'threads',
      width: 80,
      sorter: (a, b) => a.threadCount - b.threadCount,
    },
    {
      title: 'Handles',
      dataIndex: 'handleCount',
      key: 'handles',
      width: 100,
      render: (val: number) => val.toLocaleString(),
      sorter: (a, b) => a.handleCount - b.handleCount,
    },
  ];

  return (
    <Table
      columns={columns}
      dataSource={processes}
      rowKey="pid"
      loading={loading}
      pagination={{
        pageSize: 20,
        showSizeChanger: true,
        showTotal: (total) => `Total ${total} processes`,
      }}
      size="small"
    />
  );
};
