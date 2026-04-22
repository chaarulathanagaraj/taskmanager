# AutoTasker

AutoTasker is a multi-module system for Windows process monitoring, issue detection, AI-assisted diagnosis, and controlled remediation.

It combines:

- A Java agent that collects system/process metrics and detects anomalies
- A Spring Boot backend that stores data, serves APIs, and broadcasts real-time updates
- A dedicated MCP tool server for OS-level operations
- LangChain4j-based AI diagnosis orchestration (Gemini)
- A React dashboard for operations and visualization

## 1. Architecture

```text
                 +--------------------------------------+
                 | Frontend (React + Vite)             |
                 | - Dashboard, Issues, Logs, Settings |
                 +------------------+-------------------+
                                    |
                           HTTP + STOMP/SockJS
                                    |
                                    v
                 +--------------------------------------+
                 | Backend (Spring Boot, :8080)        |
                 | - REST API, WebSocket broker         |
                 | - Persistence (H2/PostgreSQL)        |
                 | - AI diagnosis orchestration entry   |
                 +------------------+-------------------+
                                    |
                      in-process module dependency
                                    |
                                    v
                 +--------------------------------------+
                 | AI Agents module (LangChain4j)      |
                 | - Specialist analyzers               |
                 | - Planner + safety validator         |
                 +------------------+-------------------+
                                    |
                              HTTP (X-API-Key)
                                    |
                                    v
                 +--------------------------------------+
                 | MCP Server (Spring Boot, :8082)     |
                 | - get_process_list, threads, I/O     |
                 | - execute tool endpoints             |
                 +--------------------------------------+

  +--------------------------------------+
  | Agent (Spring Boot, non-web)         |
  | - OSHI/JNA metrics + issue detection |
  | - Queues + retries + backend sync    |
  +------------------+-------------------+
                     |
                     | HTTP REST
                     v
        Backend /api/metrics, /api/issues, /api/actions
```

## 2. Modules and Responsibilities

### backend

- Main API and orchestration service
- Receives telemetry and detected issues from agent
- Persists metrics/issues/actions
- Exposes OpenAPI docs and actuator endpoints
- Broadcasts real-time events over WebSocket topics

### agent

- Runs as a monitoring daemon (non-web Spring app)
- Collects host/process metrics using OSHI
- Detects anomalies and queues events
- Syncs metrics/issues/actions to backend with Resilience4j retry
- Supports Windows service install scripts in [agent/scripts](agent/scripts)

### ai-agents

- LangChain4j-based diagnosis workflow
- Routes issue types to specialized analyzers
- Produces remediation plan and safety validation
- Uses Gemini API key via environment variable

### mcp-server

- Exposes MCP-style tool APIs for process and system operations
- Authenticates tool execution endpoints with X-API-Key header

### shared

- Shared DTOs/enums/models used across Java modules

### cli

- Spring Shell based utility module for CLI experiments/debug workflows

### frontend

- React + TypeScript UI
- REST polling with React Query
- Real-time event updates via STOMP over SockJS

## 3. Runtime Flow End-to-End

1. Agent collects metrics on schedule and pushes snapshots to an internal queue.
2. Agent detectors classify anomalies into issues and queue them.
3. Agent sync jobs call backend APIs:
   - POST /api/metrics
   - POST /api/issues
   - POST /api/actions
4. Backend stores data and emits updates to WebSocket topics:
   - /topic/metrics
   - /topic/issues
   - /topic/actions
5. Frontend subscribes to those topics and updates UI in near real-time.
6. When diagnosis is requested, backend calls AI orchestrator in ai-agents module.
7. AI orchestrator analyzes issue, proposes plan, and checks safety.
8. MCP tool service calls MCP server tool endpoints (with API key) for system context/tool actions.
9. Backend returns diagnosis/action results and broadcasts relevant updates.

## 4. Tech Stack

### Java platform

- Java 17
- Maven multi-module build
- Spring Boot 3.2.x

### Observability and system introspection

- OSHI
- JNA
- Spring Actuator

### AI layer

- LangChain4j
- Google Gemini integration

### Frontend

- React 19 + TypeScript
- Vite
- Ant Design
- TanStack Query
- Recharts
- STOMP + SockJS

## 5. Prerequisites

- JDK 17+
- Maven 3.8+
- Node.js 18+
- npm 9+
- Windows 10/11 for full remediation and service scripts

Notes:

- Backend and frontend can run cross-platform.
- Agent remediation behaviors and service packaging are Windows-focused.

## 6. Quick Start (Local)

### Step 1: Build all Java modules

```bash
mvn clean install
```

### Step 2: Start services in recommended order

Use module-local Maven invocation to avoid parent plugin resolution issues:

```bash
mvn -f mcp-server/pom.xml spring-boot:run
mvn -f backend/pom.xml spring-boot:run
mvn -f agent/pom.xml spring-boot:run
```

If you use module-only runs frequently, install dependency modules first:

```bash
mvn -pl shared,ai-agents -am install -DskipTests
```

1. MCP server

```bash
cd mcp-server
mvn spring-boot:run
```

2. Backend

```bash
cd backend
mvn spring-boot:run
```

3. Agent

```bash
cd agent
mvn spring-boot:run
```

4. Frontend

```bash
cd frontend
npm install
npm run dev
```

### Step 3: Open UI and diagnostics

- Dashboard: http://localhost:5173 (or Vite printed URL)
- Backend API docs: http://localhost:8080/swagger-ui.html
- Backend health: http://localhost:8080/actuator/health
- MCP health: http://localhost:8082/actuator/health

## 7. Configuration

This project uses module-level application properties/yml plus environment variables.

### Required environment variables

```bash
GEMINI_API_KEY=your_gemini_key
MCP_API_KEY=your_secure_mcp_key
```

### Backend config

Default files:

- [backend/src/main/resources/application.properties](backend/src/main/resources/application.properties)
- [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml)

Important keys:

- server.port=8080
- spring.datasource.url=jdbc:h2:mem:aios... (default in-memory DB)
- gemini.api.key=${GEMINI_API_KEY:}
- mcp.server.url=http://localhost:8082

### Agent config

Default files:

- [agent/src/main/resources/application.yml](agent/src/main/resources/application.yml)
- [agent/src/main/resources/application.properties](agent/src/main/resources/application.properties)

Important keys:

- agent.collection-interval-seconds=10
- agent.backend-url=http://localhost:8080
- agent.dry-run-mode=false
- agent.protected-processes=...

### MCP server config

Default file:

- [mcp-server/src/main/resources/application.properties](mcp-server/src/main/resources/application.properties)

Important keys:

- server.port=8082
- aios.mcp.api-key=${MCP_API_KEY:}
- aios.mcp.auth.enabled=true

### AI agents config

Default file:

- [ai-agents/src/main/resources/application.properties](ai-agents/src/main/resources/application.properties)

Important keys:

- gemini.api.key=${GEMINI_API_KEY:}
- mcp.server.url=http://localhost:8082
- aios.mcp.api-key=${MCP_API_KEY:}

### Frontend config

Files:

- [frontend/.env.example](frontend/.env.example)
- [frontend/src/config/env.ts](frontend/src/config/env.ts)

Important keys:

- VITE_API_BASE_URL (default http://localhost:8080)
- VITE_WS_BASE_URL (default http://localhost:8080)

## 8. API and WebSocket Surface

### Service ports

| Service    |                Port | Notes                                 |
| ---------- | ------------------: | ------------------------------------- |
| backend    |                8080 | REST + Swagger + WebSocket + actuator |
| mcp-server |                8082 | MCP tools + actuator                  |
| agent      |       n/a (non-web) | outbound sync client to backend       |
| frontend   | 5173 (default Vite) | UI dev server                         |

### Backend WebSocket

- Endpoint: /ws (SockJS enabled)
- Broker prefix: /topic
- App destination prefix: /app

Topics used by UI:

- /topic/metrics
- /topic/issues
- /topic/actions
- /topic/alerts

### Backend API route groups

Representative route groups in [backend/src/main/java/com/aios/backend/controller](backend/src/main/java/com/aios/backend/controller):

- /api/metrics
- /api/issues
- /api/actions
- /api/processes
- /api/diagnose
- /api/rules
- /api/rules/evaluation
- /api/dashboard
- /api/dashboard/stats
- /api/settings
- /api/health
- /api/chat
- /api/logs
- /api/policies

Actuator routes:

- /actuator/health
- /actuator/info
- /actuator/metrics
- /actuator/prometheus

### MCP API routes

Base path: /api/mcp

- GET /api/mcp/tools
- GET /api/mcp/tools/{toolName}
- POST /api/mcp/tools/{toolName}/execute
- GET /api/mcp/categories

Auth notes:

- Protected MCP routes expect header X-API-Key.
- Public discovery endpoints include GET /api/mcp/tools and GET /api/mcp/categories.

## 9. Build, Test, and Packaging

### Java build and test

```bash
mvn clean install
mvn test
```

Build specific module with dependencies:

```bash
mvn package -pl backend -am
mvn package -pl agent -am
mvn package -pl mcp-server -am
```

### Frontend

```bash
cd frontend
npm install
npm run dev
npm run build
npm run lint
npm run preview
```

### Agent packaging scripts (Windows)

Location: [agent/scripts](agent/scripts)

- build-msi.bat (MSI packaging, requires jpackage)
- build-portable.bat (portable ZIP bundle)
- install-service.bat (Windows service install, requires Administrator)
- uninstall-service.bat (service removal)

## 10. Operational Notes and Safety

### Safety defaults you must understand

- agent.dry-run-mode defaults to false in current config.
- With dry-run disabled, actions can execute for real.
- Keep protected process list up to date.
- Use lower-risk environments first before enabling automation broadly.

### Reliability behavior

- Agent uses bounded queues (max 1000 entries per queue) for metrics/issues/actions.
- Sync jobs retry with Resilience4j and exponential backoff.
- Backend unavailability can cause queue growth and eventual oldest-item drop.

### Persistence behavior

- Default backend DB is H2 in-memory (ephemeral).
- For durable data, switch to PostgreSQL in backend configuration.

## 11. Troubleshooting

### Backend starts but diagnosis fails

Check:

- MCP server is running on port 8082
- MCP_API_KEY is set consistently in backend/ai-agents/mcp-server environments
- GEMINI_API_KEY is available

### UI loads but no live data updates

Check:

- Backend is reachable from frontend VITE_API_BASE_URL
- WebSocket endpoint /ws is reachable
- Browser console for STOMP/SockJS connection errors

### Agent runs but backend has no metrics/issues/actions

Check:

- agent.backend-url points to backend
- Backend actuator health is UP
- Agent logs for retry/failure entries

### Service install fails on Windows

Check:

- Run installer script as Administrator
- prunsrv.exe exists in script directory for install-service.bat
- Built agent JAR exists as expected

## 13. Project Structure

```

## 14. Related Design Docs

- [JAVA_IMPLEMENTATION_PLAN.md](JAVA_IMPLEMENTATION_PLAN.md)
- [RULE_ENGINE_DESIGN.md](RULE_ENGINE_DESIGN.md)
- [PROCESS_CLASSIFICATION_IMPLEMENTATION.md](PROCESS_CLASSIFICATION_IMPLEMENTATION.md)
- [backend-actioncontroller.txt](backend-actioncontroller.txt)


```
