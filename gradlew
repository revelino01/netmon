#!/bin/sh
# Gradle wrapper — delegates to system gradle for bootstrap
# The wrapper JAR will be generated on first CI run

set -e

APP_BASE_NAME=$(basename "$0")
APP_HOME=$(cd "$(dirname "$0")" && pwd)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$CLASSPATH" ]; then
    echo "Gradle wrapper JAR not found. Running bootstrap..."
    exec gradle wrapper --gradle-version 8.7
fi

exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"