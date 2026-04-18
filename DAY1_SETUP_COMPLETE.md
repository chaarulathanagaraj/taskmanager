# Day 1 Setup Guide - Maven Installation & Build

## ✅ What Was Created

### Maven Multi-Module Project Structure

```
aios-monitor/
├── pom.xml                    # Parent POM with dependency management
├── .gitignore                 # Git ignore file
├── README.md                  # Project documentation
│
├── shared/                    # Shared DTOs & Enums
│   ├── pom.xml
│   └── src/main/java/com/aios/shared/
│       ├── dto/
│       │   ├── MetricSnapshot.java
│       │   ├── ProcessInfo.java
│       │   ├── DiagnosticIssue.java
│       │   └── RemediationAction.java
│       └── enums/
│           ├── IssueType.java
│           ├── Severity.java
│           ├── ActionType.java
│           ├── SafetyLevel.java
│           └── ActionStatus.java
│
├── agent/                     # Java Monitoring Agent
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/aios/agent/
│       │   └── AgentApplication.java
│       └── resources/
│           └── application.properties
│
├── backend/                   # Spring Boot REST API
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/aios/backend/
│       │   └── BackendApplication.java
│       └── resources/
│           └── application.properties
│
├── mcp-server/               # MCP Tool Server
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/aios/mcp/
│       │   └── McpServerApplication.java
│       └── resources/
│           └── application.properties
│
└── ai-agents/                # LangChain4j AI Agents
    ├── pom.xml
    └── src/main/
        ├── java/com/aios/ai/
        │   └── AiAgentsApplication.java
        └── resources/
            └── application.properties
```

## 📦 Install Maven

### Option 1: Using Chocolatey (Recommended for Windows)

```powershell
# Install Chocolatey if not already installed
Set-ExecutionPolicy Bypass -Scope Process -Force; [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072; iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))

# Install Maven
choco install maven -y

# Verify installation
mvn -version
```

### Option 2: Manual Installation

1. Download Maven from: https://maven.apache.org/download.cgi
2. Extract to `C:\Program Files\Apache\maven`
3. Add to PATH:
   ```powershell
   [Environment]::SetEnvironmentVariable("Path", $env:Path + ";C:\Program Files\Apache\maven\bin", "Machine")
   ```
4. Restart PowerShell and verify:
   ```powershell
   mvn -version
   ```

### Option 3: Using IntelliJ IDEA

IntelliJ IDEA comes with bundled Maven - simply open the project and it will automatically detect the Maven structure.

## 🚀 Build & Run

### Build All Modules

```bash
mvn clean install
```

### Run Individual Modules

**Backend (Port 8080):**

```bash
cd backend
mvn spring-boot:run
```

Access:

- API: http://localhost:8080
- Swagger: http://localhost:8080/swagger-ui.html
- H2 Console: http://localhost:8080/h2-console

**Agent:**

```bash
cd agent
mvn spring-boot:run
```

**MCP Server (Port 8081):**

```bash
cd mcp-server
mvn spring-boot:run
```

**AI Agents:**

```bash
cd ai-agents
mvn spring-boot:run
```

## 🔧 Configuration

### Backend Database (Production)

Edit `backend/src/main/resources/application.properties`:

```properties
# Switch from H2 to PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/aios
spring.datasource.username=your_username
spring.datasource.password=your_password
spring.jpa.hibernate.ddl-auto=update
```

### AI Agents OpenAI Key

Edit `ai-agents/src/main/resources/application.properties`:

```properties
openai.api.key=sk-your-actual-openai-key
```

Or use environment variable:

```powershell
$env:OPENAI_API_KEY="sk-your-actual-openai-key"
```

### MCP Server API Key

Edit `mcp-server/src/main/resources/application.properties`:

```properties
mcp.api.key=your-secure-random-key-123456
```

## 🧪 Test the Setup

### 1. Build the project

```bash
mvn clean compile
```

### 2. Run tests

```bash
mvn test
```

### 3. Create executable JARs

```bash
mvn clean package
```

JARs will be in:

- `agent/target/agent-1.0.0-SNAPSHOT.jar`
- `backend/target/backend-1.0.0-SNAPSHOT.jar`
- `mcp-server/target/mcp-server-1.0.0-SNAPSHOT.jar`
- `ai-agents/target/ai-agents-1.0.0-SNAPSHOT.jar`

### 4. Run from JAR

```bash
java -jar backend/target/backend-1.0.0-SNAPSHOT.jar
```

## 📊 Day 1 Deliverables - ✅ COMPLETE

- ✅ Maven multi-module project structure
- ✅ Parent POM with dependency management
- ✅ Shared module with DTOs and enums
- ✅ Agent module skeleton
- ✅ Backend module skeleton
- ✅ MCP Server module skeleton
- ✅ AI Agents module skeleton
- ✅ Configuration files for all modules
- ✅ .gitignore configured
- ✅ README.md with project overview

## 🎯 Next Steps - Day 2

Ready to start **Day 2: Java Agent Core (Data Collection)**?

This includes:

- Implementing `SystemMetricsCollector` with OSHI
- Implementing `ProcessInfoCollector`
- Adding scheduled metric collection (every 10s)
- Creating `AgentConfiguration` for settings
- Testing metric collection

Run:

```bash
cd agent
# Start implementing collectors as per Day 2 plan
```

## 🆘 Troubleshooting

### "mvn not recognized"

- Restart PowerShell after Maven installation
- Check PATH: `echo $env:Path | Select-String maven`
- Use absolute path: `& "C:\Program Files\Apache\maven\bin\mvn.cmd" clean install`

### "Java version mismatch"

```bash
# Check Java version
java -version  # Should be Java 17+

# Set JAVA_HOME
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
```

### IntelliJ IDEA

1. Open: File → Open → Select `C:\Users\Admin\Aios\pom.xml`
2. IntelliJ will auto-detect Maven modules
3. Wait for dependency download
4. Right-click modules → Run Spring Boot apps

## 📚 Resources

- Maven: https://maven.apache.org/guides/getting-started/
- Spring Boot: https://spring.io/guides/gs/spring-boot/
- OSHI: https://github.com/oshi/oshi
- LangChain4j: https://docs.langchain4j.dev/
