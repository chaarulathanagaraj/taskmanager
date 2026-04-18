@echo off
REM ============================================
REM AIOS Monitor - Portable ZIP Builder
REM Creates a portable distribution
REM ============================================

setlocal EnableDelayedExpansion

set SCRIPT_DIR=%~dp0
set PROJECT_ROOT=%SCRIPT_DIR%..
set OUTPUT_DIR=%PROJECT_ROOT%\dist
set APP_VERSION=1.0.0
set ZIP_NAME=aios-monitor-%APP_VERSION%-portable

echo ============================================
echo AIOS Monitor Portable Distribution Builder
echo ============================================
echo.

REM Build the project
echo Building project...
cd %PROJECT_ROOT%\..
call mvn package -pl agent -am -DskipTests -q
if %errorLevel% neq 0 (
    echo ERROR: Maven build failed
    pause
    exit /b 1
)

REM Create output directory
if exist "%OUTPUT_DIR%\%ZIP_NAME%" rmdir /s /q "%OUTPUT_DIR%\%ZIP_NAME%"
mkdir "%OUTPUT_DIR%\%ZIP_NAME%"
mkdir "%OUTPUT_DIR%\%ZIP_NAME%\lib"
mkdir "%OUTPUT_DIR%\%ZIP_NAME%\logs"
mkdir "%OUTPUT_DIR%\%ZIP_NAME%\config"

echo Copying files...

REM Copy main JAR
copy "%PROJECT_ROOT%\target\agent-1.0.0-SNAPSHOT.jar" "%OUTPUT_DIR%\%ZIP_NAME%\aios-agent.jar" >nul

REM Copy dependencies
if exist "%PROJECT_ROOT%\target\lib" (
    xcopy "%PROJECT_ROOT%\target\lib\*" "%OUTPUT_DIR%\%ZIP_NAME%\lib\" /s /e /q >nul
)

REM Copy scripts
copy "%SCRIPT_DIR%\install-service.bat" "%OUTPUT_DIR%\%ZIP_NAME%\" >nul
copy "%SCRIPT_DIR%\uninstall-service.bat" "%OUTPUT_DIR%\%ZIP_NAME%\" >nul

REM Create launcher scripts
echo Creating launcher scripts...

REM Create run.bat
(
echo @echo off
echo REM AIOS Monitor - Launcher
echo echo Starting AIOS Monitor...
echo cd /d "%%~dp0"
echo java -Xms128m -Xmx512m -jar aios-agent.jar %%*
echo if %%errorLevel%% neq 0 pause
) > "%OUTPUT_DIR%\%ZIP_NAME%\run.bat"

REM Create run-background.vbs (silent launcher)
(
echo Set WshShell = CreateObject^("WScript.Shell"^)
echo WshShell.Run "cmd /c cd /d """ ^& CreateObject^("Scripting.FileSystemObject"^).GetParentFolderName^(WScript.ScriptFullName^) ^& """ ^&^& java -Xms128m -Xmx512m -jar aios-agent.jar", 0
echo Set WshShell = Nothing
) > "%OUTPUT_DIR%\%ZIP_NAME%\run-background.vbs"

REM Create default config
(
echo # AIOS Monitor Configuration
echo # Copy this to application.properties and customize
echo.
echo # Backend URL
echo aios.backend.url=http://localhost:8080
echo.
echo # Collection intervals
echo aios.metrics.collection.interval=10000
echo aios.process.collection.interval=15000
echo aios.detection.interval=30000
echo.
echo # Remediation settings
echo aios.remediation.dry-run-mode=true
echo aios.remediation.auto-enabled=false
echo aios.remediation.max-actions-per-minute=5
echo.
echo # Protected processes (comma-separated)
echo aios.protected-processes=System,csrss.exe,lsass.exe,winlogon.exe,services.exe,smss.exe,svchost.exe
echo.
echo # System tray
echo aios.systray.enabled=true
echo.
echo # Notifications
echo aios.notifications.enabled=true
echo aios.notifications.toast.enabled=true
echo aios.notifications.throttle.seconds=60
echo.
echo # Logging
echo logging.level.com.aios=INFO
echo logging.file.name=logs/aios-monitor.log
) > "%OUTPUT_DIR%\%ZIP_NAME%\config\application.properties.template"

REM Create README
(
echo AIOS Monitor - Portable Distribution
echo ====================================
echo.
echo Quick Start:
echo 1. Run 'run.bat' to start the agent
echo 2. Or run 'run-background.vbs' for silent startup
echo.
echo As Windows Service:
echo 1. Run 'install-service.bat' as Administrator
echo 2. The service will start automatically
echo.
echo Configuration:
echo - Copy config/application.properties.template to config/application.properties
echo - Edit the settings as needed
echo.
echo Logs:
echo - Log files are stored in the 'logs' folder
echo.
echo Uninstall Service:
echo - Run 'uninstall-service.bat' as Administrator
) > "%OUTPUT_DIR%\%ZIP_NAME%\README.txt"

REM Create ZIP (using PowerShell)
echo Creating ZIP archive...
powershell -Command "Compress-Archive -Path '%OUTPUT_DIR%\%ZIP_NAME%\*' -DestinationPath '%OUTPUT_DIR%\%ZIP_NAME%.zip' -Force"

if %errorLevel% equ 0 (
    echo.
    echo ============================================
    echo Portable Distribution Created Successfully
    echo ============================================
    echo.
    echo Folder: %OUTPUT_DIR%\%ZIP_NAME%\
    echo ZIP: %OUTPUT_DIR%\%ZIP_NAME%.zip
    echo.
) else (
    echo.
    echo WARNING: ZIP creation failed. Folder is still available.
)

pause
