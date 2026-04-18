import { useState } from 'react';
import {
  Card,
  Form,
  Switch,
  Button,
  Select,
  Divider,
  Space,
  App,
  Tag,
  Popconfirm,
  Spin,
  Alert,
  InputNumber,
  Typography,
  AutoComplete,
} from 'antd';
import {
  SaveOutlined,
  ReloadOutlined,
  DeleteOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { settingsApi } from '../api/client';
import type { AgentSettings } from '../types';

const { Text } = Typography;

/**
 * Settings page for configuring AIOS behavior
 */
const SettingsPage: React.FC = () => {
  const [form] = Form.useForm();
  const [newProcessName, setNewProcessName] = useState('');
  const queryClient = useQueryClient();
  const { message } = App.useApp();

  // Fetch settings
  const {
    data: settings,
    isLoading,
    isError,
    error,
  } = useQuery({
    queryKey: ['settings'],
    queryFn: async () => {
      const response = await settingsApi.get();
      return response.data;
    },
  });

  // Fetch running processes
  const { data: runningProcesses } = useQuery({
    queryKey: ['running-processes'],
    queryFn: async () => {
      const response = await settingsApi.getRunningProcesses();
      return response.data;
    },
    staleTime: 30000, // Cache for 30 seconds
  });

  // Update settings mutation
  const updateMutation = useMutation({
    mutationFn: async (values: AgentSettings) => {
      const response = await settingsApi.update(values);
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings'] });
      message.success('Settings saved successfully');
    },
    onError: () => {
      message.error('Failed to save settings');
    },
  });

  // Reset settings mutation
  const resetMutation = useMutation({
    mutationFn: async () => {
      const response = await settingsApi.reset();
      return response.data;
    },
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['settings'] });
      form.setFieldsValue(data);
      message.success('Settings reset to defaults');
    },
    onError: () => {
      message.error('Failed to reset settings');
    },
  });

  // Add protected process mutation
  const addProcessMutation = useMutation({
    mutationFn: async (processName: string) => {
      const response = await settingsApi.addProtectedProcess(processName);
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings'] });
      setNewProcessName('');
      message.success('Process added to protected list');
    },
    onError: () => {
      message.error('Failed to add process');
    },
  });

  // Remove protected process mutation
  const removeProcessMutation = useMutation({
    mutationFn: async (processName: string) => {
      const response = await settingsApi.removeProtectedProcess(processName);
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings'] });
      message.success('Process removed from protected list');
    },
    onError: () => {
      message.error('Failed to remove process');
    },
  });

  const handleSave = (values: any) => {
    const updatedSettings: AgentSettings = {
      dryRunMode: values.dryRunMode,
      autoRemediation: values.autoRemediation,
      confidenceThreshold: values.confidenceThreshold,
      maxConcurrentActions: values.maxConcurrentActions,
      collectionIntervalSeconds: values.collectionIntervalSeconds,
      protectedProcesses: settings?.protectedProcesses || [],
      notifyOnCritical: values.notifyOnCritical,
      aiDiagnosisEnabled: values.aiDiagnosisEnabled,
    };
    updateMutation.mutate(updatedSettings);
  };

  const handleReset = () => {
    resetMutation.mutate();
  };

  const handleAddProcess = () => {
    if (newProcessName.trim()) {
      addProcessMutation.mutate(newProcessName.trim());
    }
  };

  const handleRemoveProcess = (processName: string) => {
    removeProcessMutation.mutate(processName);
  };

  if (isLoading) {
    return (
      <div style={{ textAlign: 'center', padding: '50px' }}>
        <Spin size="large" />
        <p>Loading settings...</p>
      </div>
    );
  }

  if (isError) {
    return (
      <Alert
        message="Error"
        description={`Failed to load settings: ${error instanceof Error ? error.message : 'Unknown error'}`}
        type="error"
        showIcon
      />
    );
  }

  return (
    <div>
      <Card title="Agent Configuration">
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSave}
          initialValues={{
            collectionIntervalSeconds: settings?.collectionIntervalSeconds ?? 10,
            dryRunMode: settings?.dryRunMode ?? true,
            autoRemediation: settings?.autoRemediation ?? false,
            confidenceThreshold: settings?.confidenceThreshold ?? 0.85,
            maxConcurrentActions: settings?.maxConcurrentActions ?? 3,
            notifyOnCritical: settings?.notifyOnCritical ?? true,
            aiDiagnosisEnabled: settings?.aiDiagnosisEnabled ?? true,
          }}
        >
          <Form.Item
            label="Collection Interval (seconds)"
            name="collectionIntervalSeconds"
            rules={[{ required: true, message: 'Please enter collection interval' }]}
          >
            <InputNumber min={5} max={300} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item
            label="Max Concurrent Actions"
            name="maxConcurrentActions"
            rules={[{ required: true, message: 'Please enter max concurrent actions' }]}
          >
            <InputNumber min={1} max={10} style={{ width: '100%' }} />
          </Form.Item>

          <Divider />

          <Form.Item
            label="Dry Run Mode"
            name="dryRunMode"
            valuePropName="checked"
            extra="When enabled, actions are simulated but not executed"
          >
            <Switch />
          </Form.Item>

          <Form.Item
            label="Auto Remediation"
            name="autoRemediation"
            valuePropName="checked"
            extra="Automatically execute remediation actions for high-confidence issues"
          >
            <Switch />
          </Form.Item>

          <Form.Item
            label="AI Diagnosis"
            name="aiDiagnosisEnabled"
            valuePropName="checked"
            extra="Enable AI-powered root cause analysis"
          >
            <Switch />
          </Form.Item>

          <Form.Item
            label="Notify on Critical"
            name="notifyOnCritical"
            valuePropName="checked"
            extra="Show notifications for critical issues"
          >
            <Switch />
          </Form.Item>

          <Form.Item
            label="Confidence Threshold"
            name="confidenceThreshold"
            extra="Minimum confidence level required for auto-remediation"
          >
            <Select>
              <Select.Option value={0.7}>70%</Select.Option>
              <Select.Option value={0.75}>75%</Select.Option>
              <Select.Option value={0.8}>80%</Select.Option>
              <Select.Option value={0.85}>85%</Select.Option>
              <Select.Option value={0.9}>90%</Select.Option>
              <Select.Option value={0.95}>95%</Select.Option>
            </Select>
          </Form.Item>

          <Divider />

          <Form.Item>
            <Space>
              <Button
                type="primary"
                htmlType="submit"
                icon={<SaveOutlined />}
                loading={updateMutation.isPending}
              >
                Save Settings
              </Button>
              <Popconfirm
                title="Reset to defaults?"
                description="All settings will be restored to their default values."
                onConfirm={handleReset}
                okText="Reset"
                cancelText="Cancel"
              >
                <Button icon={<ReloadOutlined />} loading={resetMutation.isPending}>
                  Reset to Defaults
                </Button>
              </Popconfirm>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card title="Protected Processes" style={{ marginTop: 24 }}>
        <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
          Protected processes cannot be terminated or modified by AIOS remediation actions.
        </Text>

        <Space.Compact style={{ width: '100%', marginBottom: 16 }}>
          <AutoComplete
            style={{ flex: 1 }}
            placeholder="Type or select a process name (e.g., explorer.exe)"
            value={newProcessName}
            onChange={(value) => setNewProcessName(value)}
            options={runningProcesses?.map((p) => ({
              value: p.name,
              label: (
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span>{p.name}</span>
                  <span style={{ color: '#888' }}>CPU: {p.cpu}% | Mem: {p.memory}MB</span>
                </div>
              ),
            })) || []}
            filterOption={(inputValue, option) =>
              option?.value.toLowerCase().includes(inputValue.toLowerCase()) ?? false
            }
          />
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={handleAddProcess}
            loading={addProcessMutation.isPending}
          >
            Add
          </Button>
        </Space.Compact>

        <div>
          {(settings?.protectedProcesses || []).length === 0 ? (
            <Text type="secondary">No protected processes</Text>
          ) : (
            (settings?.protectedProcesses || []).map((process) => (
              <div key={process} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderBottom: '1px solid #f0f0f0' }}>
                <Tag color="blue">{process}</Tag>
                <Popconfirm
                  title="Remove protection?"
                  description={`Allow AIOS to manage ${process}?`}
                  onConfirm={() => handleRemoveProcess(process)}
                  okText="Remove"
                  cancelText="Cancel"
                >
                  <Button
                    type="text"
                    danger
                    icon={<DeleteOutlined />}
                    size="small"
                    loading={removeProcessMutation.isPending}
                  />
                </Popconfirm>
              </div>
            ))
          )}
        </div>
      </Card>
    </div>
  );
};

export default SettingsPage;
