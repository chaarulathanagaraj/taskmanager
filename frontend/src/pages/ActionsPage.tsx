import { Table, Tag, Badge, Empty, Spin, Space, Card } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useActions } from '../hooks/useMetrics';
import { RemediationControls } from '../components';
import type { RemediationAction, ActionStatus } from '../types';

/**
 * Page displaying remediation action history
 */
const ActionsPage: React.FC = () => {
  const { data: actions, isLoading, error } = useActions(24);

  const columns: ColumnsType<RemediationAction> = [
    {
      title: 'Time',
      dataIndex: 'executedAt',
      key: 'time',
      width: 180,
      render: (val: string) => new Date(val).toLocaleString(),
      sorter: (a, b) => new Date(a.executedAt).getTime() - new Date(b.executedAt).getTime(),
      defaultSortOrder: 'descend',
    },
    {
      title: 'Action',
      dataIndex: 'actionType',
      key: 'action',
      width: 180,
      render: (val: string) => <Tag>{val.replace(/_/g, ' ')}</Tag>,
      filters: [
        { text: 'Kill Process', value: 'KILL_PROCESS' },
        { text: 'Reduce Priority', value: 'REDUCE_PRIORITY' },
        { text: 'Trim Working Set', value: 'TRIM_WORKING_SET' },
        { text: 'Suspend Process', value: 'SUSPEND_PROCESS' },
      ],
      onFilter: (value, record) => record.actionType === value,
    },
    {
      title: 'Target',
      dataIndex: 'targetName',
      key: 'target',
      ellipsis: true,
    },
    {
      title: 'PID',
      dataIndex: 'targetPid',
      key: 'pid',
      width: 80,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (val: ActionStatus) => (
        <Badge
          status={val === 'SUCCESS' ? 'success' : val === 'FAILED' ? 'error' : 'processing'}
          text={val}
        />
      ),
      filters: [
        { text: 'Success', value: 'SUCCESS' },
        { text: 'Failed', value: 'FAILED' },
        { text: 'Pending', value: 'PENDING' },
      ],
      onFilter: (value, record) => record.status === value,
    },
    {
      title: 'Safety Level',
      dataIndex: 'safetyLevel',
      key: 'safety',
      width: 120,
      render: (val: string) => (
        <Tag color={val === 'CRITICAL' ? 'red' : val === 'HIGH' ? 'orange' : 'green'}>
          {val}
        </Tag>
      ),
    },
    {
      title: 'Dry Run',
      dataIndex: 'dryRun',
      key: 'dryRun',
      width: 80,
      render: (val: boolean) => (val ? '✓' : '✗'),
      filters: [
        { text: 'Yes', value: true },
        { text: 'No', value: false },
      ],
      onFilter: (value, record) => record.dryRun === value,
    },
    {
      title: 'Result',
      dataIndex: 'result',
      key: 'result',
      ellipsis: true,
    },
  ];

  if (isLoading) {
    return (
      <div style={{ textAlign: 'center', padding: '100px' }}>
        <Spin size="large" tip="Loading actions..." />
      </div>
    );
  }

  if (error) {
    return <Empty description="Failed to load actions" />;
  }

  return (
    <div>
      <Card
        title="Remediation Actions"
        extra={<RemediationControls />}
        style={{ marginBottom: 16 }}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Table
            columns={columns}
            dataSource={actions || []}
            rowKey="id"
            pagination={{
              pageSize: 50,
              showSizeChanger: true,
              showTotal: (total) => `Total ${total} actions`,
            }}
          />
        </Space>
      </Card>
    </div>
  );
};

export default ActionsPage;
