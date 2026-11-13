#!/usr/bin/env bash
# Generates the Gradle wrapper (gradlew, gradlew.bat, gradle-wrapper.jar) for
# command-line builds. Android Studio does not need this; it reads
# gradle/wrapper/gradle-wrapper.properties directly.
#
# Requires a system Gradle once:  brew install gradle
set -euo pipefail

GRADLE_VERSION="9.4.1"
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WORK_DIR="$(mktemp -d)"

# Generate in an empty scratch project so the system Gradle never has to
# evaluate this project's Android build.
touch "$WORK_DIR/settings.gradle"
(cd "$WORK_DIR" && gradle wrapper --gradle-version "$GRADLE_VERSION" --distribution-type bin)

cp "$WORK_DIR/gradlew" "$WORK_DIR/gradlew.bat" "$PROJECT_DIR/"
cp "$WORK_DIR/gradle/wrapper/gradle-wrapper.jar" "$PROJECT_DIR/gradle/wrapper/"
chmod +x "$PROJECT_DIR/gradlew"

echo "Wrapper for Gradle $GRADLE_VERSION installed. Try: ./gradlew assembleDebug"
