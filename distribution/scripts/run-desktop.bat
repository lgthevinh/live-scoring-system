@echo off
setlocal
set "BASE=%~dp0"
rem Go to distribution root (this script is placed at root in dist)
cd /d "%BASE%"
set "JAVA=%JAVA_HOME%\bin\java.exe"
if not exist "%JAVA%" set "JAVA=java"
echo Using JAVA: %JAVA%
%JAVA% -jar "app\scoring-launcher.jar"
endlocal