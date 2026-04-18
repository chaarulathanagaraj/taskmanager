# 🚀 JAVA AI FULLSTACK - WINDOWS SYSTEM MONITOR

## 20-Day Implementation Plan

---

## 🏗️ TECHNOLOGY STACK

### Backend & Agent

- **Java 17+** (LTS)
- **Spring Boot 3.2+** (REST API, WebSocket, Scheduling)
- **OSHI** (Operating System & Hardware Information)
- **JNA/JNI** (Windows Native API calls)
- **LangChain4j** (AI Agent Framework - Java alternative to CrewAI)
- **OpenAI Java SDK** (AI integration)
- **H2/PostgreSQL** (Database)
- **Maven/Gradle** (Build tool)
- **Lombok** (Reduce boilerplate)
- **Jackson** (JSON processing)

### Frontend

- **React 18+** (TypeScript)
- **Vite** (Build tool)
- **TanStack Query** (Data fetching)
- **Recharts** (Visualization)
- **Ant Design / Material-UI** (Component library)
- **Axios** (HTTP client)

### AI & Tools

- **LangChain4j** (Multi-agent AI orchestration)
- **OpenAI GPT-4** (AI reasoning)
- **MCP Server** (Java implementation)
- **Spring AI** (Alternative AI framework)

---

# 🗓️ DAY-BY-DAY IMPLEMENTATION PLAN

## 📅 DAY 1: Project Setup & Architecture Design

### Tasks:

1. **Delete old Python files** (manual cleanup)

2. **Create Maven/Gradle multi-module project structure:**

```
aios-monitor/
├── agent/                          # Java monitoring agent
│   ├── src/main/java/com/aios/agent/
│   │   ├── collector/              # System metrics collection
│   │   ├── detector/               # Issue detection
│   │   ├── remediation/            # Fix execution
│   │   └── AgentApplication.java
│   └── pom.xml
├── backend/                        # Spring Boot API
│   ├── src/main/java/com/aios/backend/
│   │   ├── controller/             # REST controllers
│   │   ├── service/                # Business logic
│   │   ├── repository/             # Data access
│   │   ├── model/                  # Entities
│   │   ├── dto/                    # Data transfer objects
│   │   └── BackendApplication.java
│   └── pom.xml
├── mcp-server/                     # MCP tool server (Java)
│   ├── src/main/java/com/aios/mcp/
│   │   ├── tools/                  # Tool definitions
│   │   ├── server/                 # MCP server
│   │   └── McpServerApplication.java
│   └── pom.xml
├── ai-agents/                      # LangChain4j agents
│   ├── src/main/java/com/aios/ai/
│   │   ├── agents/                 # AI agent definitions
│   │   ├── tools/                  # AI tools
│   │   ├── chains/                 # Agent chains
│   │   └── AiAgentsApplication.java
│   └── pom.xml
├── shared/                         # Shared DTOs/models
│   ├── src/main/java/com/aios/shared/
│   │   ├── dto/
│   │   └── enums/
│   └── pom.xml
├── frontend/                       # React dashboard
│   ├── src/
│   │   ├── components/
│   │   ├── pages/
│   │   ├── services/
│   │   └── App.tsx
│   └── package.json
└── pom.xml                         # Parent POM
```

3. **Define Java data models (in shared module):**

```java
// MetricSnapshot.java
@Data
@Builder
public class MetricSnapshot {
    private Instant timestamp;
    private double cpuUsage;
    private long memoryUsed;
    private long memoryTotal;
    private long diskRead;
    private long diskWrite;
    private long networkSent;
    private long networkReceived;
}

// ProcessInfo.java
@Data
@Builder
public class ProcessInfo {
    private int pid;
    private String name;
    private double cpuPercent;
    private long memoryBytes;
    private int threadCount;
    private long handleCount;
    private long ioReadBytes;
    private long ioWriteBytes;
}

// DiagnosticIssue.java
@Data
@Builder
public class DiagnosticIssue {
    private Long id;
    private IssueType type;
    private Severity severity;
    private double confidence;
    private int affectedPid;
    private String processName;
    private String details;
    private Instant detectedAt;
}

// RemediationAction.java
@Data
@Builder
public class RemediationAction {
    private Long id;
    private ActionType actionType;
    private int targetPid;
    private String targetName;
    private SafetyLevel safetyLevel;
    private ActionStatus status;
    private String result;
    private boolean dryRun;
    private Instant executedAt;
}
```

4. **Architecture diagram** (draw.io):

```
┌─────────────────────────────────────────────────────────┐
│                    REACT DASHBOARD                      │
│            (TypeScript + Ant Design + Recharts)         │
└────────────────────┬────────────────────────────────────┘
                     │ HTTP/WebSocket
                     ▼
┌─────────────────────────────────────────────────────────┐
│              SPRING BOOT BACKEND (Port 8080)            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │
│  │ REST API     │  │ WebSocket    │  │ Database     │ │
│  │ Controllers  │  │ (Real-time)  │  │ PostgreSQL   │ │
│  └──────────────┘  └──────────────┘  └──────────────┘ │
└────────────────────┬────────────────────────────────────┘
                     │
        ┌────────────┴────────────┐
        ▼                         ▼
┌──────────────────┐    ┌──────────────────────────┐
│  JAVA AGENT      │    │  LANGCHAIN4J AI AGENTS   │
│  (OSHI + JNA)    │    │  (OpenAI GPT-4)          │
│  ┌────────────┐  │    │  ┌────────────────────┐  │
│  │ Collector  │  │    │  │ Leak Detector AI  │  │
│  ├────────────┤  │    │  ├────────────────────┤  │
│  │ Detectors  │  │◄───┤  │ Thread Expert AI   │  │
│  ├────────────┤  │    │  ├────────────────────┤  │
│  │ Remediation│  │    │  │ IO Analyst AI      │  │
│  └────────────┘  │    │  └────────────────────┘  │
└──────────────────┘    └──────────────────────────┘
        │                         │
        └────────────┬────────────┘
                     ▼
           ┌──────────────────┐
           │   MCP SERVER     │
           │  (Port 8081)     │
           │  Standardized    │
           │  Tool Interface  │
           └──────────────────┘
```

### Deliverable:

✅ Maven multi-module project structure  
✅ Shared data models defined  
✅ Architecture diagram  
✅ Parent POM with dependency management

---

## 📅 DAY 2: Java Agent Core (Data Collection)

### Tasks:

1. **Create `agent/pom.xml`:**

```xml
<dependencies>
    <!-- System monitoring -->
    <dependency>
        <groupId>com.github.oshi</groupId>
        <artifactId>oshi-core</artifactId>
        <version>6.4.11</version>
    </dependency>

    <!-- Windows native calls -->
    <dependency>
        <groupId>net.java.dev.jna</groupId>
        <artifactId>jna-platform</artifactId>
        <version>5.14.0</version>
    </dependency>

    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>

    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </dependency>

    <!-- HTTP client for backend communication -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
</dependencies>
```

2. **Create `SystemMetricsCollector.java`:**

```java
@Service
@Slf4j
public class SystemMetricsCollector {

    private final SystemInfo systemInfo;
    private final HardwareAbstractionLayer hardware;
    private final OperatingSystem os;
    private final Deque<MetricSnapshot> metricsHistory;

    public SystemMetricsCollector() {
        this.systemInfo = new SystemInfo();
        this.hardware = systemInfo.getHardware();
        this.os = systemInfo.getOperatingSystem();
        this.metricsHistory = new LinkedBlockingDeque<>(360); // 1 hour at 10s intervals
    }

    @Scheduled(fixedDelay = 10000) // Every 10 seconds
    public void collectMetrics() {
        MetricSnapshot snapshot = MetricSnapshot.builder()
            .timestamp(Instant.now())
            .cpuUsage(getCpuUsage())
            .memoryUsed(getMemoryUsed())
            .memoryTotal(hardware.getMemory().getTotal())
            .diskRead(getDiskRead())
            .diskWrite(getDiskWrite())
            .networkSent(getNetworkSent())
            .networkReceived(getNetworkReceived())
            .build();

        metricsHistory.addLast(snapshot);
        if (metricsHistory.size() > 360) {
            metricsHistory.removeFirst();
        }

        log.debug("Collected metrics: CPU={}%, Memory={} MB",
            snapshot.getCpuUsage(), snapshot.getMemoryUsed() / 1024 / 1024);
    }

    private double getCpuUsage() {
        CentralProcessor processor = hardware.getProcessor();
        long[] prevTicks = processor.getSystemCpuLoadTicks();
        Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
        double cpuLoad = processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100;
        return Math.round(cpuLoad * 100.0) / 100.0;
    }

    private long getMemoryUsed() {
        GlobalMemory memory = hardware.getMemory();
        return memory.getTotal() - memory.getAvailable();
    }

    // ... disk, network methods
}
```

3. **Create `ProcessInfoCollector.java`:**

```java
@Service
@Slf4j
public class ProcessInfoCollector {

    private final SystemInfo systemInfo;
    private final OperatingSystem os;

    public List<ProcessInfo> getTopProcesses(int limit) {
        return os.getProcesses(null, OperatingSystem.ProcessFiltering.ALL_PROCESSES,
                              OperatingSystem.ProcessSorting.CPU, limit)
            .stream()
            .map(this::toProcessInfo)
            .collect(Collectors.toList());
    }

    public Optional<ProcessInfo> getProcessInfo(int pid) {
        OSProcess process = os.getProcess(pid);
        return process != null ?
            Optional.of(toProcessInfo(process)) : Optional.empty();
    }

    private ProcessInfo toProcessInfo(OSProcess process) {
        return ProcessInfo.builder()
            .pid(process.getProcessID())
            .name(process.getName())
            .cpuPercent(process.getProcessCpuLoadCumulative() * 100)
            .memoryBytes(process.getResidentSetSize())
            .threadCount(process.getThreadCount())
            .handleCount(process.getHandleCount())
            .ioReadBytes(process.getBytesRead())
            .ioWriteBytes(process.getBytesWritten())
            .build();
    }
}
```

4. **Create `AgentConfiguration.java`:**

```java
@Configuration
@ConfigurationProperties(prefix = "agent")
@Data
public class AgentConfiguration {
    private int collectionIntervalSeconds = 10;
    private int retentionMinutes = 60;
    private String backendUrl = "http://localhost:8080";
    private boolean dryRunMode = true;
    private List<String> protectedProcesses = Arrays.asList(
        "System", "csrss.exe", "lsass.exe", "winlogon.exe",
        "services.exe", "smss.exe", "svchost.exe"
    );
}
```

5. **Create `AgentApplication.java`:**

```java
@SpringBootApplication
@EnableScheduling
@Slf4j
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(AgentApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.run(args);

        log.info("╔════════════════════════════════════════╗");
        log.info("║   AIOS Monitoring Agent Started       ║");
        log.info("╚════════════════════════════════════════╝");
    }

    @Bean
    public CommandLineRunner startupInfo(AgentConfiguration config) {
        return args -> {
            log.info("Collection interval: {}s", config.getCollectionIntervalSeconds());
            log.info("Backend URL: {}", config.getBackendUrl());
            log.info("Dry-run mode: {}", config.isDryRunMode());
        };
    }
}
```

### Deliverable:

✅ Java agent collects CPU, memory, disk, network metrics every 10s  
✅ Uses OSHI library for cross-platform monitoring  
✅ Stores last 1 hour of metrics in memory

---

## 📅 DAY 3: Java Agent - Issue Detection

### Tasks:

1. **Create detector interfaces:**

```java
public interface IssueDetector {
    List<DiagnosticIssue> detect(List<MetricSnapshot> metrics, List<ProcessInfo> processes);
}

@Data
@Builder
public class DetectionResult {
    private IssueType type;
    private Severity severity;
    private double confidence;
    private int affectedPid;
    private String processName;
    private Map<String, Object> evidence;
}
```

2. **Create `MemoryLeakDetector.java`:**

```java
@Component
@Slf4j
public class MemoryLeakDetector implements IssueDetector {

    @Override
    public List<DiagnosticIssue> detect(List<MetricSnapshot> metrics,
                                        List<ProcessInfo> processes) {
        List<DiagnosticIssue> issues = new ArrayList<>();

        for (ProcessInfo process : processes) {
            DetectionResult result = analyzeProcess(process, metrics);
            if (result != null && result.getConfidence() > 0.6) {
                issues.add(toIssue(result));
            }
        }

        return issues;
    }

    private DetectionResult analyzeProcess(ProcessInfo process,
                                          List<MetricSnapshot> metrics) {
        // Track memory growth over time
        // Use linear regression to detect steady increase
        // Calculate confidence based on slope and R²

        double memoryGrowthRate = calculateGrowthRate(process);
        double confidence = calculateConfidence(memoryGrowthRate);

        if (confidence > 0.6) {
            return DetectionResult.builder()
                .type(IssueType.MEMORY_LEAK)
                .severity(determineSeverity(memoryGrowthRate))
                .confidence(confidence)
                .affectedPid(process.getPid())
                .processName(process.getName())
                .evidence(Map.of(
                    "growthRate", memoryGrowthRate,
                    "currentMemory", process.getMemoryBytes(),
                    "trend", "increasing"
                ))
                .build();
        }

        return null;
    }
}
```

3. **Create additional detectors:**

- `ThreadExplosionDetector.java` - detects rapid thread creation
- `HungProcessDetector.java` - detects processes with 0% CPU but high handles
- `IOBottleneckDetector.java` - detects I/O wait issues
- `ResourceHogDetector.java` - detects processes consuming excessive resources

4. **Create `DetectorManager.java`:**

```java
@Service
@Slf4j
public class DetectorManager {

    private final List<IssueDetector> detectors;
    private final SystemMetricsCollector metricsCollector;
    private final ProcessInfoCollector processCollector;
    private final Map<String, DiagnosticIssue> activeIssues = new ConcurrentHashMap<>();

    @Scheduled(fixedDelay = 30000) // Every 30 seconds
    public void runDetection() {
        List<MetricSnapshot> recentMetrics = metricsCollector.getRecentMetrics(60);
        List<ProcessInfo> topProcesses = processCollector.getTopProcesses(50);

        List<DiagnosticIssue> newIssues = new ArrayList<>();

        for (IssueDetector detector : detectors) {
            newIssues.addAll(detector.detect(recentMetrics, topProcesses));
        }

        // Deduplicate by PID + type
        newIssues = deduplicateIssues(newIssues);

        // Update active issues
        updateActiveIssues(newIssues);

        log.info("Detection complete: {} issues found", newIssues.size());
    }
}
```

### Deliverable:

✅ 5 issue detectors implemented  
✅ Detection runs every 30 seconds  
✅ Confidence scores calculated  
✅ Issue deduplication

---

## 📅 DAY 4: Java Agent - Remediation Engine

### Tasks:

1. **Create `RemediationAction` interface:**

```java
public interface RemediationAction {
    ActionResult execute(RemediationContext context) throws RemediationException;
    SafetyLevel getSafetyLevel();
    boolean isDryRun();
}

@Data
public class RemediationContext {
    private int targetPid;
    private String processName;
    private DiagnosticIssue issue;
    private boolean dryRun;
    private List<String> protectedProcesses;
}

@Data
@Builder
public class ActionResult {
    private boolean success;
    private String message;
    private Map<String, Object> details;
    private Instant executedAt;
}
```

2. **Create remediation actions:**

```java
// KillProcessAction.java
@Component
@Slf4j
public class KillProcessAction implements RemediationAction {

    @Override
    public ActionResult execute(RemediationContext context) {
        if (context.isDryRun()) {
            return ActionResult.builder()
                .success(true)
                .message("[DRY RUN] Would terminate process " + context.getProcessName())
                .build();
        }

        // Safety checks
        if (context.getProtectedProcesses().contains(context.getProcessName())) {
            throw new RemediationException("Cannot kill protected process");
        }

        try {
            ProcessHandle.of(context.getTargetPid())
                .ifPresent(ProcessHandle::destroy);

            return ActionResult.builder()
                .success(true)
                .message("Process terminated successfully")
                .executedAt(Instant.now())
                .build();
        } catch (Exception e) {
            log.error("Failed to kill process", e);
            return ActionResult.builder()
                .success(false)
                .message("Failed: " + e.getMessage())
                .build();
        }
    }

    @Override
    public SafetyLevel getSafetyLevel() {
        return SafetyLevel.HIGH;
    }
}

// ReducePriorityAction.java
@Component
public class ReducePriorityAction implements RemediationAction {

    @Override
    public ActionResult execute(RemediationContext context) {
        // Use JNA to call Windows SetPriorityClass
        if (context.isDryRun()) {
            return ActionResult.builder()
                .success(true)
                .message("[DRY RUN] Would reduce priority")
                .build();
        }

        WindowsNativeUtils.setPriority(context.getTargetPid(), Priority.BELOW_NORMAL);
        return ActionResult.builder()
            .success(true)
            .message("Priority reduced to BELOW_NORMAL")
            .build();
    }

    @Override
    public SafetyLevel getSafetyLevel() {
        return SafetyLevel.LOW;
    }
}
```

3. **Create Windows JNA utilities:**

```java
@Slf4j
public class WindowsNativeUtils {

    private static final Kernel32 KERNEL32 = Native.load("kernel32", Kernel32.class);

    public static void setPriority(int pid, Priority priority) {
        HANDLE hProcess = KERNEL32.OpenProcess(
            WinNT.PROCESS_SET_INFORMATION, false, pid);

        if (hProcess != null) {
            KERNEL32.SetPriorityClass(hProcess, priority.getValue());
            KERNEL32.CloseHandle(hProcess);
        }
    }

    public static void trimWorkingSet(int pid) {
        HANDLE hProcess = KERNEL32.OpenProcess(
            WinNT.PROCESS_SET_QUOTA, false, pid);

        if (hProcess != null) {
            Psapi.INSTANCE.EmptyWorkingSet(hProcess);
            KERNEL32.CloseHandle(hProcess);
        }
    }
}
```

4. **Create `RemediationEngine.java`:**

```java
@Service
@Slf4j
public class RemediationEngine {

    private final Map<ActionType, RemediationAction> actions;
    private final RuleEngine ruleEngine;
    private final AgentConfiguration config;
    private final ActionLogger actionLogger;

    public ActionResult executeRemediation(DiagnosticIssue issue) {
        // Get recommended action from rule engine
        ActionType actionType = ruleEngine.getRecommendedAction(issue);

        RemediationAction action = actions.get(actionType);
        if (action == null) {
            return ActionResult.builder()
                .success(false)
                .message("No action found for type: " + actionType)
                .build();
        }

        // Create context
        RemediationContext context = RemediationContext.builder()
            .targetPid(issue.getAffectedPid())
            .processName(issue.getProcessName())
            .issue(issue)
            .dryRun(config.isDryRunMode())
            .protectedProcesses(config.getProtectedProcesses())
            .build();

        // Execute action
        ActionResult result = action.execute(context);

        // Log action
        actionLogger.log(actionType, context, result);

        return result;
    }
}
```

5. **Create `RuleEngine.java`:**

```java
@Service
public class RuleEngine {

    public ActionType getRecommendedAction(DiagnosticIssue issue) {
        return switch (issue.getType()) {
            case MEMORY_LEAK -> {
                if (issue.getConfidence() > 0.8) {
                    yield ActionType.KILL_PROCESS;
                } else {
                    yield ActionType.TRIM_WORKING_SET;
                }
            }
            case THREAD_EXPLOSION -> ActionType.KILL_PROCESS;
            case HUNG_PROCESS -> ActionType.KILL_PROCESS;
            case IO_BOTTLENECK -> ActionType.REDUCE_PRIORITY;
            case RESOURCE_HOG -> {
                if (issue.getSeverity() == Severity.CRITICAL) {
                    yield ActionType.KILL_PROCESS;
                } else {
                    yield ActionType.REDUCE_PRIORITY;
                }
            }
        };
    }
}
```

### Deliverable:

✅ 8+ remediation actions implemented  
✅ Dry-run mode working  
✅ Safety checks (protected processes)  
✅ Rule engine maps issues → actions  
✅ Windows JNA integration

---

## 📅 DAY 5: Spring Boot Backend Setup

### Tasks:

1. **Create `backend/pom.xml`:**

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-websocket</artifactId>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        <version>2.3.0</version>
    </dependency>
</dependencies>
```

2. **Create JPA entities:**

```java
@Entity
@Table(name = "metrics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant timestamp;
    private Double cpuUsage;
    private Long memoryUsed;
    private Long memoryTotal;
    private Long diskRead;
    private Long diskWrite;
    private Long networkSent;
    private Long networkReceived;
}

@Entity
@Table(name = "issues")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private IssueType type;

    @Enumerated(EnumType.STRING)
    private Severity severity;

    private Double confidence;
    private Integer affectedPid;
    private String processName;

    @Column(columnDefinition = "TEXT")
    private String details;

    private Instant detectedAt;
    private Instant resolvedAt;
    private Boolean resolved = false;
}

@Entity
@Table(name = "actions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private ActionType actionType;

    private Integer targetPid;
    private String targetName;

    @Enumerated(EnumType.STRING)
    private SafetyLevel safetyLevel;

    @Enumerated(EnumType.STRING)
    private ActionStatus status;

    private String result;
    private Boolean dryRun;
    private Instant executedAt;

    @ManyToOne
    private IssueEntity issue;
}
```

3. **Create repositories:**

```java
public interface MetricRepository extends JpaRepository<MetricEntity, Long> {
    List<MetricEntity> findByTimestampAfter(Instant timestamp);
}

public interface IssueRepository extends JpaRepository<IssueEntity, Long> {
    List<IssueEntity> findByResolvedFalse();
    List<IssueEntity> findByDetectedAtAfter(Instant timestamp);
}

public interface ActionRepository extends JpaRepository<ActionEntity, Long> {
    List<ActionEntity> findByExecutedAtAfter(Instant timestamp);
}
```

4. **Create REST controllers:**

```java
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricController {

    private final MetricService metricService;

    @PostMapping
    public ResponseEntity<Void> saveMetrics(@RequestBody List<MetricSnapshot> metrics) {
        metricService.saveMetrics(metrics);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/recent")
    public ResponseEntity<List<MetricSnapshot>> getRecentMetrics(
            @RequestParam(defaultValue = "10") int minutes) {
        return ResponseEntity.ok(metricService.getRecentMetrics(minutes));
    }
}

@RestController
@RequestMapping("/api/issues")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;

    @PostMapping
    public ResponseEntity<IssueEntity> createIssue(@RequestBody DiagnosticIssue issue) {
        return ResponseEntity.ok(issueService.createIssue(issue));
    }

    @GetMapping
    public ResponseEntity<List<IssueEntity>> getAllIssues() {
        return ResponseEntity.ok(issueService.getAllIssues());
    }

    @GetMapping("/active")
    public ResponseEntity<List<IssueEntity>> getActiveIssues() {
        return ResponseEntity.ok(issueService.getActiveIssues());
    }
}

@RestController
@RequestMapping("/api/actions")
@RequiredArgsConstructor
public class ActionController {

    private final ActionService actionService;

    @PostMapping
    public ResponseEntity<ActionEntity> logAction(@RequestBody RemediationActionLog log) {
        return ResponseEntity.ok(actionService.logAction(log));
    }

    @GetMapping("/history")
    public ResponseEntity<List<ActionEntity>> getHistory(
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(actionService.getRecentActions(hours));
    }
}

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<DashboardData> getDashboard() {
        return ResponseEntity.ok(dashboardService.getDashboardData());
    }
}
```

5. **Create WebSocket configuration:**

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins("*").withSockJS();
    }
}
```

### Deliverable:

✅ Spring Boot backend with REST API  
✅ PostgreSQL database with JPA entities  
✅ WebSocket for real-time updates  
✅ OpenAPI/Swagger documentation

---

## 📅 DAY 6: Connect Agent to Backend

### Tasks:

1. **Create `BackendClient.java` in agent:**

```java
@Service
@Slf4j
public class BackendClient {

    private final WebClient webClient;
    private final BlockingQueue<MetricSnapshot> metricQueue;
    private final BlockingQueue<DiagnosticIssue> issueQueue;
    private final BlockingQueue<RemediationActionLog> actionQueue;

    @Scheduled(fixedDelay = 30000) // Every 30 seconds
    public void syncMetrics() {
        List<MetricSnapshot> batch = new ArrayList<>();
        metricQueue.drainTo(batch);

        if (!batch.isEmpty()) {
            webClient.post()
                .uri("/api/metrics")
                .bodyValue(batch)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(e -> {
                    log.error("Failed to sync metrics", e);
                    metricQueue.addAll(batch); // Re-queue on failure
                })
                .subscribe();
        }
    }

    @Scheduled(fixedDelay = 10000) // Every 10 seconds
    public void syncIssues() {
        List<DiagnosticIssue> batch = new ArrayList<>();
        issueQueue.drainTo(batch);

        if (!batch.isEmpty()) {
            batch.forEach(issue -> {
                webClient.post()
                    .uri("/api/issues")
                    .bodyValue(issue)
                    .retrieve()
                    .bodyToMono(IssueEntity.class)
                    .subscribe();
            });
        }
    }
}
```

2. **Add retry logic with Resilience4j:**

```java
@Configuration
public class ResilienceConfig {

    @Bean
    public Retry backendRetry() {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofSeconds(5))
            .retryExceptions(WebClientResponseException.class)
            .build();

        return Retry.of("backend", config);
    }
}
```

### Deliverable:

✅ Agent sends metrics to backend every 30s  
✅ Queuing system for offline operation  
✅ Retry logic with exponential backoff

---

## 📅 DAY 7: React Frontend Setup (TypeScript)

### Tasks:

1. **Initialize Vite + React + TypeScript:**

```bash
npm create vite@latest frontend -- --template react-ts
cd frontend
npm install
npm install @tanstack/react-query recharts antd axios react-router-dom
npm install -D @types/node
```

2. **Create folder structure:**

```
frontend/src/
├── api/
│   └── client.ts        # Axios setup
├── components/
│   ├── MetricsCard.tsx
│   ├── MetricsChart.tsx
│   └── ProcessTable.tsx
├── pages/
│   ├── Dashboard.tsx
│   ├── IssuesPage.tsx
│   ├── ActionsPage.tsx
│   └── SettingsPage.tsx
├── hooks/
│   └── useMetrics.ts
├── types/
│   └── index.ts
└── App.tsx
```

3. **Create API client:**

```typescript
// api/client.ts
import axios from "axios";

export const apiClient = axios.create({
  baseURL: "http://localhost:8080/api",
  timeout: 10000,
});

export const metricsApi = {
  getRecent: (minutes: number = 10) =>
    apiClient.get<MetricSnapshot[]>(`/metrics/recent?minutes=${minutes}`),
};

export const issuesApi = {
  getAll: () => apiClient.get<DiagnosticIssue[]>("/issues"),
  getActive: () => apiClient.get<DiagnosticIssue[]>("/issues/active"),
};

export const actionsApi = {
  getHistory: (hours: number = 24) =>
    apiClient.get<RemediationAction[]>(`/actions/history?hours=${hours}`),
};

export const dashboardApi = {
  getData: () => apiClient.get<DashboardData>("/dashboard"),
};
```

4. **Create hooks:**

```typescript
// hooks/useMetrics.ts
import { useQuery } from "@tanstack/react-query";
import { metricsApi } from "../api/client";

export const useMetrics = (minutes: number = 10) => {
  return useQuery({
    queryKey: ["metrics", minutes],
    queryFn: () => metricsApi.getRecent(minutes).then((res) => res.data),
    refetchInterval: 10000, // Refresh every 10s
  });
};

export const useIssues = () => {
  return useQuery({
    queryKey: ["issues"],
    queryFn: () => issuesApi.getActive().then((res) => res.data),
    refetchInterval: 10000,
  });
};
```

5. **Create basic layout:**

```typescript
// App.tsx
import { Layout, Menu } from 'antd';
import { BrowserRouter, Routes, Route, Link } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import Dashboard from './pages/Dashboard';
import IssuesPage from './pages/IssuesPage';
import ActionsPage from './pages/ActionsPage';
import SettingsPage from './pages/SettingsPage';

const { Header, Sider, Content } = Layout;
const queryClient = new QueryClient();

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Layout style={{ minHeight: '100vh' }}>
          <Header style={{ color: 'white', fontSize: '24px' }}>
            🤖 AIOS Monitor
          </Header>
          <Layout>
            <Sider width={200}>
              <Menu mode="inline" defaultSelectedKeys={['1']}>
                <Menu.Item key="1">
                  <Link to="/">Dashboard</Link>
                </Menu.Item>
                <Menu.Item key="2">
                  <Link to="/issues">Issues</Link>
                </Menu.Item>
                <Menu.Item key="3">
                  <Link to="/actions">Actions</Link>
                </Menu.Item>
                <Menu.Item key="4">
                  <Link to="/settings">Settings</Link>
                </Menu.Item>
              </Menu>
            </Sider>
            <Layout style={{ padding: '24px' }}>
              <Content>
                <Routes>
                  <Route path="/" element={<Dashboard />} />
                  <Route path="/issues" element={<IssuesPage />} />
                  <Route path="/actions" element={<ActionsPage />} />
                  <Route path="/settings" element={<SettingsPage />} />
                </Routes>
              </Content>
            </Layout>
          </Layout>
        </Layout>
      </BrowserRouter>
    </QueryClientProvider>
  );
}

export default App;
```

### Deliverable:

✅ React + TypeScript + Vite setup  
✅ Ant Design UI library  
✅ TanStack Query for data fetching  
✅ Basic layout with routing

---

## 📅 DAY 8: Dashboard - Metrics Visualization

### Tasks:

1. **Create `MetricsCard.tsx`:**

```typescript
import { Card, Statistic } from 'antd';
import { ArrowUpOutlined, ArrowDownOutlined } from '@ant-design/icons';

interface Props {
  title: string;
  value: number;
  suffix: string;
  threshold: number;
}

export const MetricsCard: React.FC<Props> = ({ title, value, suffix, threshold }) => {
  const color = value > threshold ? 'red' : value > threshold * 0.7 ? 'orange' : 'green';

  return (
    <Card>
      <Statistic
        title={title}
        value={value}
        suffix={suffix}
        valueStyle={{ color }}
        prefix={value > threshold ? <ArrowUpOutlined /> : null}
      />
    </Card>
  );
};
```

2. **Create `MetricsChart.tsx`:**

```typescript
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend } from 'recharts';
import { useMetrics } from '../hooks/useMetrics';

export const MetricsChart: React.FC = () => {
  const { data: metrics } = useMetrics(10);

  const chartData = metrics?.map(m => ({
    time: new Date(m.timestamp).toLocaleTimeString(),
    cpu: m.cpuUsage,
    memory: (m.memoryUsed / m.memoryTotal) * 100,
  })) || [];

  return (
    <LineChart width={800} height={300} data={chartData}>
      <CartesianGrid strokeDasharray="3 3" />
      <XAxis dataKey="time" />
      <YAxis />
      <Tooltip />
      <Legend />
      <Line type="monotone" dataKey="cpu" stroke="#8884d8" name="CPU %" />
      <Line type="monotone" dataKey="memory" stroke="#82ca9d" name="Memory %" />
    </LineChart>
  );
};
```

3. **Create `ProcessTable.tsx`:**

```typescript
import { Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';

interface ProcessTableProps {
  processes: ProcessInfo[];
}

export const ProcessTable: React.FC<ProcessTableProps> = ({ processes }) => {
  const columns: ColumnsType<ProcessInfo> = [
    { title: 'PID', dataIndex: 'pid', key: 'pid', width: 80 },
    { title: 'Name', dataIndex: 'name', key: 'name' },
    { title: 'CPU %', dataKey: 'cpuPercent', key: 'cpu',
      render: (val) => `${val.toFixed(2)}%` },
    { title: 'Memory', dataIndex: 'memoryBytes', key: 'memory',
      render: (val) => `${(val / 1024 / 1024).toFixed(0)} MB` },
    { title: 'Threads', dataIndex: 'threadCount', key: 'threads' },
  ];

  return <Table columns={columns} dataSource={processes} rowKey="pid" />;
};
```

4. **Create `Dashboard.tsx`:**

```typescript
import { Row, Col, Card } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { MetricsCard } from '../components/MetricsCard';
import { MetricsChart } from '../components/MetricsChart';
import { ProcessTable } from '../components/ProcessTable';
import { dashboardApi } from '../api/client';

const Dashboard: React.FC = () => {
  const { data } = useQuery({
    queryKey: ['dashboard'],
    queryFn: () => dashboardApi.getData().then(res => res.data),
    refetchInterval: 10000,
  });

  return (
    <div>
      <Row gutter={16}>
        <Col span={6}>
          <MetricsCard title="CPU Usage" value={data?.cpuUsage || 0}
                       suffix="%" threshold={80} />
        </Col>
        <Col span={6}>
          <MetricsCard title="Memory" value={data?.memoryPercent || 0}
                       suffix="%" threshold={85} />
        </Col>
        <Col span={6}>
          <MetricsCard title="Disk I/O" value={data?.diskIO || 0}
                       suffix="MB/s" threshold={100} />
        </Col>
        <Col span={6}>
          <MetricsCard title="Network" value={data?.networkIO || 0}
                       suffix="MB/s" threshold={50} />
        </Col>
      </Row>

      <Card title="System Trends" style={{ marginTop: 24 }}>
        <MetricsChart />
      </Card>

      <Card title="Top Processes" style={{ marginTop: 24 }}>
        <ProcessTable processes={data?.topProcesses || []} />
      </Card>
    </div>
  );
};

export default Dashboard;
```

### Deliverable:

✅ Real-time metrics cards  
✅ Line charts showing trends  
✅ Process table  
✅ Auto-refresh every 10s

---

## 📅 DAY 9: Issues & Alerts UI

### Tasks:

1. **Create `IssueCard.tsx`:**

```typescript
import { Card, Badge, Progress, Tag, Button } from 'antd';
import { WarningOutlined } from '@ant-design/icons';

interface Props {
  issue: DiagnosticIssue;
  onAnalyze: (id: number) => void;
}

export const IssueCard: React.FC<Props> = ({ issue, onAnalyze }) => {
  const severityColor = {
    LOW: 'blue',
    MEDIUM: 'orange',
    HIGH: 'red',
    CRITICAL: 'purple',
  };

  return (
    <Card
      title={
        <span>
          <WarningOutlined /> {issue.type.replace('_', ' ')}
        </span>
      }
      extra={<Tag color={severityColor[issue.severity]}>{issue.severity}</Tag>}
    >
      <p><strong>Process:</strong> {issue.processName} (PID: {issue.affectedPid})</p>
      <p><strong>Detected:</strong> {new Date(issue.detectedAt).toLocaleString()}</p>
      <p><strong>Confidence:</strong></p>
      <Progress percent={Math.round(issue.confidence * 100)}
                status={issue.confidence > 0.8 ? 'success' : 'normal'} />
      <p>{issue.details}</p>
      <Button type="primary" onClick={() => onAnalyze(issue.id!)}>
        Ask AI to Analyze
      </Button>
    </Card>
  );
};
```

2. **Create `IssuesPage.tsx`:**

```typescript
import { List, Select, Space } from 'antd';
import { useState } from 'react';
import { useIssues } from '../hooks/useMetrics';
import { IssueCard } from '../components/IssueCard';
import { toast } from 'react-toastify';

const IssuesPage: React.FC = () => {
  const { data: issues } = useIssues();
  const [filter, setFilter] = useState<string>('all');

  const filteredIssues = issues?.filter(i =>
    filter === 'all' || i.type === filter
  ) || [];

  const handleAnalyze = async (issueId: number) => {
    toast.info('Sending to AI for analysis...');
    // Call AI diagnosis endpoint
  };

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Select value={filter} onChange={setFilter} style={{ width: 200 }}>
          <Select.Option value="all">All Issues</Select.Option>
          <Select.Option value="MEMORY_LEAK">Memory Leaks</Select.Option>
          <Select.Option value="THREAD_EXPLOSION">Thread Issues</Select.Option>
          <Select.Option value="HUNG_PROCESS">Hung Processes</Select.Option>
        </Select>
      </Space>

      <List
        dataSource={filteredIssues}
        renderItem={issue => (
          <List.Item>
            <IssueCard issue={issue} onAnalyze={handleAnalyze} />
          </List.Item>
        )}
      />
    </div>
  );
};

export default IssuesPage;
```

3. **Add toast notifications:**

```typescript
// Install: npm install react-toastify
import { ToastContainer, toast } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';

// In App.tsx:
export default function App() {
  return (
    <>
      {/* ... existing code ... */}
      <ToastContainer position="top-right" autoClose={5000} />
    </>
  );
}

// Trigger notifications on new issues:
useEffect(() => {
  if (issues) {
    const criticalIssues = issues.filter(i => i.severity === 'CRITICAL');
    criticalIssues.forEach(issue => {
      toast.error(`Critical Issue: ${issue.type} in ${issue.processName}`);
    });
  }
}, [issues]);
```

### Deliverable:

✅ Issues timeline page  
✅ Filter by type/severity  
✅ Confidence meters  
✅ Toast notifications

---

## 📅 DAY 10: Remediation Control Panel

### Tasks:

1. **Create `ActionsPage.tsx`:**

```typescript
import { Table, Tag, Badge } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { actionsApi } from '../api/client';

const ActionsPage: React.FC = () => {
  const { data: actions } = useQuery({
    queryKey: ['actions'],
    queryFn: () => actionsApi.getHistory(24).then(res => res.data),
  });

  const columns = [
    { title: 'Time', dataIndex: 'executedAt',
      render: (val) => new Date(val).toLocaleString() },
    { title: 'Action', dataIndex: 'actionType',
      render: (val) => <Tag>{val.replace('_', ' ')}</Tag> },
    { title: 'Target', dataIndex: 'targetName' },
    { title: 'PID', dataIndex: 'targetPid' },
    { title: 'Status', dataIndex: 'status',
      render: (val) => (
        <Badge status={val === 'SUCCESS' ? 'success' : 'error'} text={val} />
      ) },
    { title: 'Dry Run', dataIndex: 'dryRun',
      render: (val) => val ? '✓' : '✗' },
  ];

  return <Table columns={columns} dataSource={actions} rowKey="id" />;
};

export default ActionsPage;
```

2. **Create manual action controls:**

```typescript
// components/RemediationControls.tsx
import { Button, Modal, Select, Form, Switch } from 'antd';
import { useState } from 'react';

export const RemediationControls: React.FC = () => {
  const [modalVisible, setModalVisible] = useState(false);
  const [form] = Form.useForm();

  const handleExecute = async (values: any) => {
    const confirmed = await Modal.confirm({
      title: 'Confirm Action',
      content: `Execute ${values.action} on PID ${values.pid}?`,
    });

    if (confirmed) {
      // Call backend to execute action
      setModalVisible(false);
    }
  };

  return (
    <>
      <Button type="primary" onClick={() => setModalVisible(true)}>
        Manual Remediation
      </Button>

      <Modal
        title="Execute Remediation Action"
        visible={modalVisible}
        onCancel={() => setModalVisible(false)}
        footer={null}
      >
        <Form form={form} onFinish={handleExecute}>
          <Form.Item name="action" label="Action">
            <Select>
              <Select.Option value="KILL_PROCESS">Kill Process</Select.Option>
              <Select.Option value="REDUCE_PRIORITY">Reduce Priority</Select.Option>
              <Select.Option value="TRIM_WORKING_SET">Trim Memory</Select.Option>
            </Select>
          </Form.Item>

          <Form.Item name="pid" label="Target PID">
            <Input type="number" />
          </Form.Item>

          <Form.Item name="dryRun" label="Dry Run" valuePropName="checked">
            <Switch defaultChecked />
          </Form.Item>

          <Button type="primary" htmlType="submit">Execute</Button>
        </Form>
      </Modal>
    </>
  );
};
```

### Deliverable:

✅ Action history table  
✅ Manual remediation controls  
✅ Confirmation dialogs  
✅ Dry-run toggle

---

## 📅 DAY 11: MCP Server Implementation (Java)

### Tasks:

1. **Create `mcp-server/pom.xml`:**

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>com.github.oshi</groupId>
        <artifactId>oshi-core</artifactId>
    </dependency>
</dependencies>
```

2. **Define MCP tool interface:**

```java
public interface McpTool {
    String getName();
    String getDescription();
    JsonNode execute(JsonNode parameters) throws Exception;
    JsonNode getSchema();
}

@Data
@Builder
public class McpToolResponse {
    private String tool;
    private JsonNode result;
    private boolean success;
    private String error;
    private Instant executedAt;
}
```

3. **Create MCP tools:**

```java
@Component
public class GetProcessListTool implements McpTool {

    private final SystemInfo systemInfo;

    @Override
    public String getName() {
        return "get_process_list";
    }

    @Override
    public String getDescription() {
        return "Get list of all running processes with CPU and memory usage";
    }

    @Override
    public JsonNode execute(JsonNode parameters) {
        int limit = parameters.has("limit") ?
            parameters.get("limit").asInt() : 50;

        List<OSProcess> processes = systemInfo.getOperatingSystem()
            .getProcesses(null, OperatingSystem.ProcessFiltering.ALL_PROCESSES,
                         OperatingSystem.ProcessSorting.CPU, limit);

        ObjectMapper mapper = new ObjectMapper();
        return mapper.valueToTree(processes);
    }

    @Override
    public JsonNode getSchema() {
        return new ObjectMapper().createObjectNode()
            .put("type", "object")
            .set("properties", new ObjectMapper().createObjectNode()
                .set("limit", new ObjectMapper().createObjectNode()
                    .put("type", "integer")
                    .put("default", 50)));
    }
}

@Component
public class GetProcessThreadsTool implements McpTool {
    @Override
    public String getName() { return "get_process_threads"; }

    @Override
    public JsonNode execute(JsonNode parameters) {
        int pid = parameters.get("pid").asInt();
        // Use JNA to get thread details
        List<ThreadInfo> threads = WindowsNativeUtils.getThreads(pid);
        return new ObjectMapper().valueToTree(threads);
    }
}

// Additional tools:
// - KillProcessTool
// - GetIOStatsTool
// - ReadEventLogTool
// - GetPerformanceCounterTool
```

4. **Create MCP server:**

```java
@RestController
@RequestMapping("/mcp/tools")  @RequiredArgsConstructor
public class McpController {

    private final Map<String, McpTool> tools;

    @GetMapping
    public ResponseEntity<List<McpToolInfo>> listTools() {
        List<McpToolInfo> toolInfo = tools.values().stream()
            .map(t -> McpToolInfo.builder()
                .name(t.getName())
                .description(t.getDescription())
                .schema(t.getSchema())
                .build())
            .collect(Collectors.toList());

        return ResponseEntity.ok(toolInfo);
    }

    @PostMapping("/{toolName}")
    public ResponseEntity<McpToolResponse> executeTool(
            @PathVariable String toolName,
            @RequestBody JsonNode parameters) {

        McpTool tool = tools.get(toolName);
        if (tool == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            JsonNode result = tool.execute(parameters);
            return ResponseEntity.ok(McpToolResponse.builder()
                .tool(toolName)
                .result(result)
                .success(true)
                .executedAt(Instant.now())
                .build());
        } catch (Exception e) {
            return ResponseEntity.ok(McpToolResponse.builder()
                .tool(toolName)
                .success(false)
                .error(e.getMessage())
                .executedAt(Instant.now())
                .build());
        }
    }
}

@SpringBootApplication
public class McpServerApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(McpServerApplication.class);
        app.setDefaultProperties(Map.of("server.port", "8081"));
        app.run(args);
    }
}
```

5. **Add authentication:**

```java
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    @Value("${mcp.api.key}")
    private String apiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {
        String requestApiKey = request.getHeader("X-API-Key");

        if (!apiKey.equals(requestApiKey)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
```

### Deliverable:

✅ MCP server on port 8081  
✅ 7+ standardized tools  
✅ API key authentication  
✅ JSON schema for each tool

---

## 📅 DAY 12: LangChain4j AI Agents Setup

### Tasks:

1. **Add LangChain4j dependencies:**

```xml
<dependencies>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j</artifactId>
        <version>0.28.0</version>
    </dependency>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-open-ai</artifactId>
        <version>0.28.0</version>
    </dependency>
</dependencies>
```

2. **Define AI agents:**

```java
@Service
public class LeakDetectorAgent {

    private final ChatLanguageModel model;
    private final McpToolService mcpTools;

    public LeakDetectorAgent(@Value("${openai.api.key}") String apiKey) {
        this.model = OpenAiChatModel.builder()
            .apiKey(apiKey)
            .modelName("gpt-4")
            .temperature(0.3)
            .build();
    }

    public AiAnalysisResult analyzeMemoryLeak(DiagnosticIssue issue) {
        // Get process details via MCP
        ProcessInfo process = mcpTools.getProcessInfo(issue.getAffectedPid());
        List<ThreadInfo> threads = mcpTools.getThreads(issue.getAffectedPid());

        String prompt = String.format("""
            You are a memory leak detection expert. Analyze this process:

            Process: %s (PID: %d)
            Memory Usage: %d MB
            Thread Count: %d
            Evidence: %s

            Provide:
            1. Root cause analysis
            2. Confidence level (0-1)
            3. Recommended action
            4. Reasoning
            """,
            process.getName(),
            process.getPid(),
            process.getMemoryBytes() / 1024 / 1024,
            threads.size(),
            issue.getDetails()
        );

        AiMessage response = model.generate(prompt);

        return parseResponse(response.text());
    }
}

@Service
public class ThreadExpertAgent {

    private final ChatLanguageModel model;

    public AiAnalysisResult analyzeThreadBehavior(DiagnosticIssue issue) {
        // Similar structure, focused on thread analysis
        String prompt = """
            You are a thread behavior expert. Analyze thread patterns and identify:
            - Thread explosion causes
            - Deadlocks
            - Thread pool exhaustion
            """;

        // ... implementation
    }
}

@Service
public class IOAnalystAgent {
    // Analyzes disk/network I/O bottlenecks
}

@Service
public class RemediationPlannerAgent {
    // Creates safe remediation plans
}

@Service
public class SafetyValidatorAgent {
    // Validates actions against safety policies
}
```

3. **Create agent orchestrator (multi-agent workflow):**

```java
@Service
@RequiredArgsConstructor
public class AiDiagnosticOrchestrator {

    private final LeakDetectorAgent leakDetector;
    private final ThreadExpertAgent threadExpert;
    private final IOAnalystAgent ioAnalyst;
    private final RemediationPlannerAgent remediationPlanner;
    private final SafetyValidatorAgent safetyValidator;

    public CompleteDiagnosisReport diagnose(DiagnosticIssue issue) {
        // Step 1: Specialized agent analyzes based on issue type
        AiAnalysisResult analysis = switch (issue.getType()) {
            case MEMORY_LEAK -> leakDetector.analyzeMemoryLeak(issue);
            case THREAD_EXPLOSION -> threadExpert.analyzeThreadBehavior(issue);
            case IO_BOTTLENECK -> ioAnalyst.analyzeIO(issue);
            default -> null;
        };

        if (analysis == null || analysis.getConfidence() < 0.5) {
            return CompleteDiagnosisReport.builder()
                .success(false)
                .message("Unable to diagnose with confidence")
                .build();
        }

        // Step 2: Remediation planner creates action plan
        RemediationPlan plan = remediationPlanner.createPlan(analysis);

        // Step 3: Safety validator validates plan
        SafetyValidation validation = safetyValidator.validate(plan);

        if (!validation.isSafe()) {
            plan.setApprovalRequired(true);
            plan.setWarnings(validation.getWarnings());
        }

        return CompleteDiagnosisReport.builder()
            .success(true)
            .analysis(analysis)
            .remediationPlan(plan)
            .safetyValidation(validation)
            .confidence(analysis.getConfidence())
            .timestamp(Instant.now())
            .build();
    }
}
```

4. **Create MCP tool wrappers for LangChain4j:**

```java
@Component
public class McpToolService {

    private final WebClient mcpClient;

    public McpToolService(@Value("${mcp.server.url}") String mcpUrl) {
        this.mcpClient = WebClient.builder()
            .baseUrl(mcpUrl)
            .defaultHeader("X-API-Key", "${mcp.api.key}")
            .build();
    }

    public ProcessInfo getProcessInfo(int pid) {
        return mcpClient.post()
            .uri("/mcp/tools/get_process_info")
            .bodyValue(Map.of("pid", pid))
            .retrieve()
            .bodyToMono(ProcessInfo.class)
            .block();
    }

    public List<ThreadInfo> getThreads(int pid) {
        McpToolResponse response = mcpClient.post()
            .uri("/mcp/tools/get_process_threads")
            .bodyValue(Map.of("pid", pid))
            .retrieve()
            .bodyToMono(McpToolResponse.class)
            .block();

        ObjectMapper mapper = new ObjectMapper();
        return mapper.convertValue(response.getResult(),
            new TypeReference<List<ThreadInfo>>() {});
    }
}
```

### Deliverable:

✅ 5 LangChain4j AI agents defined  
✅ Multi-agent orchestration workflow  
✅ MCP tools integrated as agent tools  
✅ OpenAI GPT-4 integration

---

## 📅 DAY 13: AI Diagnosis Integration

### Tasks:

1. **Create diagnosis endpoint in backend:**

```java
@RestController
@RequestMapping("/api/diagnose")
@RequiredArgsConstructor
public class DiagnosisController {

    private final AiDiagnosticOrchestrator orchestrator;
    private final IssueRepository issueRepository;
    private final DiagnosisRepository diagnosisRepository;

    @PostMapping("/{issueId}")
    public ResponseEntity<CompleteDiagnosisReport> diagnoseIssue(
            @PathVariable Long issueId) {

        IssueEntity issue = issueRepository.findById(issueId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // Convert to DiagnosticIssue DTO
        DiagnosticIssue issueDto = toDto(issue);

        // Run AI diagnosis
        CompleteDiagnosisReport report = orchestrator.diagnose(issueDto);

        // Save report
        DiagnosisEntity entity = diagnosisRepository.save(toDiagnosisEntity(report, issue));

        return ResponseEntity.ok(report);
    }

    @PostMapping("/process/{pid}")
    public ResponseEntity<CompleteDiagnosisReport> diagnoseProcess(
            @PathVariable int pid) {

        // Create synthetic issue for the process
        DiagnosticIssue syntheticIssue = DiagnosticIssue.builder()
            .affectedPid(pid)
            .type(IssueType.UNKNOWN)
            .severity(Severity.LOW)
            .confidence(0.0)
            .detectedAt(Instant.now())
            .build();

        CompleteDiagnosisReport report = orchestrator.diagnose(syntheticIssue);

        return ResponseEntity.ok(report);
    }

    @GetMapping("/history")
    public ResponseEntity<List<DiagnosisEntity>> getHistory() {
        return ResponseEntity.ok(diagnosisRepository.findAll());
    }
}
```

2. **Auto-trigger AI for low-confidence detections:**

```java
@Service
@RequiredArgsConstructor
public class AiAutoTriggerService {

    private final AiDiagnosticOrchestrator orchestrator;
    private final IssueRepository issueRepository;

    @EventListener
    public void onIssueDetected(IssueDetectedEvent event) {
        DiagnosticIssue issue = event.getIssue();

        // If confidence < 0.6, ask AI for help
        if (issue.getConfidence() < 0.6) {
            log.info("Low confidence detection, triggering AI analysis for PID {}",
                issue.getAffectedPid());

            CompleteDiagnosisReport aiReport = orchestrator.diagnose(issue);

            // Update issue with AI findings
            if (aiReport.getConfidence() > issue.getConfidence()) {
                issueRepository.updateConfidence(issue.getId(),
                    aiReport.getConfidence());
            }
        }
    }
}
```

3. **Create CLI for testing:**

```java
@Component
@RequiredArgsConstructor
public class DiagnosisCli implements CommandLineRunner {

    private final AiDiagnosticOrchestrator orchestrator;

    @Override
    public void run(String... args) {
        if (args.length > 0 && args[0].equals("diagnose")) {
            int pid = Integer.parseInt(args[1]);

            DiagnosticIssue issue = DiagnosticIssue.builder()
                .affectedPid(pid)
                .type(IssueType.UNKNOWN)
                .build();

            CompleteDiagnosisReport report = orchestrator.diagnose(issue);

            System.out.println("═══════════════════════════════════════");
            System.out.println("AI DIAGNOSIS REPORT");
            System.out.println("═══════════════════════════════════════");
            System.out.println("Confidence: " + report.getConfidence());
            System.out.println("Root Cause: " + report.getAnalysis().getRootCause());
            System.out.println("Recommendation: " + report.getAnalysis().getRecommendation());
            System.out.println("═══════════════════════════════════════");
        }
    }
}
```

### Deliverable:

✅ `/api/diagnose/{issueId}` endpoint  
✅ Auto-trigger AI for low confidence (<0.6)  
✅ CLI tool for testing  
✅ AI results saved to database

---

## 📅 DAY 14: AI Results in Frontend

### Tasks:

1. **Create `AIAnalysisCard.tsx`:**

```typescript
import { Card, Descriptions, Progress, Alert, Tag } from 'antd';
import { BulbOutlined } from '@ant-design/icons';

interface Props {
  analysis: AiAnalysisResult;
}

export const AIAnalysisCard: React.FC<Props> = ({ analysis }) => {
  return (
    <Card
      title={<span><BulbOutlined /> AI Analysis</span>}
      extra={<Tag color="blue">GPT-4</Tag>}
    >
      <Alert
        message="AI Diagnosis"
        description={analysis.rootCause}
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
      />

      <Descriptions column={1}>
        <Descriptions.Item label="Confidence">
          <Progress percent={Math.round(analysis.confidence * 100)} />
        </Descriptions.Item>
        <Descriptions.Item label="Recommendation">
          {analysis.recommendation}
        </Descriptions.Item>
        <Descriptions.Item label="Reasoning">
          {analysis.reasoning}
        </Descriptions.Item>
        <Descriptions.Item label="Agent">
          {analysis.agentName}
        </Descriptions.Item>
      </Descriptions>
    </Card>
  );
};
```

2. **Add "Ask AI" functionality:**

```typescript
// hooks/useDiagnosis.ts
import { useMutation } from '@tanstack/react-query';
import axios from 'axios';

export const useDiagnosis = () => {
  return useMutation({
    mutationFn: (issueId: number) =>
      axios.post(`/api/diagnose/${issueId}`).then(res => res.data),
  });
};

// In IssueCard.tsx:
const { mutate: diagnose, data: aiReport, isLoading } = useDiagnosis();

<Button
  type="primary"
  onClick={() => diagnose(issue.id)}
  loading={isLoading}
>
  Ask AI to Analyze
</Button>

{aiReport && <AIAnalysisCard analysis={aiReport.analysis} />}
```

3. **Create AI analysis history page:**

```typescript
// pages/AIHistoryPage.tsx
import { Timeline, Card } from 'antd';
import { useQuery } from '@tanstack/react-query';

const AIHistoryPage: React.FC = () => {
  const { data: diagnoses } = useQuery({
    queryKey: ['diagnoses'],
    queryFn: () => axios.get('/api/diagnose/history').then(res => res.data),
  });

  return (
    <Timeline>
      {diagnoses?.map(d => (
        <Timeline.Item key={d.id} color={d.confidence > 0.8 ? 'green' : 'blue'}>
          <Card size="small">
            <p><strong>{d.analysis.agentName}</strong> analyzed {d.processName}</p>
            <p>Confidence: {(d.confidence * 100).toFixed(0)}%</p>
            <p>{d.analysis.recommendation}</p>
          </Card>
        </Timeline.Item>
      ))}
    </Timeline>
  );
};
```

4. **Add AI confidence to remediation flow:**

```typescript
// components/RemediationPlanner.tsx
{issue.aiConfidence > 0.8 && (
  <Alert
    message="AI recommends auto-approval"
    description="High confidence diagnosis suggests safe remediation"
    type="success"
    showIcon
  />
)}

<Button
  type="primary"
  disabled={issue.aiConfidence < 0.7}
>
  {issue.aiConfidence > 0.8 ? 'Auto Execute' : 'Requires Approval'}
</Button>
```

### Deliverable:

✅ AI analysis card component  
✅ "Ask AI" button on issues  
✅ AI history timeline  
✅ Auto-approval based on AI confidence

---

## 📅 DAY 15: Safety & Policy System

### Tasks:

1. **Create policy entities:**

```java
@Entity
@Table(name = "safety_policies")
@Data
public class SafetyPolicy {
    @Id
    @GeneratedValue
    private Long id;

    @Enumerated(EnumType.STRING)
    private ActionType actionType;

    @Enumerated(EnumType.STRING)
    private SafetyLevel safetyLevel;

    private Boolean requiresApproval;
    private Boolean allowedInDryRun;
    private Boolean allowedInProduction;

    @ElementCollection
    private List<String> protectedProcessPatterns;
}

@Service
@RequiredArgsConstructor
public class SafetyPolicyService {

    private final SafetyPolicyRepository policyRepository;
    private final AgentConfiguration config;

    public PolicyViolation checkPolicy(RemediationContext context) {
        // Check protected processes
        if (config.getProtectedProcesses().contains(context.getProcessName())) {
            return PolicyViolation.builder()
                .violated(true)
                .reason("Process is protected")
                .severity(ViolationSeverity.CRITICAL)
                .build();
        }

        // Check action safety level
        SafetyPolicy policy = policyRepository
            .findByActionType(context.getActionType());

        if (!context.isDryRun() && policy.getRequiresApproval()) {
            return PolicyViolation.builder()
                .violated(true)
                .reason("Action requires user approval")
                .severity(ViolationSeverity.HIGH)
                .build();
        }

        return PolicyViolation.builder()
            .violated(false)
            .build();
    }
}
```

2. **Enhance remediation engine with policy checking:**

```java
@Service
public class RemediationEngine {

    public ActionResult executeRemediation(DiagnosticIssue issue) {
        RemediationContext context = buildContext(issue);

        // Check policy
        PolicyViolation violation = safetyPolicyService.checkPolicy(context);

        if (violation.isViolated()) {
            log.warn("Policy violation: {}", violation.getReason());

            return ActionResult.builder()
                .success(false)
                .message("Blocked by policy: " + violation.getReason())
                .policyViolation(violation)
                .build();
        }

        // Execute action
        return action.execute(context);
    }
}
```

3. **Create settings page:**

```typescript
// pages/SettingsPage.tsx
import { Form, Switch, Input, Button, List, Tag } from 'antd';

const SettingsPage: React.FC = () => {
  const [form] = Form.useForm();

  const handleSave = async (values: any) => {
    await axios.post('/api/settings', values);
    message.success('Settings saved');
  };

  return (
    <Form form={form} onFinish={handleSave} layout="vertical">
      <Form.Item name="dryRunMode" label="Dry Run Mode" valuePropName="checked">
        <Switch />
      </Form.Item>

      <Form.Item name="autoRemediateHighConfidence" label="Auto-remediate (AI confidence > 80%)">
        <Switch />
      </Form.Item>

      <Form.Item label="Protected Processes">
        <List
          dataSource={protectedProcesses}
          renderItem={proc => (
            <List.Item>
              <span>{proc}</span>
              <Tag color="red">PROTECTED</Tag>
            </List.Item>
          )}
        />
      </Form.Item>

      <Button type="primary" htmlType="submit">Save Settings</Button>
    </Form>
  );
};
```

### Deliverable:

✅ Safety policy database  
✅ Policy enforcement before actions  
✅ Protected processes list  
✅ Settings page in UI  
✅ Policy violation logging

---

## 📅 DAY 16: Logging & Monitoring

### Tasks:

1. **Add SLF4J + Logback configuration:**

```xml
<!-- logback-spring.xml -->
<configuration>
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/aios-monitor.log</file>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder" />
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/aios-monitor.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
    </appender>

    <root level="INFO">
        <appender-ref ref="FILE" />
    </root>
</configuration>
```

2. **Create health check endpoint:**

```java
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;
    private final WebClient mcpClient;
    private final WebClient agentClient;

    @GetMapping
    public ResponseEntity<HealthStatus> getHealth() {
        HealthStatus status = HealthStatus.builder()
            .status("UP")
            .timestamp(Instant.now())
            .checks(Map.of(
                "database", checkDatabase(),
                "mcp-server", checkMcpServer(),
                "agent", checkAgent()
            ))
            .build();

        return ResponseEntity.ok(status);
    }

    private HealthCheck checkDatabase() {
        try {
            dataSource.getConnection().close();
            return HealthCheck.builder().status("UP").build();
        } catch (Exception e) {
            return Health Check.builder()
                .status("DOWN")
                .error(e.getMessage())
                .build();
        }
    }
}
```

3. **Create log viewer in frontend:**

```typescript
// pages/LogsPage.tsx
import { Table, Select, Input } from 'antd';
import { useState } from 'react';

const LogsPage: React.FC = () => {
  const [level, setLevel] = useState('ALL');
  const [search, setSearch] = useState('');

  const { data: logs } = useQuery({
    queryKey: ['logs', level, search],
    queryFn: () => axios.get(`/api/logs?level=${level}&search=${search}`)
      .then(res => res.data),
  });

  const columns = [
    { title: 'Time', dataIndex: 'timestamp',
      render: (val) => new Date(val).toLocaleString() },
    { title: 'Level', dataIndex: 'level',
      render: (val) => <Tag color={getLevelColor(val)}>{val}</Tag> },
    { title: 'Component', dataIndex: 'logger' },
    { title: 'Message', dataIndex: 'message' },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Select value={level} onChange={setLevel} style={{ width: 120 }}>
          <Select.Option value="ALL">All Levels</Select.Option>
          <Select.Option value="ERROR">Error</Select.Option>
          <Select.Option value="WARN">Warning</Select.Option>
          <Select.Option value="INFO">Info</Select.Option>
        </Select>

        <Input.Search
          placeholder="Search logs"
          value={search}
          onChange={e => setSearch(e.target.value)}
          style={{ width: 300 }}
        />
      </Space>

      <Table columns={columns} dataSource={logs} />
    </div>
  );
};
```

4. **Add performance metrics (Micrometer):**

```java
@Configuration
public class MetricsConfig {

    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}

@Service
public class PerformanceMetrics {

    private final MeterRegistry registry;

    public void recordDetectionLatency(Duration duration) {
        registry.timer("detection.latency").record(duration);
    }

    public void recordRemediationSuccess() {
        registry.counter("remediation.success").increment();
    }
}
```

### Deliverable:

✅ Structured JSON logging  
✅ Health check endpoint  
✅ Log viewer in UI  
✅ Performance metrics (Micrometer)

---

## 📅 DAY 17: Testing & Edge Cases

### Tasks:

1. **Create test scenarios:**

```java
@Service
public class FailureSimulator {

    public void simulateMemoryLeak() {
        List<byte[]> leak = new ArrayList<>();
        new Thread(() -> {
            while (true) {
                leak.add(new byte[1024 * 1024]); // 1 MB per second
                Thread.sleep(1000);
            }
        }).start();
    }

    public void simulateThreadExplosion() {
        for (int i = 0; i < 1000; i++) {
            new Thread(() -> {
                while (true) {
                    try { Thread.sleep(10000); } catch (Exception e) {}
                }
            }).start();
        }
    }

    public void simulateHungProcess() {
        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        while (true) {
            // Busy wait, consuming handles but no CPU
        }
    }
}
```

2. **Write unit tests:**

```java
@SpringBootTest
class DetectorTests {

    @Autowired
    private MemoryLeakDetector leakDetector;

    @Test
    void shouldDetectMemoryLeak() {
        List<MetricSnapshot> metrics = generateLeakMetrics();
        List<DiagnosticIssue> issues = leakDetector.detect(metrics, processes);

        assertThat(issues).isNotEmpty();
        assertThat(issues.get(0).getType()).isEqualTo(IssueType.MEMORY_LEAK);
        assertThat(issues.get(0).getConfidence()).isGreaterThan(0.7);
    }
}

@SpringBootTest
class RemediationEngineTests {

    @Test
    void shouldBlockProtectedProcess() {
        RemediationContext context = RemediationContext.builder()
            .processName("csrss.exe")
            .build();

        PolicyViolation violation = safetyPolicyService.checkPolicy(context);

        assertThat(violation.isViolated()).isTrue();
    }
}
```

3. **Test failure modes:**

```java
@Test
void shouldQueueWhenBackendDown() {
    // Stop backend
    // Generate metrics
    // Verify metrics queued locally
    // Restart backend
    // Verify metrics synced
}

@Test
void shouldFallbackToRuleBasedWhenAIDown() {
    // Mock OpenAI API failure
    // Trigger detection
    // Verify rule-based detection still works
}
```

### Deliverable:

✅ Failure simulators for testing  
✅ Unit tests for detectors  
✅ Integration tests for remediation  
✅ Edge case tests (offline, API down)

---

## 📅 DAY 18: Windows Integration

### Tasks:

1. **Create Windows service wrapper:**

```java
// Use Apache Commons Daemon (procrun) to create Windows service
// pom.xml
<dependency>
    <groupId>commons-daemon</groupId>
    <artifactId>commons-daemon</artifactId>
    <version>1.3.4</version>
</dependency>

// WindowsService.java
public class WindowsService {

    public static void main(String[] args) throws Exception {
        if ("start".equals(args[0])) {
            start(args);
        } else if ("stop".equals(args[0])) {
            stop(args);
        }
    }

    public static void start(String[] args) {
        AgentApplication.main(args);
    }

    public static void stop(String[] args) {
        // Graceful shutdown
    }
}

// Install service:
// sc create "AIOS Monitor" binPath= "path\to\aios-agent.exe"
```

2. **Create system tray icon (using AWT):**

```java
@Service
public class SystemTrayService {

    @PostConstruct
    public void initSystemTray() {
        if (!SystemTray.isSupported()) {
            return;
        }

        PopupMenu popup = new PopupMenu();

        MenuItem openDashboard = new MenuItem("Open Dashboard");
        openDashboard.addActionListener(e ->
            openBrowser("http://localhost:3000"));

        MenuItem pauseMonitoring = new MenuItem("Pause Monitoring");
        pauseMonitoring.addActionListener(e -> pauseAgent());

        MenuItem exit = new MenuItem("Exit");
        exit.addActionListener(e -> System.exit(0));

        popup.add(openDashboard);
        popup.add(pauseMonitoring);
        popup.addSeparator();
        popup.add(exit);

        Image image = Toolkit.getDefaultToolkit()
            .getImage("icon.png");

        TrayIcon trayIcon = new TrayIcon(image, "AIOS Monitor", popup);
        trayIcon.setImageAutoSize(true);

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            log.error("Could not add system tray icon", e);
        }
    }
}
```

3 **Add Windows toast notifications (using JNA):**

```java
public class WindowsToastNotification {

    public static void showNotification(String title, String message) {
        if (!System.getProperty("os.name").contains("Windows")) {
            return;
        }

        try {
            Runtime.getRuntime().exec(new String[]{
                "powershell",
                "-Command",
                String.format(
                    "[Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null;" +
                    "$template = [Windows.UI.Notifications.ToastNotificationManager]::GetTemplateContent([Windows.UI.Notifications.ToastTemplateType]::ToastText02);" +
                    "$text = $template.GetElementsByTagName('text');" +
                    "$text[0].AppendChild($template.CreateTextNode('%s')) | Out-Null;" +
                    "$text[1].AppendChild($template.CreateTextNode('%s')) | Out-Null;" +
                    "$toast = [Windows.UI.Notifications.ToastNotification]::new($template);" +
                    "[Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('AIOS Monitor').Show($toast)",
                    title, message
                )
            });
        } catch (IOException e) {
            log.error("Failed to show toast notification", e);
        }
    }
}

@Service
public class NotificationService {

    @EventListener
    public void onCriticalIssue(IssueDetectedEvent event) {
        if (event.getIssue().getSeverity() == Severity.CRITICAL) {
            WindowsToastNotification.showNotification(
                "Critical Issue Detected",
                String.format("%s in %s (PID: %d)",
                    event.getIssue().getType(),
                    event.getIssue().getProcessName(),
                    event.getIssue().getAffectedPid())
            );
        }
    }
}
```

4. **Create installer (using jpackage - Java 14+):**

```bash
# Build executable
jpackage \
  --input target/ \
  --name "AIOS Monitor" \
  --main-jar aios-agent.jar \
  --main-class com.aios.agent.AgentApplication \
  --type msi \
  --icon icon.ico \
  --win-menu \
  --win-shortcut
```

### Deliverable:

✅ Windows service installation  
✅ System tray icon with menu  
✅ Windows toast notifications  
✅ MSI installer

---

## 📅 DAY 19: Documentation

### Tasks:

1. **Update README.md:**

```markdown
# 🤖 AIOS Monitor - AI-Powered Windows System Monitor

## Overview

AIOS Monitor is an intelligent Windows monitoring system that uses AI agents to detect and remediate system issues automatically.

## Architecture
```

[React Dashboard] ← HTTP/WebSocket → [Spring Boot Backend]
↓
[PostgreSQL]
↓ ↓
[Java Agent (OSHI)] → Metrics → [LangChain4j AI Agents]
↓ ↓
[MCP Server] ← Tools ← [OpenAI GPT-4]

````

## Features
- ✅ Real-time system monitoring (CPU, memory, disk, network)
- ✅ 5 AI-powered issue detectors (memory leaks, thread explosions, hung processes, I/O bottlenecks, resource hogs)
- ✅ Multi-agent AI diagnosis (LangChain4j + GPT-4)
- ✅ 8+ remediation actions (kill process, reduce priority, trim memory, etc.)
- ✅ Safety system (dry-run mode, protected processes, policy enforcement)
- ✅ React dashboard with real-time charts
- ✅ Windows service + system tray integration
- ✅ Toast notifications for critical issues

## Installation

### Prerequisites
- Java 17+
- Node.js 18+
- PostgreSQL 14+
- OpenAI API key

### Backend Setup
```bash
cd backend
mvn clean install
mvn spring-boot:run
````

### Agent Setup

```bash
cd agent
mvn clean install
java -jar target/aios-agent.jar
```

### Frontend Setup

```bash
cd frontend
npm install
npm run dev
```

### Configuration

Create `application.properties`:

```properties
openai.api.key=sk-...
spring.datasource.url=jdbc:postgresql://localhost:5432/aios
agent.dry-run-mode=true
```

## Usage

1. Start backend: `java -jar backend.jar`
2. Start agent: `java -jar agent.jar`
3. Start frontend: `npm run dev`
4. Open dashboard: http://localhost:3000

## Safety

AIOS Monitor includes comprehensive safety features:

- **Dry-run mode**: Test actions without executing them
- **Protected processes**: System-critical processes cannot be terminated
- **Policy enforcement**: Actions require approval based on safety level
- **AI validation**: Multi-agent validation before remediation

## API Documentation

See [API.md](API.md) for complete API reference.

## Contributing

Pull requests welcome! See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

MIT License

````

2. **Create ARCHITECTURE .md:**
```markdown
# Architecture

## Component Diagram

### Java Agent
- **SystemMetricsCollector**: Collects CPU/memory/disk/network every 10s
- **ProcessInfoCollector**: Monitors top processes
- **DetectorManager**: Runs 5 issue detectors every 30s
- **RemediationEngine**: Executes actions based on rules
- **BackendClient**: Syncs data to backend

### Spring Boot Backend
- **REST API**: CRUD operations for metrics/issues/actions
- **WebSocket**: Real-time updates to frontend
- **JPA Repositories**: PostgreSQL persistence
- **AI Orchestrator**: Coordinates LangChain4j agents

### LangChain4j AI Agents
- **LeakDetectorAgent**: Memory leak analysis
- **ThreadExpertAgent**: Thread behavior analysis
- **IOAnalystAgent**: I/O bottleneck analysis
- **RemediationPlannerAgent**: Creates action plans
- **SafetyValidatorAgent**: Validates safety

### MCP Server
- Standardized tools for LLM agents
- 7+ tools (process list, threads, kill, I/O stats, event logs, etc.)

### React Frontend
- **Dashboard**: Real-time metrics + charts
- **Issues Page**: Issue timeline + AI analysis
- **Actions Page**: Remediation history
- **Settings Page**: Configure policies

## Data Flow

1. Agent collects metrics → sends to backend every 30s
2. Detectors analyze metrics → create issues
3. Issues synced to backend → stored in PostgreSQL
4. Frontend polls backend → displays on dashboard
5. User clicks "Ask AI" → triggers AI diagnosis
6. AI agents call MCP tools → analyze process
7. AI returns diagnosis + remediation plan
8. User approves → action executed → result logged
````

3. **Create AI_AGENTS.md:**

```markdown
# AI Agents (LangChain4j)

## Agent Architecture

AIOS Monitor uses a multi-agent system powered by LangChain4j and OpenAI GPT-4.

### Specialized Agents

#### 1. Leak DetectorAgent

- **Role**: Memory leak expert
- **Tools**: get_process_info, get_process_threads, get_memory_stats
- **Output**: Root cause, confidence, recommended action

#### 2. ThreadExpertAgent

- **Role**: Thread behavior analyst
- **Tools**: get_process_threads, get_cpu_usage, get_stack_traces
- **Output**: Thread patterns, deadlock detection, recommendations

#### 3. IOAnalystAgent

- **Role**: I/O bottleneck specialist
- **Tools**: get_io_stats, get_disk_queue, get_network_connections
- **Output**: I/O bottleneck analysis, optimization suggestions

#### 4. RemediationPlannerAgent

- **Role**: Action planner
- **Input**: Diagnosis from specialized agents
- **Output**: Step-by-step remediation plan with safety checks

#### 5. SafetyValidatorAgent

- **Role**: Safety validator
- **Input**: Remediation plan
- **Output**: Safety validation, warnings, approval requirements

## Workflow
```

Issue Detected (confidence < 0.6)
↓
Route to Specialized Agent
↓
Agent calls MCP tools for data
↓
LLM (GPT-4) analyzes data
↓
Returns diagnosis + confidence
↓
RemediationPlanner creates plan
↓
SafetyValidator checks plan
↓
Execute (if safe) or Request Approval

````

## Example Diagnosis

```json
{
  "confidence": 0.92,
  "rootCause": "Memory leak in Java heap - ArrayList growing unbounded",
  "recommendation": "Terminate process PID 1234",
  "reasoning": "Process has consumed 2GB over 10 minutes with linear growth (R²=0.98). No disk I/O or network activity indicates memory accumulation without release.",
  "agentName": "LeakDetectorAgent",
  "evidence": {
    "memoryGrowthRate": "3.2 MB/s",
    "trend": "linear",
    "rSquared": 0.98
  }
}
````

````

4. **Create SAFETY.md:**
```markdown
# Safety System

## Safety Levels

| Level | Description | Requires Approval | Examples |
|-------|-------------|-------------------|----------|
| LOW | Minimal risk | No | Reduce priority, trim memory |
| MEDIUM | Moderate risk | Dry-run only | Clear temp files |
| HIGH | Significant risk | Yes | Kill user process |
| CRITICAL | System risk | Admin approval | Restart service, registry edit |

## Protected Processes

The following processes CANNOT be terminated:

- System
- csrss.exe
- lsass.exe
- winlogon.exe
- services.exe
- smss.exe
- svchost.exe

## Policy Rules

1. **Dry-run mode**: All actions simulated, nothing executed
2. **Protected check**: Actions blocked on system-critical processes
3. **Approval workflow**: High/critical actions require user confirmation
4. **Logging**: All actions logged, even dry-run
5. **AI validation**: Multi-agent safety check before execution

## Configuration

Edit in Settings Page or `application.properties`:

```properties
agent.dry-run-mode=true
agent.protected-processes=System,csrss.exe,lsass.exe
agent.auto-remediate-confidence-threshold=0.8
````

````

### Deliverable:
✅ Comprehensive README.md
✅ ARCHITECTURE.md with diagrams
✅ AI_AGENTS.md explaining agent system
✅ SAFETY.md documenting policies
✅ Code comments and JavaDoc

---

## 📅 DAY 20: Polish & Demo Prep

### Tasks:

1. **UI polish:**
- Consistent Ant Design theme
- Loading skeletons for data fetching
- Error boundaries for graceful errors
- Responsive design (works on tablets)
- Dark mode support

2. **Add dashboard widgets:**
```java
@RestController
@RequestMapping("/api/dashboard/stats")
public class DashboardStatsController {

    @GetMapping("/issues-resolved-today")
    public ResponseEntity<Long> getIssuesResolvedToday() {
        Instant startOfDay = LocalDate.now().atStartOfDay()
            .atZone(ZoneId.systemDefault()).toInstant();

        long count = issueRepository.countByResolvedTrueAndResolvedAtAfter(startOfDay);
        return ResponseEntity.ok(count);
    }

    @GetMapping("/system-health-score")
    public ResponseEntity<Integer> getHealthScore() {
        // Calculate 0-100 score based on:
        // - Active issues (weighted by severity)
        // - CPU/memory usage
        // - Recent remediation success rate

        int score = calculateHealthScore();
        return ResponseEntity.ok(score);
    }

    @GetMapping("/time-saved")
    public ResponseEntity<Duration> getTimeSaved() {
        // Estimate time saved vs manual troubleshooting
        // Assume each auto-remediated issue saves 30 minutes

        long resolvedCount = issueRepository.countByResolvedTrue();
        Duration timeSaved = Duration.ofMinutes(resolvedCount * 30);

        return ResponseEntity.ok(timeSaved);
    }
}
````

3. **Create demo script:**

```markdown
# Demo Script

## Part 1: Normal Monitoring (1 min)

1. Start agent + backend + frontend
2. Show dashboard with live metrics
3. Point out process table, charts updating in real-time

## Part 2: Trigger Memory Leak (1 min)

1. Run failure simulator: `java -jar simulator.jar --memory-leak`
2. Watch metrics spike on dashboard
3. Wait 30 seconds for detection

## Part 3: AI Detection (1 min)

1. Issue appears on Issues page
2. Click "Ask AI to Analyze"
3. Show AI analysis card with confidence + recommendation

## Part 4: Dry-Run Remediation (1 min)

1. Show recommended action: "Kill Process"
2. Verify dry-run mode is ON
3. Click "Execute Fix"
4. Show "[DRY RUN] Would terminate process..." message

## Part 5: Execute Fix (1 min)

1. Toggle dry-run mode OFF
2. Execute action again
3. Show success message
4. Verify issue resolved on dashboard
5. Show action in Actions History

## Part 6: Highlight Features (30sec)

- Multi-agent AI system (LangChain4j + GPT-4)
- MCP standardized tools
- Safety system (protected processes)
- Real-time WebSocket updates
- Windows service + system tray
```

4. **Record demo video:**

- Use OBS Studio or similar
- 3-5 minute walkthrough
- Narrate the problem → solution → results
- Show architecture diagram
- Highlight unique features (AI agents, MCP, safety)

5. **Create presentation slides:**

```
Slide 1: Title
- AIOS Monitor: AI-Powered Windows System Monitor
- Java Fullstack + Multi-Agent AI

Slide 2: Problem
- Windows systems suffer from memory leaks, hung processes, resource hogs
- Manual troubleshooting is time-consuming
- Existing tools lack AI-powered diagnosis

Slide 3: Solution
- Automated monitoring with OSHI
- AI-powered diagnosis (LangChain4j + GPT-4)
- Safe remediation with policy enforcement
- Real-time dashboard

Slide 4: Architecture
- [Include architecture diagram]
- Java Agent → Spring Boot Backend → React Frontend
- MCP Server for standardized AI tools
- Multi-agent AI system

Slide 5: Key Features
- 5 AI-powered detectors
- Multi-agent diagnosis
- 8+ remediation actions
- Safety system (dry-run, protected processes)
- Real-time dashboard with WebSocket

Slide 6: Demo
- [Include screenshots]
- Memory leak detection
- AI analysis with 92% confidence
- Dry-run remediation
- Successful fix

Slide 7: Technical Stack
- Java 17 + Spring Boot  + OSHI + JNA
- LangChain4j + OpenAI GPT-4
- React + TypeScript + Ant Design
- PostgreSQL + JPA
- WebSocket for real-time

Slide 8: Results & Impact
- Auto-remediates issues in < 60 seconds
- 92%+ AI confidence on diagnoses
- Zero false positives on critical actions
- Estimated 30 min saved per issue

Slide 9: Future Enhancements
- Linux/macOS support
- Predictive failure analysis
- Cluster-wide monitoring
- Custom ML models

Slide 10: Thank You
- GitHub: [link]
- Demo: [link]
- Contact: [email]
```

### Deliverable:

✅ Polished UI with loading states  
✅ Dashboard stats widgets  
✅ Demo script written  
✅ Demo video recorded  
✅ Presentation slides created  
✅ **FULLY FUNCTIONAL JAVA AI FULLSTACK PROJECT**

---

# 🎯 FINAL JAVA PROJECT STRUCTURE

```
aios-monitor/
├── agent/                          # Java monitoring agent (OSHI + JNA)
│   ├── src/main/java/com/aios/agent/
│   │   ├── collector/              # SystemMetricsCollector, ProcessInfoCollector
│   │   ├── detector/               # 5 issue detectors
│   │   ├── remediation/            # RemediationEngine + 8 actions
│   │   ├── service/                # BackendClient, WindowsNativeUtils
│   │   └── AgentApplication.java
│   └── pom.xml
├── backend/                        # Spring Boot REST API + WebSocket
│   ├── src/main/java/com/aios/backend/
│   │   ├── controller/             # REST controllers
│   │   ├── service/                # Business logic
│   │   ├── repository/             # JPA repositories
│   │   ├── model/                  # JPA entities
│   │   ├── dto/                    # DTOs
│   │   ├── config/                 # WebSocket, Security, Metrics
│   │   └── BackendApplication.java
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   └── logback-spring.xml
│   └── pom.xml
├── mcp-server/                     # MCP standardized tools
│   ├── src/main/java/com/aios/mcp/
│   │   ├── tools/                  # 7+ MCP tools
│   │   ├── controller/             # McpController
│   │   └── McpServerApplication.java
│   └── pom.xml
├── ai-agents/                      # LangChain4j AI agents
│   ├── src/main/java/com/aios/ai/
│   │   ├── agents/                 # 5 specialized agents
│   │   ├── service/                # AiDiagnosticOrchestrator
│   │   ├── tools/                  # McpToolService (wrappers)
│   │   └── AiAgentsApplication.java
│   └── pom.xml
├── shared/                         # Shared DTOs, enums, utilities
│   ├── src/main/java/com/aios/shared/
│   │   ├── dto/                    # MetricSnapshot, ProcessInfo, etc.
│   │   ├── enums/                  # IssueType, Severity, ActionType, etc.
│   │   └── util/
│   └── pom.xml
├── frontend/                       # React + TypeScript + Vite
│   ├── src/
│   │   ├── api/                    # Axios client
│   │   ├── components/             # MetricsCard, MetricsChart, ProcessTable, etc.
│   │   ├── pages/                  # Dashboard, IssuesPage, ActionsPage, SettingsPage
│   │   ├── hooks/                  # useMetrics, useDiagnosis
│   │   ├── types/                  # TypeScript types
│   │   ├── App.tsx
│   │   └── main.tsx
│   ├── package.json
│   └── vite.config.ts
├── docs/                           # Documentation
│   ├── ARCHITECTURE.md
│   ├── API.md
│   ├── AI_AGENTS.md
│   └── SAFETY.md
├── README.md
├── pom.xml                         # Parent POM
└── .gitignore
```

---

# ✅ SUCCESS METRICS

After 20 days, you'll have a **production-ready Java AI fullstack project** with:

✅ **Java monitoring agent** using OSHI + JNA  
✅ **Spring Boot backend** with REST API + WebSocket  
✅ **PostgreSQL database** with JPA  
✅ **Multi-agent AI system** (LangChain4j + GPT-4)  
✅ **MCP tool server** (standardized AI tools)  
✅ **React dashboard** (TypeScript + Ant Design)  
✅ **5 issue detectors** (memory leak, thread explosion, hung process, I/O bottleneck, resource hog)  
✅ **8+ remediation actions** (kill, reduce priority, trim memory, clear cache, etc.)  
✅ **Safety system** (dry-run, protected processes, policy enforcement)  
✅ **Windows integration** (service, system tray, toast notifications)  
✅ **Real-time updates** (WebSocket)  
✅ **Professional documentation**  
✅ **Demo video + presentation**

---

# 🚀 PORTFOLIO IMPACT

This project demonstrates:

1. **Full-stack Java expertise**: Spring Boot, JPA, WebSocket, REST API
2. **System programming**: OSHI, JNA, Windows native API
3. **AI/ML integration**: LangChain4j multi-agent system
4. **Modern frontend**: React + TypeScript + Vite
5. **Production practices**: Safety policies, logging, health checks
6. **Real-world problem solving**: Automated Windows system monitoring

**This is a STANDOUT portfolio project** showing enterprise-level Java development + cutting-edge AI.

---

# 🎬 READY TO START?

Would you like me to:

1. **Start Day 1** (create the Maven multi-module project structure)
2. **Generate specific code** for any component
3. **Explain any architecture decisions** in more detail

Let me know and I'll begin implementation! 🚀
