@echo off
setlocal
set "DIR=%~dp0"
set "JAVA_OPTS=%JAVA_OPTS%"
REM You can pass system properties here if needed, e.g.
REM set JAVA_OPTS=%JAVA_OPTS% -Dmatchmaker.bin=bin\MatchMaker.exe -Dmatchmaker.out=data\match_schedule.txt

REM Prefer the bundled JRE, then JAVA_HOME, then PATH
set "JAVA=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
if exist "%DIR%jre\bin\java.exe" set "JAVA=%DIR%jre\bin\java.exe"

"%JAVA%" %JAVA_OPTS% -jar "%DIR%app\scoring-system.jar"
