@echo off
REM ============================================
REM AIOS Monitor - Windows Service Installer
REM ============================================
REM 
REM This script installs AIOS Monitor as a Windows service
REM using Apache Commons Daemon (prunsrv.exe)
REM
REM Requirements:
REM   - prunsrv.exe from Apache Commons Daemon
REM   - Java 17+ installed
REM   - Administrator privileges
REM ============================================

setlocal EnableDelayedExpansion

REM Configuration
set SERVICE_NAME=AIOSMonitor
set DISPLAY_NAME=AIOS Monitor
set DESCRIPTION=AI-powered Windows system monitor
set JAR_NAME=agent.jar

REM Determine script directory
set SCRIPT_DIR=%~dp0
set INSTALL_DIR=%SCRIPT_DIR%

REM Check for administrator privileges
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo ERROR: This script requires Administrator privileges.
    echo Please right-click and select "Run as Administrator"
    pause
    exit /b 1
)

REM Check if prunsrv.exe exists
if not exist "%INSTALL_DIR%prunsrv.exe" (
    echo ERROR: prunsrv.exe not found in %INSTALL_DIR%
    echo.
    echo Please download Apache Commons Daemon from:
    echo https://commons.apache.org/proper/commons-daemon/download_daemon.cgi
    echo.
    echo Extract prunsrv.exe (64-bit) to: %INSTALL_DIR%
    pause
    exit /b 1
)

REM Check if JAR exists
if not exist "%INSTALL_DIR%%JAR_NAME%" (
    echo ERROR: %JAR_NAME% not found in %INSTALL_DIR%
    echo Please build the agent module first: mvn package -pl agent
    pause
    exit /b 1
)

REM Find Java installation
FOR /F "tokens=*" %%g IN ('where java 2^>nul') do (SET JAVA_PATH=%%g)
if not defined JAVA_PATH (
    if defined JAVA_HOME (
        set JAVA_PATH=%JAVA_HOME%\bin\java.exe
    ) else (
        echo ERROR: Java not found in PATH or JAVA_HOME
        pause
        exit /b 1
    )
)

REM Get JVM path from Java installation
for %%i in ("%JAVA_PATH%") do set JAVA_BIN_DIR=%%~dpi
set JVM_DLL=%JAVA_BIN_DIR%..\lib\server\jvm.dll
if not exist "%JVM_DLL%" (
    set JVM_DLL=%JAVA_BIN_DIR%server\jvm.dll
)

echo ============================================
echo AIOS Monitor Service Installer
echo ============================================
echo.
echo Service Name:    %SERVICE_NAME%
echo Display Name:    %DISPLAY_NAME%
echo Install Dir:     %INSTALL_DIR%
echo Java:            %JAVA_PATH%
echo JVM:             %JVM_DLL%
echo.
echo Press any key to install, or Ctrl+C to cancel...
pause >nul

REM Create logs directory
if not exist "%INSTALL_DIR%logs" mkdir "%INSTALL_DIR%logs"

REM Stop existing service if running
sc query %SERVICE_NAME% >nul 2>&1
if %errorLevel% equ 0 (
    echo Stopping existing service...
    net stop %SERVICE_NAME% >nul 2>&1
    echo Removing existing service...
    "%INSTALL_DIR%prunsrv.exe" //DS//%SERVICE_NAME%
    timeout /t 2 >nul
)

REM Install the service
echo Installing service...
"%INSTALL_DIR%prunsrv.exe" //IS//%SERVICE_NAME% ^
    --DisplayName="%DISPLAY_NAME%" ^
    --Description="%DESCRIPTION%" ^
    --Startup=auto ^
    --Jvm="%JVM_DLL%" ^
    --Classpath="%INSTALL_DIR%%JAR_NAME%" ^
    --StartMode=jvm ^
    --StartClass=com.aios.agent.service.WindowsService ^
    --StartMethod=start ^
    --StartParams=start ^
    --StopMode=jvm ^
    --StopClass=com.aios.agent.service.WindowsService ^
    --StopMethod=stop ^
    --StopParams=stop ^
    --LogPath="%INSTALL_DIR%logs" ^
    --LogPrefix=aios-service ^
    --LogLevel=Info ^
    --StdOutput=auto ^
    --StdError=auto ^
    --JvmOptions="-Dspring.profiles.active=prod" ^
    --JvmOptions="-Xms256m" ^
    --JvmOptions="-Xmx1024m"

if %errorLevel% neq 0 (
    echo ERROR: Failed to install service
    pause
    exit /b 1
)

echo.
echo Service installed successfully!
echo.
echo Starting service...
net start %SERVICE_NAME%

if %errorLevel% equ 0 (
    echo.
    echo ============================================
    echo Installation Complete!
    echo ============================================
    echo.
    echo The AIOS Monitor service is now running.
    echo.
    echo Management commands:
    echo   Start:     net start %SERVICE_NAME%
    echo   Stop:      net stop %SERVICE_NAME%
    echo   Status:    sc query %SERVICE_NAME%
    echo   Uninstall: %INSTALL_DIR%uninstall-service.bat
    echo.
    echo Log files: %INSTALL_DIR%logs\
    echo.
) else (
    echo.
    echo WARNING: Service installed but failed to start.
    echo Check logs at: %INSTALL_DIR%logs\
)

pause
