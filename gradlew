#!/bin/sh
# 精简版 Gradle Wrapper 启动脚本（等价于官方 gradlew，仅保留核心逻辑）
DIR=$(cd "$(dirname "$0")" && pwd)
if [ -n "$JAVA_HOME" ]; then
  JAVA_EXE="$JAVA_HOME/bin/java"
else
  JAVA_EXE=java
fi
exec "$JAVA_EXE" -Dorg.gradle.appname=gradlew \
  -classpath "$DIR/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain "$@"
