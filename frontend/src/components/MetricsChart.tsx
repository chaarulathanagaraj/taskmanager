import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import { useMetrics } from '../hooks/useMetrics';
import { Spin, Empty } from 'antd';

/**
 * Line chart component displaying CPU and Memory metrics over time
 */
export const MetricsChart: React.FC = () => {
  const { data: metrics, isLoading, error } = useMetrics(10);

  if (isLoading) {
    return (
      <div style={{ textAlign: 'center', padding: '50px' }}>
        <Spin size="large" />
      </div>
    );
  }

  if (error) {
    return (
      <Empty
        description="Failed to load metrics"
        style={{ padding: '50px' }}
      />
    );
  }

  if (!metrics || metrics.length === 0) {
    return (
      <Empty
        description="No metrics available"
        style={{ padding: '50px' }}
      />
    );
  }

  // Transform data for chart
  const chartData = metrics.map((m) => ({
    time: new Date(m.timestamp).toLocaleTimeString(),
    cpu: Number(m.cpuUsage.toFixed(2)),
    memory: Number(((m.memoryUsed / m.memoryTotal) * 100).toFixed(2)),
  }));

  return (
    <ResponsiveContainer width="100%" height={300}>
      <LineChart data={chartData}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="time" />
        <YAxis domain={[0, 100]} />
        <Tooltip />
        <Legend />
        <Line
          type="monotone"
          dataKey="cpu"
          stroke="#8884d8"
          name="CPU %"
          strokeWidth={2}
        />
        <Line
          type="monotone"
          dataKey="memory"
          stroke="#82ca9d"
          name="Memory %"
          strokeWidth={2}
        />
      </LineChart>
    </ResponsiveContainer>
  );
};
