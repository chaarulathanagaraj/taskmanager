import React, { useMemo, useState } from 'react';
import { Card, Descriptions, Progress, Alert, Tag, List, Collapse, Typography, Checkbox, Button, Space } from 'antd';
import { BulbOutlined, CheckCircleOutlined, WarningOutlined, ExperimentOutlined } from '@ant-design/icons';
import type { AiAnalysisResult, IssueType } from '../types';

const { Text, Paragraph } = Typography;
const { Panel } = Collapse;

interface AIAnalysisCardProps {
  analysis: AiAnalysisResult;
  loading?: boolean;
  issueType?: IssueType;
}

/**
 * Displays AI analysis results from LangChain4j agents.
 * Shows root cause, confidence, recommendations, and reasoning.
 */
export const AIAnalysisCard: React.FC<AIAnalysisCardProps> = ({
  analysis,
  loading = false,
  issueType,
}) => {
  const [completedChecks, setCompletedChecks] = useState<string[]>([]);
  const [checklistSubmitted, setChecklistSubmitted] = useState(false);

  const isMemoryLeakFlow = issueType === 'MEMORY_LEAK';

  const quickChecks = [
    'Close unnecessary tabs or windows in the affected app',
    'Stop non-essential background extensions/plugins for that app',
    'Wait 2-3 minutes and verify whether memory keeps increasing',
  ];

  const normalizedAlternativeCauses = useMemo(() => {
    const source = Array.isArray(analysis.alternativeCauses) ? analysis.alternativeCauses : [];
    return source
      .map((item) => item.trim())
      .filter((item) => item.length > 0)
      .filter((item, index, list) => list.findIndex((v) => v.toLowerCase() === item.toLowerCase()) === index);
  }, [analysis.alternativeCauses]);

  const postChecklistCauses = useMemo(() => {
    if (!isMemoryLeakFlow) {
      return normalizedAlternativeCauses;
    }

    const genericPatterns = [
      /task\s*manager/i,
      /diagnostic\s*tool/i,
      /close\s+.*tabs?/i,
      /monitor/i,
      /working\s*set/i,
    ];

    const filtered = normalizedAlternativeCauses.filter(
      (cause) => !genericPatterns.some((pattern) => pattern.test(cause))
    );

    if (filtered.length > 0) {
      return filtered;
    }

    return [
      'Object retention caused by event listeners or callbacks that are never released',
      'Cache growth without eviction policy (in-memory maps, LRU misconfiguration)',
      'Large response payload buffering or stream objects not being disposed',
      'Frequent re-renders creating detached DOM nodes or stale closures',
      'Third-party extension/plugin memory regression in the current app version',
    ];
  }, [isMemoryLeakFlow, normalizedAlternativeCauses]);

  const canSubmitChecks = completedChecks.length === quickChecks.length;
  const getConfidenceStatus = (confidence: number) => {
    if (confidence >= 0.8) return 'success';
    if (confidence >= 0.6) return 'normal';
    if (confidence >= 0.4) return 'active';
    return 'exception';
  };

  const getConfidenceColor = (confidence: number) => {
    if (confidence >= 0.8) return 'green';
    if (confidence >= 0.6) return 'blue';
    if (confidence >= 0.4) return 'orange';
    return 'red';
  };

  const getRiskColor = (risk: string | undefined) => {
    if (!risk) return 'default';
    const riskLower = risk.toLowerCase();
    if (riskLower.includes('high') || riskLower.includes('critical')) return 'red';
    if (riskLower.includes('medium') || riskLower.includes('moderate')) return 'orange';
    return 'green';
  };

  return (
    <Card
      title={
        <span>
          <BulbOutlined style={{ marginRight: 8, color: '#1890ff' }} />
          AI Analysis
        </span>
      }
      extra={
        <Tag color="blue" icon={<ExperimentOutlined />}>
          {analysis.agentName || 'GPT-4'}
        </Tag>
      }
      loading={loading}
      style={{ marginTop: 16 }}
    >
      {/* Root Cause Alert */}
      <Alert
        message="Root Cause Analysis"
        description={analysis.rootCause}
        type="info"
        showIcon
        icon={<BulbOutlined />}
        style={{ marginBottom: 16 }}
      />

      {/* Key Metrics */}
      <Descriptions column={1} bordered size="small" style={{ marginBottom: 16 }}>
        <Descriptions.Item label="Confidence">
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <Progress
              percent={Math.round(analysis.confidence * 100)}
              status={getConfidenceStatus(analysis.confidence)}
              style={{ width: 150, marginBottom: 0 }}
            />
            <Tag color={getConfidenceColor(analysis.confidence)}>
              {analysis.confidence >= 0.8 ? 'High' : analysis.confidence >= 0.6 ? 'Medium' : 'Low'}
            </Tag>
          </div>
        </Descriptions.Item>

        <Descriptions.Item label="Recommended Action">
          <Tag color="purple" style={{ fontSize: 13 }}>
            {analysis.recommendedAction?.replace(/_/g, ' ') || 'MONITOR'}
          </Tag>
        </Descriptions.Item>

        {analysis.riskAssessment && (
          <Descriptions.Item label="Risk Assessment">
            <Tag color={getRiskColor(analysis.riskAssessment)}>
              {analysis.riskAssessment}
            </Tag>
          </Descriptions.Item>
        )}

        <Descriptions.Item label="Agent">
          <Text code>{analysis.agentName}</Text>
        </Descriptions.Item>
      </Descriptions>

      {/* Detailed Reasoning */}
      <Collapse ghost defaultActiveKey={['reasoning']}>
        <Panel
          header={
            <span>
              <CheckCircleOutlined style={{ marginRight: 8 }} />
              Reasoning
            </span>
          }
          key="reasoning"
        >
          <Paragraph style={{ whiteSpace: 'pre-wrap' }}>
            {analysis.reasoning}
          </Paragraph>
        </Panel>

        {/* Evidence */}
        {analysis.evidence && analysis.evidence.length > 0 && (
          <Panel
            header={
              <span>
                <CheckCircleOutlined style={{ marginRight: 8 }} />
                Evidence ({analysis.evidence.length} items)
              </span>
            }
            key="evidence"
          >
            <List
              size="small"
              dataSource={analysis.evidence}
              renderItem={(item) => (
                <List.Item>
                  <Text>
                    <CheckCircleOutlined style={{ color: '#52c41a', marginRight: 8 }} />
                    {item}
                  </Text>
                </List.Item>
              )}
            />
          </Panel>
        )}

        {/* Memory Leak Checklist Flow */}
        {isMemoryLeakFlow && (
          <Panel
            header={
              <span>
                <CheckCircleOutlined style={{ marginRight: 8 }} />
                Initial Checks Before Deep Dive
              </span>
            }
            key="memory-checklist"
          >
            {!checklistSubmitted ? (
              <>
                <Alert
                  type="info"
                  showIcon
                  style={{ marginBottom: 12 }}
                  message="Complete these quick checks first"
                  description="This prevents repeating the same basic recommendation. Once submitted, AIOS will focus on deeper possible causes."
                />

                <Checkbox.Group
                  value={completedChecks}
                  onChange={(values) => setCompletedChecks(values as string[])}
                  style={{ width: '100%' }}
                >
                  <Space direction="vertical" style={{ width: '100%' }}>
                    {quickChecks.map((check) => (
                      <Checkbox key={check} value={check}>
                        {check}
                      </Checkbox>
                    ))}
                  </Space>
                </Checkbox.Group>

                <Button
                  type="primary"
                  style={{ marginTop: 12 }}
                  onClick={() => setChecklistSubmitted(true)}
                  disabled={!canSubmitChecks}
                >
                  Submit Checks
                </Button>
              </>
            ) : (
              <Alert
                type="success"
                showIcon
                message="Checks submitted"
                description="Showing deeper possible causes below."
              />
            )}
          </Panel>
        )}

        {/* Alternative Causes */}
        {((!isMemoryLeakFlow && normalizedAlternativeCauses.length > 0)
          || (isMemoryLeakFlow && checklistSubmitted && postChecklistCauses.length > 0)) && (
          <Panel
            header={
              <span>
                <WarningOutlined style={{ marginRight: 8 }} />
                Alternative Causes ({isMemoryLeakFlow ? postChecklistCauses.length : normalizedAlternativeCauses.length})
              </span>
            }
            key="alternatives"
          >
            <List
              size="small"
              dataSource={isMemoryLeakFlow ? postChecklistCauses : normalizedAlternativeCauses}
              renderItem={(item) => (
                <List.Item>
                  <Text type="secondary">
                    <WarningOutlined style={{ color: '#faad14', marginRight: 8 }} />
                    {item}
                  </Text>
                </List.Item>
              )}
            />
          </Panel>
        )}
      </Collapse>
    </Card>
  );
};

export default AIAnalysisCard;
