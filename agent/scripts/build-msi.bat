@echo off
REM ============================================
REM AIOS Monitor - MSI Installer Builder
REM Requires JDK 14+ with jpackage tool
REM ============================================

setlocal EnableDelayedExpansion

set SCRIPT_DIR=%~dp0
set PROJECT_ROOT=%SCRIPT_DIR%..
set JAR_FILE=%PROJECT_ROOT%\target\agent-1.0.0-SNAPSHOT.jar
set OUTPUT_DIR=%PROJECT_ROOT%\installer
set APP_NAME=AIOS Monitor
set APP_VERSION=1.0.0
set VENDOR=AIOS
set DESCRIPTION=Intelligent OS Monitoring and Self-Healing Agent

REM Check for jpackage
where jpackage >nul 2>&1
if %errorLevel% neq 0 (
    echo ERROR: jpackage not found. JDK 14+ is required.
    echo Please ensure JAVA_HOME points to JDK 14 or later.
    pause
    exit /b 1
)

REM Build the project first
echo Building project...
cd %PROJECT_ROOT%\..
call mvn package -pl agent -am -DskipTests -q
if %errorLevel% neq 0 (
    echo ERROR: Maven build failed
    pause
    exit /b 1
)

REM Create output directory
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"

REM Check if JAR exists
if not exist "%JAR_FILE%" (
    echo ERROR: JAR file not found: %JAR_FILE%
    echo Please run 'mvn package' first.
    pause
    exit /b 1
)

echo.
echo ============================================
echo Building MSI Installer
echo ============================================
echo.

REM Create jpackage input directory
set JPACKAGE_INPUT=%OUTPUT_DIR%\input
if exist "%JPACKAGE_INPUT%" rmdir /s /q "%JPACKAGE_INPUT%"
mkdir "%JPACKAGE_INPUT%"

REM Copy JAR and dependencies
copy "%JAR_FILE%" "%JPACKAGE_INPUT%\" >nul

REM Copy runtime dependencies from lib
if exist "%PROJECT_ROOT%\target\lib" (
    xcopy "%PROJECT_ROOT%\target\lib\*" "%JPACKAGE_INPUT%\lib\" /s /e /q >nul 2>&1
)

REM Create MSI installer
echo Creating MSI package...
jpackage ^
    --type msi ^
    --name "AIOS Monitor" ^
    --app-version %APP_VERSION% ^
    --vendor "%VENDOR%" ^
    --description "%DESCRIPTION%" ^
    --input "%JPACKAGE_INPUT%" ^
    --main-jar agent-1.0.0-SNAPSHOT.jar ^
    --main-class com.aios.agent.AgentApplication ^
    --dest "%OUTPUT_DIR%" ^
    --win-menu ^
    --win-menu-group "AIOS" ^
    --win-shortcut ^
    --win-shortcut-prompt ^
    --win-dir-chooser ^
    --win-per-user-install ^
    --icon "%SCRIPT_DIR%\aios-icon.ico" ^
    --java-options "-Xms128m" ^
    --java-options "-Xmx512m" ^
    --java-options "-Dspring.profiles.active=production" ^
    --java-options "-Djava.awt.headless=false" ^
    --resource-dir "%SCRIPT_DIR%\resources" ^
    --license-file "%PROJECT_ROOT%\..\LICENSE" ^
    --verbose

if %errorLevel% equ 0 (
    echo.
    echo ============================================
    echo MSI Installer Created Successfully
    echo ============================================
    echo.
    echo Output: %OUTPUT_DIR%\AIOS Monitor-%APP_VERSION%.msi
    echo.
) else (
    echo.
    echo WARNING: jpackage completed with warnings or errors.
    echo Check output above for details.
)

REM Cleanup
rmdir /s /q "%JPACKAGE_INPUT%" 2>nul

pause
