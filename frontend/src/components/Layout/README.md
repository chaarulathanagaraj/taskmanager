# AIOS Frontend Layout System

Complete layout system with sidebar navigation, responsive design, and reusable components.

## 📁 Structure

```
frontend/src/
├── components/
│   ├── Layout/
│   │   ├── MainLayout.tsx       # Main layout wrapper
│   │   ├── AppSidebar.tsx       # Sidebar navigation menu
│   │   ├── AppHeader.tsx        # Header with health status
│   │   ├── AppFooter.tsx        # Footer with links
│   │   ├── AppBreadcrumb.tsx    # Breadcrumb navigation
│   │   └── index.ts             # Exports
│   ├── Loading.tsx              # Loading spinner
│   ├── ErrorDisplay.tsx         # Error message display
│   ├── EmptyState.tsx           # Empty data placeholder
│   ├── PageCard.tsx             # Styled card component
│   └── index.ts                 # All component exports
├── pages/
│   ├── Dashboard.tsx
│   ├── IssuesPage.tsx
│   ├── ActionsPage.tsx
│   ├── SettingsPage.tsx
│   ├── AboutPage.tsx            # System information
│   └── NotFoundPage.tsx         # 404 page
└── App.tsx                      # Main app with layout
```

## 🎨 Layout Components

### MainLayout

The main layout wrapper that provides the application structure.

**Features:**

- Collapsible sidebar
- Sticky header
- Fixed footer
- Responsive design
- Content area with padding

**Usage:**

```tsx
<MainLayout
  sidebar={<AppSidebar />}
  header={<AppHeader />}
  footer={<AppFooter />}
  sidebarCollapsed={collapsed}
  onSidebarCollapse={setCollapsed}
>
  <YourContent />
</MainLayout>
```

### AppSidebar

Sidebar navigation menu with icons and nested items.

**Features:**

- Dark theme
- Icon navigation
- Nested menu items (Monitoring, System)
- Active route highlighting
- Collapsible

**Routes:**

- `/` - Dashboard
- `/issues` - Issues
- `/actions` - Actions
- `/metrics` - System Metrics
- `/processes` - Processes
- `/settings` - Settings
- `/about` - About

### AppHeader

Header bar with system status and user menu.

**Features:**

- System health indicator (green/red)
- Notification bell icon
- User avatar and dropdown menu
- Responsive design

**Health Status:**

- ✅ Green `CheckCircleOutlined` - HEALTHY/UP
- ❌ Red `CloseCircleOutlined` - UNHEALTHY/DOWN

### AppFooter

Footer with copyright and links.

**Features:**

- Copyright notice
- GitHub link
- Version display
- Made with ❤️ message

### AppBreadcrumb

Breadcrumb navigation showing current path.

**Features:**

- Home icon for root
- Auto-generates from route
- Clickable parent paths
- Hides on homepage

## 🛠️ Utility Components

### Loading

Customizable loading spinner.

```tsx
// Inline loading
<Loading tip="Loading data..." size="default" />

// Full-screen loading
<Loading fullScreen tip="Initializing..." />
```

**Props:**

- `tip` - Loading message
- `size` - 'small' | 'default' | 'large'
- `fullScreen` - Cover entire viewport

### ErrorDisplay

Error message with optional back button.

```tsx
<ErrorDisplay
  title="Failed to load"
  message="Unable to connect to backend"
  showBackButton={true}
/>
```

### EmptyState

Empty data placeholder with optional action.

```tsx
<EmptyState
  description="No issues found"
  action={{
    text: "Refresh",
    onClick: () => refetch(),
  }}
/>
```

### PageCard

Styled card for page sections.

```tsx
<PageCard title="System Metrics">
  <MetricsChart />
</PageCard>
```

## 📄 Pages

### Dashboard (`/`)

Main dashboard with metrics overview, charts, and top processes.

### IssuesPage (`/issues`)

List of diagnostic issues with filtering and AI analysis.

### ActionsPage (`/actions`)

Remediation action history with detailed filters.

### SettingsPage (`/settings`)

Configuration for agent behavior and protected processes.

### AboutPage (`/about`)

System information, version, technology stack, and features.

### NotFoundPage (`*`)

404 error page with back button.

## 🎯 App.tsx Structure

```tsx
<ConfigProvider theme={...}>
  <QueryClientProvider>
    <BrowserRouter>
      <AppContent>
        <MainLayout
          sidebar={<AppSidebar />}
          header={<AppHeader />}
          footer={<AppFooter />}
        >
          <AppBreadcrumb />
          <Routes>
            {/* Routes here */}
          </Routes>
        </MainLayout>
      </AppContent>
    </BrowserRouter>
  </QueryClientProvider>
</ConfigProvider>
```

## 🎨 Theme Configuration

Ant Design theme is configured in App.tsx:

```tsx
<ConfigProvider
  theme={{
    algorithm: theme.defaultAlgorithm,
    token: {
      colorPrimary: '#1890ff',
      borderRadius: 6,
    },
  }}
>
```

**Customizable tokens:**

- `colorPrimary` - Primary brand color
- `borderRadius` - Global border radius
- `fontSize` - Base font size
- `colorSuccess` - Success state color
- `colorWarning` - Warning state color
- `colorError` - Error state color

## 📱 Responsive Design

The layout automatically adapts to different screen sizes:

**Desktop (>1200px):**

- Full sidebar (250px width)
- Expanded content area
- All features visible

**Tablet (768px - 1200px):**

- Collapsible sidebar (80px when collapsed)
- Adjusted content padding
- Touch-friendly controls

**Mobile (<768px):**

- Hidden sidebar (overlay mode)
- Single-column layout
- Hamburger menu

## 🔧 Customization

### Sidebar Width

Edit `AppSidebar.tsx`:

```tsx
<Sider width={250}> {/* Change to your value */}
```

### Header Height

Edit `AppHeader.tsx`:

```tsx
<Header style={{ height: 64 }}> {/* Default is 64px */}
```

### Content Padding

Edit `MainLayout.tsx`:

```tsx
<Content style={{ margin: '24px 16px', padding: 24 }}>
```

### Footer Style

Edit `AppFooter.tsx`:

```tsx
<Footer style={{ textAlign: 'center', background: '#f0f2f5' }}>
```

## 🎨 Color Scheme

**Primary Colors:**

- Primary Blue: `#1890ff`
- Success Green: `#52c41a`
- Warning Orange: `#faad14`
- Error Red: `#ff4d4f`

**Gray Scale:**

- Dark: `#262626`
- Medium: `#8c8c8c`
- Light: `#d9d9d9`
- Background: `#f0f2f5`

## 🚀 Features

✅ **Collapsible sidebar** - Toggle with button or keyboard shortcut  
✅ **Sticky header** - Always visible when scrolling  
✅ **Breadcrumb navigation** - Shows current path  
✅ **System health indicator** - Real-time backend status  
✅ **Notification system** - Critical issue alerts  
✅ **User menu** - Settings and logout  
✅ **404 handling** - Friendly error page  
✅ **Loading states** - Smooth transitions  
✅ **Empty states** - Helpful placeholders  
✅ **Responsive design** - Works on all devices  
✅ **Theme support** - Customizable colors

## 📝 Best Practices

1. **Use PageCard** for consistent section styling
2. **Show Loading** during async operations
3. **Display ErrorDisplay** on fetch failures
4. **Use EmptyState** when no data exists
5. **Keep sidebar items** under 10 for usability
6. **Test responsiveness** at all breakpoints
7. **Maintain consistent spacing** (use Ant Design Space)
8. **Use semantic HTML** for accessibility

## 🔍 Accessibility

The layout includes accessibility features:

- **Keyboard navigation** - Tab through menu items
- **ARIA labels** - Screen reader support
- **Focus indicators** - Visible focus states
- **Color contrast** - WCAG AA compliant
- **Semantic HTML** - Proper heading hierarchy

## 🧪 Testing Layout

```tsx
// Test sidebar navigation
it("should navigate on menu click", () => {
  render(<App />);
  fireEvent.click(screen.getByText("Issues"));
  expect(window.location.pathname).toBe("/issues");
});

// Test 404 page
it("should show 404 for unknown route", () => {
  render(<App />, { route: "/unknown" });
  expect(screen.getByText("404")).toBeInTheDocument();
});
```

## 📦 Dependencies

- `antd` - UI components
- `react-router-dom` - Routing
- `@ant-design/icons` - Icons

## 🎓 Resources

- [Ant Design Layout](https://ant.design/components/layout)
- [React Router Documentation](https://reactrouter.com/)
- [Responsive Design Guide](https://web.dev/responsive-web-design-basics/)
