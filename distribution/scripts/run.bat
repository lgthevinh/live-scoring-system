@echo off
setlocal
set DIR=%~dp0
set JAVA_OPTS=%JAVA_OPTS%
REM You can pass system properties here if needed, e.g.
REM set JAVA_OPTS=%JAVA_OPTS% -Dmatchmaker.bin=bin\MatchMaker.exe -Dmatchmaker.out=data\match_schedule.txt
java %JAVA_OPTS% -jar "%DIR%app\scoring-system.jar"