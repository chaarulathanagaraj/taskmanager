# 🤖 AIOS Monitor - AI-Powered Windows System Monitor

## Overview

AIOS Monitor is an intelligent Windows monitoring system that uses AI agents to detect and remediate system issues automatically.

## Architecture

```
[React Dashboard] ← HTTP/WebSocket → [Spring Boot Backend]
                                            ↓
                                     [PostgreSQL]
        ↓                                   ↓
[Java Agent (OSHI)] → Metrics → [LangChain4j AI Agents]
        ↓                                   ↓
[MCP Server] ← Tools ← [OpenAI GPT-4]
```

## Technology Stack

### Backend & Agent

- **Java 17+** (LTS)
- **Spring Boot 3.2+** (REST API, WebSocket, Scheduling)
- **OSHI** (Operating System & Hardware Information)
- **JNA** (Windows Native API calls)
- **LangChain4j** (AI Agent Framework)
- **PostgreSQL** (Database)
- **Maven** (Build tool)

### Frontend

- **React 18+** (TypeScript)
- **Vite** (Build tool)
- **Ant Design** (UI Component library)
- **TanStack Query** (Data fetching)
- **Recharts** (Visualization)

### AI

- **LangChain4j** (Multi-agent orchestration)
- **OpenAI GPT-4** (AI reasoning)

## Project Structure

```
aios-monitor/
├── agent/          # Java monitoring agent
├── backend/        # Spring Boot REST API
├── mcp-server/     # MCP tool server
├── ai-agents/      # LangChain4j agents
├── shared/         # Shared DTOs/models
├── frontend/       # React dashboard
└── pom.xml         # Parent POM
```

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.8+
- Node.js 18+ (for frontend)
- PostgreSQL 14+ (for production)

### Build All Modules

```bash
mvn clean install
```

### Run Individual Modules

**Backend:**

```bash
cd backend
mvn spring-boot:run
# Access: http://localhost:8080
# Swagger: http://localhost:8080/swagger-ui.html
```

**Agent:**

```bash
cd agent
mvn spring-boot:run
```

**MCP Server:**

```bash
cd mcp-server
mvn spring-boot:run
# Port: 8081
```

**AI Agents:**

```bash
cd ai-agents
mvn spring-boot:run
```

### Configuration

Create `application-local.properties` in each module's `src/main/resources/`:

**Backend:**

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/aios
spring.datasource.username=your_username
spring.datasource.password=your_password
```

**AI Agents:**

```properties
openai.api.key=sk-your-openai-key
```

## Features

- ✅ Real-time system monitoring (CPU, memory, disk, network)
- ✅ 5 AI-powered issue detectors
- ✅ Multi-agent AI diagnosis (LangChain4j + GPT-4)
- ✅ 8+ remediation actions
- ✅ Safety system (dry-run mode, protected processes)
- ✅ React dashboard with real-time charts
- ✅ Windows service integration
- ✅ Toast notifications

## Development Status

🚧 **Day 1: Project Setup & Architecture** - IN PROGRESS

See [JAVA_IMPLEMENTATION_PLAN.md](JAVA_IMPLEMENTATION_PLAN.md) for the complete 20-day implementation roadmap.

## License

MIT License

## Contributing

Pull requests are welcome! For major changes, please open an issue first.
