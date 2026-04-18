@echo off
:: Set up Java 21 to ensure Maven works properly
set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.10"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "MVN=C:\Program Files\apache-maven-3.9.14\bin\mvn.cmd"

echo =======================================================
echo Starting AIOS System (Backend, Agent, MCP Server)
echo =======================================================

:: Create temporary helper scripts to safely keep windows open on errors
echo @echo off > temp-backend.bat
echo set JAVA_HOME=C:\Program Files\Java\jdk-21.0.10>> temp-backend.bat
echo set PATH=%%JAVA_HOME%%\bin;%%PATH%%>> temp-backend.bat
echo echo Starting Backend...>> temp-backend.bat
echo call "C:\Program Files\apache-maven-3.9.14\bin\mvn.cmd" -pl backend spring-boot:run>> temp-backend.bat
echo pause >> temp-backend.bat

echo @echo off > temp-mcp.bat
echo set JAVA_HOME=C:\Program Files\Java\jdk-21.0.10>> temp-mcp.bat
echo set PATH=%%JAVA_HOME%%\bin;%%PATH%%>> temp-mcp.bat
echo echo Starting MCP Server...>> temp-mcp.bat
echo call "C:\Program Files\apache-maven-3.9.14\bin\mvn.cmd" -pl mcp-server spring-boot:run>> temp-mcp.bat
echo pause >> temp-mcp.bat

echo @echo off > temp-agent.bat
echo set JAVA_HOME=C:\Program Files\Java\jdk-21.0.10>> temp-agent.bat
echo set PATH=%%JAVA_HOME%%\bin;%%PATH%%>> temp-agent.bat
echo echo Starting Agent...>> temp-agent.bat
echo call "C:\Program Files\apache-maven-3.9.14\bin\mvn.cmd" -pl agent spring-boot:run>> temp-agent.bat
echo pause >> temp-agent.bat

:: Launch the helpers
start "AIOS Backend" cmd /c temp-backend.bat
echo [1/3] Backend starting...
timeout /t 5 /nobreak > nul

start "AIOS MCP Server" cmd /c temp-mcp.bat
echo [2/3] MCP Server starting...

start "AIOS Agent" cmd /c temp-agent.bat
echo [3/3] Agent starting...

echo =======================================================
echo Done! All three components are running in their own windows.
echo =======================================================
pause
