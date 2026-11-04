@REM ----------------------------------------------------------------------------
@REM Maven Wrapper Script for Windows
@REM ----------------------------------------------------------------------------
@echo off
setlocal

set "MAVEN_CMD=mvn"
where mvn >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    if exist "C:\Users\pruth\AppData\Local\Programs\apache-maven-3.9.6\bin\mvn.cmd" (
        set "MAVEN_CMD=C:\Users\pruth\AppData\Local\Programs\apache-maven-3.9.6\bin\mvn.cmd"
    )
)

"%MAVEN_CMD%" %*
exit /b %ERRORLEVEL%
