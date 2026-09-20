@rem Gradle startup script for Windows
@if "%DEBUG%"=="" @echo off
@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
set APP_HOME=%APP_HOME:"=%
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

@rem Execute Gradle
"%JAVA_HOME%\bin\java.exe" ^
  -classpath "%CLASSPATH%" ^
  org.gradle.wrapper.GradleWrapperMain ^
  %*

@rem End local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" endlocal

:omega
exit /b %ERRORLEVEL%