#!/bin/sh
# Minimal wrapper launcher. Downloads gradle-wrapper.jar once if it is missing (needs network), then runs the wrapper.
APP_HOME=$(cd "$(dirname "$0")" && pwd)
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$JAR" ]; then
  echo "Downloading gradle-wrapper.jar ..." >&2
  curl -fsSL -o "$JAR" "https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar" || { echo "Download failed. Run: gradle wrapper --gradle-version 8.9" >&2; rm -f "$JAR"; exit 1; }
fi
exec java -Dorg.gradle.appname=gradlew -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
