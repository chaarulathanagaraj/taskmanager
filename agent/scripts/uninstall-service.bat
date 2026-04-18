@echo off
REM ============================================
REM AIOS Monitor - Windows Service Uninstaller
REM ============================================

setlocal EnableDelayedExpansion

set SERVICE_NAME=AIOSMonitor
set SCRIPT_DIR=%~dp0

REM Check for administrator privileges
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo ERROR: This script requires Administrator privileges.
    echo Please right-click and select "Run as Administrator"
    pause
    exit /b 1
)

echo ============================================
echo AIOS Monitor Service Uninstaller
echo ============================================
echo.
echo This will stop and remove the AIOS Monitor service.
echo.
echo Press any key to continue, or Ctrl+C to cancel...
pause >nul

REM Check if service exists
sc query %SERVICE_NAME% >nul 2>&1
if %errorLevel% neq 0 (
    echo Service %SERVICE_NAME% is not installed.
    pause
    exit /b 0
)

REM Stop the service
echo Stopping service...
net stop %SERVICE_NAME% >nul 2>&1
timeout /t 2 >nul

REM Remove the service
echo Removing service...
if exist "%SCRIPT_DIR%prunsrv.exe" (
    "%SCRIPT_DIR%prunsrv.exe" //DS//%SERVICE_NAME%
) else (
    sc delete %SERVICE_NAME%
)

if %errorLevel% equ 0 (
    echo.
    echo ============================================
    echo Service Uninstalled Successfully
    echo ============================================
    echo.
    echo The AIOS Monitor service has been removed.
    echo Log files are still available at: %SCRIPT_DIR%logs\
    echo.
) else (
    echo.
    echo ERROR: Failed to remove service.
    echo Try using: sc delete %SERVICE_NAME%
)

pause
