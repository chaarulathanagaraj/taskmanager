# AIOS Frontend Hooks

Comprehensive collection of custom React hooks for the AIOS monitoring system.

## 📁 Structure

```
frontend/src/hooks/
├── useMetrics.ts         # REST API data fetching hooks
├── useWebSocket.ts       # WebSocket real-time streaming hooks
├── useNotifications.ts   # Desktop notification hooks
├── useProcessFilter.ts   # Process filtering and sorting hooks
├── usePagination.ts      # Pagination utility hook
├── useCommon.ts          # Common utility hooks
├── index.ts              # Central exports
└── README.md             # This file
```

## 📊 Data Fetching Hooks

### `useMetrics(minutes?)`

Fetch recent system metrics with auto-refresh.

```typescript
import { useMetrics } from '@/hooks';

function MetricsDisplay() {
  const { data, isLoading, error } = useMetrics(10); // Last 10 minutes

  if (isLoading) return <Spin />;
  if (error) return <Alert message="Failed to load" />;

  return <div>CPU: {data[0].cpuUsage}%</div>;
}
```

**Parameters:**

- `minutes` (optional): Number of minutes of historical data (default: 10)

**Returns:** React Query result with `MetricSnapshot[]`

**Auto-refresh:** Every 10 seconds

### `useIssues()`

Fetch active diagnostic issues.

```typescript
const { data: issues } = useIssues();
```

**Returns:** `DiagnosticIssue[]`

### `useHighPriorityIssues()`

Fetch only high-priority issues.

```typescript
const { data: criticalIssues } = useHighPriorityIssues();
```

### `useActions(hours?)`

Fetch remediation action history.

```typescript
const { data: actions } = useActions(24); // Last 24 hours
```

**Parameters:**

- `hours` (optional): Hours of history (default: 24)

### `useDashboard()`

Fetch complete dashboard data (metrics, issues, actions, processes).

```typescript
const { data: dashboard } = useDashboard();

// Access: dashboard.cpuUsage, dashboard.topProcesses, etc.
```

### `useSystemHealth()`

Fetch system health status.

```typescript
const { data: health } = useSystemHealth();
```

**Auto-refresh:** Every 30 seconds

### `useActionStats(hours?)`

Fetch action statistics.

```typescript
const { data: stats } = useActionStats(24);
```

## 🔌 WebSocket Hooks

### `useWebSocket()`

Establish WebSocket connection. Auto-connects on mount.

```typescript
import { useWebSocket } from '@/hooks';

function Component() {
  const ws = useWebSocket();

  console.log('Connected:', ws.isConnected());

  return <div>...</div>;
}
```

### `useMetricsStream(callback)`

Subscribe to real-time metric updates.

```typescript
import { useMetricsStream } from '@/hooks';

function LiveMetrics() {
  const [latest, setLatest] = useState(null);

  useMetricsStream((metric) => {
    setLatest(metric);
  });

  return <div>Live CPU: {latest?.cpuUsage}%</div>;
}
```

**Parameters:**

- `callback`: Function called with each new `MetricSnapshot`

### `useIssuesStream(callback)`

Subscribe to real-time issue updates.

```typescript
useIssuesStream((issue) => {
  if (issue.severity === "CRITICAL") {
    alert(`Critical issue: ${issue.details}`);
  }
});
```

### `useActionsStream(callback)`

Subscribe to real-time action updates.

```typescript
useActionsStream((action) => {
  console.log("Action executed:", action);
});
```

## 🔔 Notification Hooks

### `useNotifications(enabled?)`

Auto-show notifications for critical issues.

```typescript
import { useNotifications } from '@/hooks';

function App() {
  const [notificationsEnabled, setNotificationsEnabled] = useState(true);

  useNotifications(notificationsEnabled);

  return <Switch checked={notificationsEnabled} onChange={setNotificationsEnabled} />;
}
```

**Features:**

- Automatic severity-based notifications (error/warning/info)
- Browser desktop notifications for critical issues
- Auto-closes based on severity (critical = never, high = 10s, medium = 6s, low = 4s)
- Tracks notified issues to avoid duplicates
- Requests notification permission on mount

## 🔍 Process Filter Hooks

### `useProcessFilter(processes, options)`

Filter and sort processes.

```typescript
import { useProcessFilter } from '@/hooks';

function ProcessList({ processes }) {
  const filtered = useProcessFilter(processes, {
    searchTerm: 'chrome',
    minCpu: 5,
    minMemory: 100, // MB
    sortBy: 'cpu',
    sortOrder: 'desc',
  });

  return <ProcessTable data={filtered} />;
}
```

**Options:**

- `searchTerm`: Search by name or PID
- `minCpu`: Minimum CPU usage percentage
- `minMemory`: Minimum memory in MB
- `sortBy`: 'cpu' | 'memory' | 'threads' | 'handles' | 'name' | 'pid'
- `sortOrder`: 'asc' | 'desc'

### `useProcessFilterState()`

Manage filter state with setState functions.

```typescript
const {
  filters,
  setSearchTerm,
  setMinCpu,
  setMinMemory,
  setSortBy,
  setSortOrder,
  reset,
} = useProcessFilterState();

const filtered = useProcessFilter(processes, filters);
```

## 📄 Pagination Hook

### `usePagination(data, options?)`

Paginate any array of data.

```typescript
import { usePagination } from '@/hooks';

function PaginatedTable({ data }) {
  const {
    paginatedData,
    currentPage,
    totalPages,
    pageSize,
    goToPage,
    nextPage,
    prevPage,
    changePageSize,
  } = usePagination(data, {
    initialPage: 1,
    initialPageSize: 20,
    pageSizeOptions: [10, 20, 50, 100],
  });

  return (
    <div>
      <Table data={paginatedData} />
      <Pagination
        current={currentPage}
        total={totalPages}
        pageSize={pageSize}
        onChange={goToPage}
        onShowSizeChange={changePageSize}
      />
    </div>
  );
}
```

**Returns:**

- `paginatedData`: Current page data
- `currentPage`: Current page number
- `pageSize`: Items per page
- `totalPages`: Total number of pages
- `totalItems`: Total data length
- `goToPage(n)`: Go to specific page
- `nextPage()`: Go to next page
- `prevPage()`: Go to previous page
- `changePageSize(n)`: Change page size
- `hasNextPage`: Boolean
- `hasPrevPage`: Boolean

## 🛠️ Common Utility Hooks

### `useDebounce(value, options?)`

Debounce a value (useful for search inputs).

```typescript
import { useDebounce } from '@/hooks';

function SearchBox() {
  const [search, setSearch] = useState('');
  const debouncedSearch = useDebounce(search, { delay: 500 });

  useEffect(() => {
    // API call with debouncedSearch
  }, [debouncedSearch]);

  return <Input value={search} onChange={e => setSearch(e.target.value)} />;
}
```

### `usePrevious(value)`

Track the previous value.

```typescript
const count = 5;
const prevCount = usePrevious(count); // Previous value
```

### `useLocalStorage(key, initialValue)`

Persist state to localStorage with JSON serialization.

```typescript
const [theme, setTheme, removeTheme] = useLocalStorage("theme", "dark");

setTheme("light"); // Saves to localStorage
removeTheme(); // Removes from localStorage
```

### `useInterval(callback, delay)`

Run callback on interval.

```typescript
useInterval(() => {
  console.log("Runs every second");
}, 1000);

// Pause by passing null
useInterval(callback, enabled ? 1000 : null);
```

### `useIsMounted()`

Check if component is mounted.

```typescript
const isMounted = useIsMounted();

useEffect(() => {
  if (isMounted) {
    // Safe to update state
  }
}, [isMounted]);
```

### `useWindowSize()`

Track window dimensions.

```typescript
const { width, height } = useWindowSize();

if (width < 768) {
  return <MobileView />;
}
```

### `useDocumentTitle(title)`

Set document title.

```typescript
useDocumentTitle("Dashboard - AIOS");
```

## 🎯 Usage Examples

### Complete Dashboard with Real-time Updates

```typescript
import {
  useDashboard,
  useMetricsStream,
  useNotifications,
  useDocumentTitle,
} from '@/hooks';

function Dashboard() {
  const { data, isLoading } = useDashboard();
  const [liveMetrics, setLiveMetrics] = useState([]);

  // REST API with auto-refresh
  useDocumentTitle('AIOS Dashboard');
  useNotifications(true);

  // Real-time updates
  useMetricsStream((metric) => {
    setLiveMetrics(prev => [...prev.slice(-99), metric]);
  });

  if (isLoading) return <Spin />;

  return (
    <div>
      <MetricsChart data={liveMetrics} />
      <ProcessTable processes={data.topProcesses} />
    </div>
  );
}
```

### Filtered and Paginated Process Table

```typescript
import {
  useProcessFilter,
  useProcessFilterState,
  usePagination,
  useDebounce,
} from '@/hooks';

function ProcessTable({ processes }) {
  const [search, setSearch] = useState('');
  const debouncedSearch = useDebounce(search, { delay: 300 });

  const { filters, setMinCpu, setSortBy } = useProcessFilterState();
  const filtered = useProcessFilter(processes, {
    ...filters,
    searchTerm: debouncedSearch,
  });

  const { paginatedData, currentPage, totalPages, goToPage } =
    usePagination(filtered, { pageSize: 20 });

  return (
    <div>
      <Input
        placeholder="Search processes..."
        value={search}
        onChange={e => setSearch(e.target.value)}
      />
      <Slider min={0} max={100} onChange={setMinCpu} />
      <Table data={paginatedData} onSort={setSortBy} />
      <Pagination current={currentPage} total={totalPages} onChange={goToPage} />
    </div>
  );
}
```

### Settings with LocalStorage Persistence

```typescript
import { useLocalStorage } from '@/hooks';

function Settings() {
  const [settings, setSettings] = useLocalStorage('aios-settings', {
    autoRemediation: false,
    notificationsEnabled: true,
    refreshInterval: 10,
  });

  return (
    <Form initialValues={settings} onFinish={setSettings}>
      <Form.Item name="autoRemediation">
        <Switch />
      </Form.Item>
    </Form>
  );
}
```

## 🔄 Hook Dependencies

Some hooks build on others:

- `useMetricsStream` → uses `useWebSocket`
- `useIssuesStream` → uses `useWebSocket`
- `useActionsStream` → uses `useWebSocket`
- `useNotifications` → uses `useIssuesStream`

The WebSocket connection is shared across all streaming hooks.

## ⚡ Performance Tips

1. **Memoize callbacks**: Use `useCallback` for stream callbacks
2. **Debounce inputs**: Use `useDebounce` for search/filter inputs
3. **Paginate large lists**: Use `usePagination` for 100+ items
4. **Conditional subscriptions**: Pass `null` to disable intervals
5. **LocalStorage**: Store user preferences to persist across sessions

## 📚 Best Practices

1. **Error handling**: Always check `isLoading` and `error` from React Query hooks
2. **Cleanup**: Streaming hooks auto-unsubscribe on unmount
3. **Type safety**: All hooks are fully typed with TypeScript
4. **Separation of concerns**: Use hooks for logic, components for presentation
5. **Reusability**: Combine hooks for complex behaviors

## 🔧 Configuration

Hook behavior is configured via `src/config/env.ts`:

```typescript
// Adjust polling intervals
POLLING_INTERVALS.METRICS = 5000; // 5 seconds
POLLING_INTERVALS.ISSUES = 15000; // 15 seconds

// WebSocket reconnection
WS_RECONNECT.MAX_ATTEMPTS = 10;
WS_RECONNECT.DELAY = 5000; // 5 seconds
```

## 📦 Dependencies

All hooks depend on:

- `react` (useState, useEffect, useMemo, etc.)
- `@tanstack/react-query` (for data fetching hooks)
- `antd` (for notifications)

## 🎓 Learning Resources

- [React Hooks Documentation](https://react.dev/reference/react)
- [React Query Guide](https://tanstack.com/query/latest/docs/react/overview)
- [Custom Hooks Best Practices](https://react.dev/learn/reusing-logic-with-custom-hooks)
