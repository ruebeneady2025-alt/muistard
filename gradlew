#!/usr/bin/env bash
set -eu

WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "${WRAPPER_JAR}" ]; then
  mkdir -p "$(dirname "${WRAPPER_JAR}")"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL -o "${WRAPPER_JAR}" "https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradle/wrapper/gradle-wrapper.jar"
  elif command -v wget >/dev/null 2>&1; then
    wget -q -O "${WRAPPER_JAR}" "https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradle/wrapper/gradle-wrapper.jar"
  else
    echo "ERROR: curl or wget is required to fetch the Gradle wrapper JAR." >&2
    exit 1
  fi
fi

exec java -classpath "${WRAPPER_JAR}" org.gradle.wrapper.GradleWrapperMain "$@"
