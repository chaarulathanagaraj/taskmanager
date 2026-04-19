import { useState } from 'react';
import { Button, Modal, Select, Form, Switch, InputNumber, Space, Alert, Typography, Divider } from 'antd';
import { ThunderboltOutlined, ExclamationCircleOutlined } from '@ant-design/icons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'react-toastify';
import { actionsApi } from '../api/client';
import type { ActionType, SafetyLevel } from '../types';

const { Text } = Typography;

interface ExecuteActionRequest {
  actionType: ActionType;
  targetPid: number;
  dryRun: boolean;
  targetPriority?: 'BELOW_NORMAL' | 'IDLE';
}

interface RemediationControlsProps {
  /** Pre-fill with specific PID */
  defaultPid?: number;
  /** Pre-fill with specific process name */
  defaultProcessName?: string;
  /** Callback after action execution */
  onActionExecuted?: () => void;
}

/**
 * Action type information for display
 */
const actionInfo: Record<ActionType, { label: string; description: string; safetyLevel: SafetyLevel }> = {
  KILL_PROCESS: {
    label: 'Kill Process',
    description: 'Forcefully terminates the process immediately',
    safetyLevel: 'HIGH',
  },
  REDUCE_PRIORITY: {
    label: 'Reduce Priority',
    description: 'Lowers the process priority to BELOW_NORMAL',
    safetyLevel: 'LOW',
  },
  TRIM_WORKING_SET: {
    label: 'Trim Working Set',
    description: 'Releases unused memory from the process',
    safetyLevel: 'LOW',
  },
  SUSPEND_PROCESS: {
    label: 'Suspend Process',
    description: 'Pauses the process execution temporarily',
    safetyLevel: 'MEDIUM',
  },
  RESTART_PROCESS: {
    label: 'Restart Process',
    description: 'Kills and restarts the process',
    safetyLevel: 'HIGH',
  },
};

/**
 * Safety level colors
 */
const safetyColors: Record<SafetyLevel, string> = {
  LOW: 'green',
  MEDIUM: 'orange',
  HIGH: 'red',
  CRITICAL: 'purple',
};

/**
 * Manual remediation control component
 * Allows operators to execute remediation actions manually
 */
export const RemediationControls: React.FC<RemediationControlsProps> = ({
  defaultPid,
  defaultProcessName,
  onActionExecuted,
}) => {
  const [modalVisible, setModalVisible] = useState(false);
  const [confirmModalVisible, setConfirmModalVisible] = useState(false);
  const [pendingValues, setPendingValues] = useState<ExecuteActionRequest | null>(null);
  const [form] = Form.useForm<ExecuteActionRequest>();
  const queryClient = useQueryClient();

  // Execute action mutation
  const executeMutation = useMutation({
    mutationFn: async (values: ExecuteActionRequest) => {
      const response = await actionsApi.execute(values);
      if (response.data.success === false) {
        throw new Error(response.data.message || response.data.error || 'Execution failed');
      }
      return {
        success: true,
        message: values.dryRun
          ? `[DRY RUN] Would execute ${values.actionType} on PID ${values.targetPid}`
          : `Executed ${values.actionType} on PID ${values.targetPid}`,
        details: response.data.details,
      };
    },
    onSuccess: (data, variables) => {
      if (variables.dryRun) {
        toast.info(data.message);
      } else {
        const steps = Array.isArray(data.details?.steps) ? data.details.steps : [];
        const stepSummary = steps.length ? ` Steps: ${steps.join(' | ')}` : '';
        toast.info(`${data.message}. This confirms the action ran, not that the issue is resolved. Review the resolution card for verification steps.${stepSummary}`);
      }
      setModalVisible(false);
      setConfirmModalVisible(false);
      form.resetFields();
      // Invalidate queries to refresh data
      queryClient.invalidateQueries({ queryKey: ['actions'] });
      queryClient.invalidateQueries({ queryKey: ['issues'] });
      onActionExecuted?.();
    },
    onError: (error: Error) => {
      toast.error(`Failed to execute action: ${error.message}`);
    },
  });

  /**
   * Handle form submission - show confirmation for non-dry-run actions
   */
  const handleSubmit = (values: ExecuteActionRequest) => {
    if (values.dryRun) {
      // Execute dry run immediately without confirmation
      executeMutation.mutate(values);
    } else {
      // Show confirmation modal for real actions
      setPendingValues(values);
      setConfirmModalVisible(true);
    }
  };

  /**
   * Confirm and execute the action
   */
  const handleConfirmExecute = () => {
    if (pendingValues) {
      executeMutation.mutate(pendingValues);
    }
  };

  /**
   * Get selected action's safety level
   */
  const selectedAction = Form.useWatch('actionType', form);
  const selectedSafetyLevel = selectedAction ? actionInfo[selectedAction]?.safetyLevel : null;

  return (
    <>
      <Button 
        type="primary" 
        icon={<ThunderboltOutlined />}
        onClick={() => setModalVisible(true)}
      >
        Manual Remediation
      </Button>

      {/* Main Form Modal */}
      <Modal
        title="Execute Remediation Action"
        open={modalVisible}
        onCancel={() => {
          setModalVisible(false);
          form.resetFields();
        }}
        footer={null}
        width={500}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          initialValues={{
            actionType: 'REDUCE_PRIORITY',
            targetPid: defaultPid,
            dryRun: false,
            targetPriority: 'BELOW_NORMAL',
          }}
        >
          <Form.Item
            name="actionType"
            label="Action"
            rules={[{ required: true, message: 'Please select an action' }]}
          >
            <Select>
              {Object.entries(actionInfo).map(([key, info]) => (
                <Select.Option key={key} value={key}>
                  <Space>
                    <span>{info.label}</span>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      ({info.safetyLevel})
                    </Text>
                  </Space>
                </Select.Option>
              ))}
            </Select>
          </Form.Item>

          {selectedAction && (
            <Alert
              message={actionInfo[selectedAction].description}
              type={selectedSafetyLevel === 'HIGH' || selectedSafetyLevel === 'CRITICAL' ? 'warning' : 'info'}
              showIcon
              style={{ marginBottom: 16 }}
            />
          )}

          <Form.Item
            name="targetPid"
            label="Target PID"
            rules={[
              { required: true, message: 'Please enter a PID' },
              { type: 'number', min: 1, message: 'PID must be a positive number' },
            ]}
            extra={defaultProcessName ? `Process: ${defaultProcessName}` : undefined}
          >
            <InputNumber 
              style={{ width: '100%' }} 
              placeholder="Enter process ID"
              min={1}
            />
          </Form.Item>

          {selectedAction === 'REDUCE_PRIORITY' && (
            <Form.Item
              name="targetPriority"
              label="Target Priority"
              extra="Task Manager should show this priority after successful execution"
            >
              <Select>
                <Select.Option value="BELOW_NORMAL">Below Normal</Select.Option>
                <Select.Option value="IDLE">Low (Idle)</Select.Option>
              </Select>
            </Form.Item>
          )}

          <Divider />

          <Form.Item
            name="dryRun"
            label="Dry Run Mode"
            valuePropName="checked"
            extra="When enabled, simulates the action without actually executing it"
          >
            <Switch checkedChildren="ON" unCheckedChildren="OFF" />
          </Form.Item>

          {selectedSafetyLevel && (
            <div style={{ marginBottom: 16 }}>
              <Text>Safety Level: </Text>
              <Text strong style={{ color: safetyColors[selectedSafetyLevel] }}>
                {selectedSafetyLevel}
              </Text>
            </div>
          )}

          <Form.Item>
            <Space>
              <Button 
                type="primary" 
                htmlType="submit"
                loading={executeMutation.isPending}
                danger={selectedSafetyLevel === 'HIGH' || selectedSafetyLevel === 'CRITICAL'}
              >
                {Form.useWatch('dryRun', form) ? 'Test Action' : 'Execute Action'}
              </Button>
              <Button onClick={() => {
                setModalVisible(false);
                form.resetFields();
              }}>
                Cancel
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* Confirmation Modal */}
      <Modal
        title={
          <Space>
            <ExclamationCircleOutlined style={{ color: '#faad14' }} />
            <span>Confirm Action Execution</span>
          </Space>
        }
        open={confirmModalVisible}
        onOk={handleConfirmExecute}
        onCancel={() => {
          setConfirmModalVisible(false);
          setPendingValues(null);
        }}
        okText="Yes, Execute"
        okButtonProps={{ 
          danger: true,
          loading: executeMutation.isPending,
        }}
        cancelText="Cancel"
      >
        {pendingValues && (
          <div>
            <p>Are you sure you want to execute this action?</p>
            <Alert
              message="This action will be executed for real (not a dry run)"
              description={
                <div>
                  <p><strong>Action:</strong> {actionInfo[pendingValues.actionType].label}</p>
                  <p><strong>Target PID:</strong> {pendingValues.targetPid}</p>
                  <p><strong>Safety Level:</strong>{' '}
                    <Text style={{ color: safetyColors[actionInfo[pendingValues.actionType].safetyLevel] }}>
                      {actionInfo[pendingValues.actionType].safetyLevel}
                    </Text>
                  </p>
                </div>
              }
              type="warning"
              showIcon
            />
          </div>
        )}
      </Modal>
    </>
  );
};

export default RemediationControls;
