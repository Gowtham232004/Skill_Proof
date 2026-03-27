@echo off
setlocal

REM Local Maven shim: makes `mvn ...` work in this project by delegating to Maven Wrapper.
if not defined JAVA_HOME (
  if exist "C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"
  )
)

if defined JAVA_HOME (
  set "PATH=%JAVA_HOME%\bin;%PATH%"
)

call "%~dp0mvnw.cmd" %*
exit /b %ERRORLEVEL%
