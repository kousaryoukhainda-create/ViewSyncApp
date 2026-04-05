#!/bin/bash
#
# Gradle Wrapper Bootstrap Script
# This script is automatically run by Android Studio when syncing the project
# It ensures gradle-wrapper.jar is present
#

set -e

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WRAPPER_DIR="$PROJECT_DIR/gradle/wrapper"
WRAPPER_JAR="$WRAPPER_DIR/gradle-wrapper.jar"
GRADLE_VERSION="8.5"

# Check if already present
if [ -f "$WRAPPER_JAR" ] && [ $(stat -f%z "$WRAPPER_JAR" 2>/dev/null || stat -c%s "$WRAPPER_JAR" 2>/dev/null || echo 0) -gt 10000 ]; then
    exit 0
fi

echo "ViewSyncApp: Downloading Gradle Wrapper JAR (v$GRADLE_VERSION)..."

mkdir -p "$WRAPPER_DIR"

# Download from multiple sources
download_jar() {
    local url="$1"
    if command -v curl >/dev/null 2>&1; then
        curl -L --connect-timeout 10 --max-time 60 -s "$url" -o "$WRAPPER_JAR"
    elif command -v wget >/dev/null 2>&1; then
        wget --timeout=60 -q -O "$WRAPPER_JAR" "$url"
    else
        return 1
    fi
}

# Try GitHub raw (most reliable)
download_jar "https://raw.githubusercontent.com/gradle/gradle/v${GRADLE_VERSION}/gradle/wrapper/gradle-wrapper.jar" && \
    [ -f "$WRAPPER_JAR" ] && [ $(stat -c%s "$WRAPPER_JAR" 2>/dev/null || echo 0) -gt 10000 ] && {
        echo "ViewSyncApp: Gradle Wrapper JAR downloaded successfully"
        exit 0
    }

# Try Gradle Services (official)
TEMP_ZIP="/tmp/gradle-${GRADLE_VERSION}-wrapper-temp.zip"
if curl -L --connect-timeout 10 --max-time 120 -s \
    "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "$TEMP_ZIP" && \
    [ -f "$TEMP_ZIP" ]; then
    unzip -q -j "$TEMP_ZIP" "gradle-${GRADLE_VERSION}/lib/gradle-wrapper-shared-*.jar" "$WRAPPER_DIR/" 2>/dev/null || \
    unzip -q -l "$TEMP_ZIP" | grep "gradle-wrapper" | head -1 | awk '{print $NF}' | xargs -I {} \
        unzip -q -j "$TEMP_ZIP" "{}" -d "$WRAPPER_DIR/"
    rm -f "$TEMP_ZIP"
    [ -f "$WRAPPER_JAR" ] && [ $(stat -c%s "$WRAPPER_JAR" 2>/dev/null || echo 0) -gt 10000 ] && {
        echo "ViewSyncApp: Gradle Wrapper JAR extracted from distribution"
        exit 0
    }
fi

echo "ViewSyncApp WARNING: Could not download gradle-wrapper.jar automatically"
echo "  Please download manually from:"
echo "    https://raw.githubusercontent.com/gradle/gradle/v${GRADLE_VERSION}/gradle/wrapper/gradle-wrapper.jar"
echo "  And place it in:"
echo "    $WRAPPER_JAR"
echo "  Or simply open the project in Android Studio and let it sync automatically."

exit 1
