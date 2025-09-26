@ECHO OFF
SETLOCAL

SET DIRNAME=%~dp0
IF "%DIRNAME%"=="" SET DIRNAME=.
SET APP_BASE_NAME=%~n0
SET APP_HOME=%DIRNAME%

FOR %%i IN ("%APP_HOME%") DO SET APP_HOME=%%~fi

SET DEFAULT_JVM_OPTS=

SET CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

SET JAVA_EXE=java.exe
IF DEFINED JAVA_HOME SET JAVA_EXE=%JAVA_HOME%\bin\java.exe

IF EXIST "%JAVA_EXE%" GOTO foundJava
ECHO ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
GOTO fail

:foundJava
SET CMD_LINE_ARGS=%*
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %CMD_LINE_ARGS%
EXIT /B %ERRORLEVEL%

:fail
EXIT /B 1
