@rem 精简版 Gradle Wrapper 启动脚本（等价于官方 gradlew.bat，仅保留核心逻辑）
@echo off
setlocal
set DIR=%~dp0
if defined JAVA_HOME (
  set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_EXE=java"
)
"%JAVA_EXE%" -Dorg.gradle.appname=gradlew -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
endlocal
