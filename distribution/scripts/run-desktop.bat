@echo off
setlocal
set "DIR=%~dp0"
cd /d "%DIR%"

REM Prefer the bundled JRE, then JAVA_HOME, then PATH
set "JAVA=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
if exist "%DIR%jre\bin\java.exe" set "JAVA=%DIR%jre\bin\java.exe"

echo Using JAVA: %JAVA%
"%JAVA%" -cp "app\scoring-system.jar" org.thingai.app.desktop.DesktopLauncher
