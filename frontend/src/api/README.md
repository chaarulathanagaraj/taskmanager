# AIOS Frontend API Clients

Complete API integration for the AIOS monitoring system, including REST API and WebSocket support for real-time updates.

## 📁 Structure

```
frontend/src/api/
├── client.ts       # REST API client (Axios)
├── websocket.ts    # WebSocket client (STOMP + SockJS)
├── index.ts        # Module exports
└── README.md       # This file

frontend/src/config/
└── env.ts          # Environment configuration

frontend/src/hooks/
├── useMetrics.ts   # React Query hooks for REST API
└── useWebSocket.ts # React hooks for WebSocket
```

## 🚀 REST API Client

### Configuration

The REST API client is built with Axios and configured via environment variables:

```typescript
// .env file
VITE_API_BASE_URL=http://localhost:8080
```

### Usage

```typescript
import { metricsApi, issuesApi, actionsApi, dashboardApi } from "@/api";

// Fetch recent metrics
const response = await metricsApi.getRecent(10); // last 10 minutes
const metrics = response.data;

// Get active issues
const issues = await issuesApi.getActive();

// Get action history
const actions = await actionsApi.getHistory(24); // last 24 hours

// Get dashboard data
const dashboard = await dashboardApi.getData();
```

### React Query Hooks

For React components, use the provided hooks that include caching and auto-refresh:

```typescript
import { useMetrics, useIssues, useActions } from '@/hooks/useMetrics';

function MyComponent() {
  const { data: metrics, isLoading, error } = useMetrics(10);
  const { data: issues } = useIssues();
  const { data: actions } = useActions(24);

  return <div>...</div>;
}
```

**Auto-refresh intervals:**

- Metrics: Every 10 seconds
- Issues: Every 10 seconds
- Actions: Every 10 seconds
- Health: Every 30 seconds

## 🔌 WebSocket Client

### Configuration

WebSocket client connects to the backend STOMP endpoint:

```typescript
// .env file
VITE_WS_BASE_URL=http://localhost:8080
```

### Direct Usage

```typescript
import { wsClient } from "@/api";

// Connect
wsClient.connect(
  () => console.log("Connected!"),
  (error) => console.error("Connection error:", error),
);

// Subscribe to metrics
const unsubscribe = wsClient.subscribeToMetrics((metric) => {
  console.log("New metric:", metric);
});

// Unsubscribe when done
unsubscribe();

// Disconnect
wsClient.disconnect();
```

### React Hooks

For React components, use the streaming hooks:

```typescript
import { useMetricsStream, useIssuesStream, useActionsStream } from '@/hooks/useWebSocket';
import { useState } from 'react';

function RealtimeMetrics() {
  const [latestMetric, setLatestMetric] = useState(null);

  // Automatically connects and subscribes
  useMetricsStream((metric) => {
    setLatestMetric(metric);
  });

  return <div>CPU: {latestMetric?.cpuUsage}%</div>;
}
```

## 📊 API Endpoints

### Metrics API (`/api/metrics`)

| Method | Endpoint                           | Description                     |
| ------ | ---------------------------------- | ------------------------------- |
| GET    | `/recent?minutes=10`               | Get metrics from last N minutes |
| GET    | `/latest?limit=100`                | Get latest N metric snapshots   |
| GET    | `/range?startTime=...&endTime=...` | Get metrics in time range       |
| GET    | `/stats`                           | Get metric statistics           |

### Issues API (`/api/issues`)

| Method | Endpoint                     | Description                           |
| ------ | ---------------------------- | ------------------------------------- |
| GET    | `/`                          | Get all issues                        |
| GET    | `/active`                    | Get active (unresolved) issues        |
| GET    | `/high-priority`             | Get high-priority issues              |
| GET    | `/{id}`                      | Get issue by ID                       |
| GET    | `/type/{type}`               | Get issues by type                    |
| GET    | `/severity/{severity}`       | Get issues by severity                |
| GET    | `/process/{pid}`             | Get issues for process                |
| GET    | `/eligible-for-remediation`  | Get issues ready for auto-remediation |
| PUT    | `/{id}/resolve`              | Mark issue as resolved                |
| GET    | `/count/active`              | Count active issues                   |
| GET    | `/count/severity/{severity}` | Count by severity                     |

### Actions API (`/api/actions`)

| Method | Endpoint                        | Description                    |
| ------ | ------------------------------- | ------------------------------ |
| GET    | `/`                             | Get all actions                |
| GET    | `/history?hours=24`             | Get action history             |
| GET    | `/{id}`                         | Get action by ID               |
| GET    | `/type/{type}`                  | Get actions by type            |
| GET    | `/status/{status}`              | Get actions by status          |
| GET    | `/process/{pid}`                | Get actions for process        |
| GET    | `/issue/{issueId}`              | Get actions for issue          |
| GET    | `/real`                         | Get real (non-dry-run) actions |
| GET    | `/review-required`              | Get actions needing review     |
| GET    | `/success-rate/{type}?hours=24` | Get success rate               |
| GET    | `/stats?hours=24`               | Get action statistics          |

### Dashboard API (`/api/dashboard`)

| Method | Endpoint           | Description                 |
| ------ | ------------------ | --------------------------- |
| GET    | `/`                | Get complete dashboard data |
| GET    | `/overview`        | Get system overview         |
| GET    | `/health`          | Get system health status    |
| GET    | `/trends?hours=24` | Get metric trends           |

## 📡 WebSocket Topics

| Topic            | Payload             | Description                  |
| ---------------- | ------------------- | ---------------------------- |
| `/topic/metrics` | `MetricSnapshot`    | Real-time system metrics     |
| `/topic/issues`  | `DiagnosticIssue`   | New diagnostic issues        |
| `/topic/actions` | `RemediationAction` | Remediation actions executed |

## 🔧 Environment Variables

Create a `.env` file in the frontend root:

```env
# Backend API URL
VITE_API_BASE_URL=http://localhost:8080

# WebSocket URL (usually same as API)
VITE_WS_BASE_URL=http://localhost:8080
```

## 🛠️ Configuration

All configuration is centralized in `src/config/env.ts`:

```typescript
import { API_BASE_URL, WS_BASE_URL, POLLING_INTERVALS } from "@/config/env";

// Customize polling intervals
POLLING_INTERVALS.METRICS = 5000; // 5 seconds
POLLING_INTERVALS.ISSUES = 15000; // 15 seconds
```

## 🔍 Error Handling

The API client includes automatic error handling with interceptors:

```typescript
// Request logging
apiClient.interceptors.request.use((config) => {
  console.log(`[API] ${config.method?.toUpperCase()} ${config.url}`);
  return config;
});

// Response error handling
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    console.error("[API Error]", error.message);
    if (error.response) {
      console.error("Status:", error.response.status);
      console.error("Data:", error.response.data);
    }
    return Promise.reject(error);
  },
);
```

## 🔄 Connection Management

WebSocket includes automatic reconnection:

```typescript
// Reconnect settings
WS_RECONNECT.MAX_ATTEMPTS = 5; // Max reconnection attempts
WS_RECONNECT.DELAY = 3000; // 3 seconds between attempts
```

## 📝 TypeScript Types

All API responses are fully typed. Import types from `@/types`:

```typescript
import type {
  MetricSnapshot,
  DiagnosticIssue,
  RemediationAction,
  DashboardData,
  IssueType,
  Severity,
  ActionType,
  ActionStatus,
} from "@/types";
```

## 🧪 Testing Connection

```typescript
// Test REST API
import { dashboardApi } from "@/api";

dashboardApi
  .getHealth()
  .then((res) => console.log("Backend health:", res.data))
  .catch((err) => console.error("Backend unavailable:", err));

// Test WebSocket
import { wsClient } from "@/api";

wsClient.connect(
  () => console.log("✅ WebSocket connected"),
  (err) => console.error("❌ WebSocket failed:", err),
);
```

## 🚦 Best Practices

1. **Use hooks in components**: Prefer `useMetrics()` over direct `metricsApi.getRecent()`
2. **Centralize queries**: Define React Query hooks in `hooks/useMetrics.ts`
3. **Handle loading states**: Always check `isLoading` and `error` from hooks
4. **Clean up subscriptions**: WebSocket hooks auto-unsubscribe on unmount
5. **Environment variables**: Never hardcode URLs, use `.env` files
6. **Type safety**: Always import and use TypeScript types

## 📦 Dependencies

```json
{
  "dependencies": {
    "axios": "^1.13.6",
    "@stomp/stompjs": "^7.0.0",
    "sockjs-client": "^1.6.1",
    "@tanstack/react-query": "^5.90.21"
  },
  "devDependencies": {
    "@types/sockjs-client": "^1.6.4"
  }
}
```

## 🎯 Common Patterns

### Fetch and Display Data

```typescript
function Dashboard() {
  const { data, isLoading, error } = useDashboard();

  if (isLoading) return <Spin />;
  if (error) return <Alert message="Failed to load" type="error" />;

  return <div>CPU: {data.cpuUsage}%</div>;
}
```

### Real-time Updates

```typescript
function LiveMetrics() {
  const [metrics, setMetrics] = useState<MetricSnapshot[]>([]);

  useMetricsStream((metric) => {
    setMetrics(prev => [...prev.slice(-99), metric]); // Keep last 100
  });

  return <MetricsChart data={metrics} />;
}
```

### Conditional Subscriptions

```typescript
function IssueMonitor({ enabled }: { enabled: boolean }) {
  useIssuesStream(
    useCallback(
      (issue) => {
        if (enabled && issue.severity === "CRITICAL") {
          notification.error({ message: issue.details });
        }
      },
      [enabled],
    ),
  );

  return null;
}
```

## 📖 Additional Resources

- [Axios Documentation](https://axios-http.com/)
- [STOMP Protocol](https://stomp.github.io/)
- [React Query Guide](https://tanstack.com/query/latest/docs/react/overview)
- [SockJS Documentation](https://github.com/sockjs/sockjs-client)
