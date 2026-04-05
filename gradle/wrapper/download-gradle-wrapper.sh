#!/bin/bash
###############################################################################
# ViewSyncApp - Automatic Gradle Wrapper Setup
# 
# This script intelligently downloads gradle-wrapper.jar using multiple methods
# Run it once before building the project
###############################################################################

set -o pipefail

WRAPPER_DIR="$(cd "$(dirname "$0")" && pwd)"
WRAPPER_JAR="$WRAPPER_DIR/gradle-wrapper.jar"
GRADLE_VERSION="8.5"
MIN_SIZE=10000  # Minimum expected size in bytes

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}ViewSyncApp${NC} - Setting up Gradle Wrapper v$GRADLE_VERSION"
echo "================================================================"

# Check if already present and valid
if [ -f "$WRAPPER_JAR" ]; then
    CURRENT_SIZE=$(stat -c%s "$WRAPPER_JAR" 2>/dev/null || stat -f%z "$WRAPPER_JAR" 2>/dev/null || echo 0)
    if [ "$CURRENT_SIZE" -gt "$MIN_SIZE" ]; then
        echo -e "${GREEN}✓${NC} gradle-wrapper.jar already exists ($(($CURRENT_SIZE/1024)) KB)"
        exit 0
    else
        echo -e "${YELLOW}!${NC} Existing file too small ($CURRENT_SIZE bytes), re-downloading..."
    fi
fi

# Function to try downloading with a specific tool
try_download() {
    local url="$1"
    local tool="$2"
    
    case "$tool" in
        curl)
            curl -L --connect-timeout 15 --max-time 120 -s --insecure \
                --retry 3 --retry-delay 2 \
                -o "$WRAPPER_JAR" "$url" 2>/dev/null
            ;;
        wget)
            wget --timeout=120 --tries=3 --wait=2 \
                --no-check-certificate -q \
                -O "$WRAPPER_JAR" "$url" 2>/dev/null
            ;;
    esac
}

# List of mirrors
MIRRORS=(
    "https://raw.githubusercontent.com/gradle/gradle/v${GRADLE_VERSION}/gradle/wrapper/gradle-wrapper.jar"
    "https://cdn.jsdelivr.net/gh/gradle/gradle@v${GRADLE_VERSION}/gradle/wrapper/gradle-wrapper.jar"
    "https://fastdl.mongodb.org/tools/gradle-wrapper-${GRADLE_VERSION}.jar"
)

# Try each mirror with each available tool
TOOLS=()
command -v curl >/dev/null 2>&1 && TOOLS+=("curl")
command -v wget >/dev/null 2>&1 && TOOLS+=("wget")

if [ ${#TOOLS[@]} -eq 0 ]; then
    echo -e "${RED}✗${NC} No download tools found (need curl or wget)"
    exit 1
fi

for mirror in "${MIRRORS[@]}"; do
    for tool in "${TOOLS[@]}"; do
        echo -n "  Trying $tool: $(basename $(dirname $(dirname $mirror)))/... "
        if try_download "$mirror" "$tool"; then
            if [ -f "$WRAPPER_JAR" ]; then
                SIZE=$(stat -c%s "$WRAPPER_JAR" 2>/dev/null || echo 0)
                if [ "$SIZE" -gt "$MIN_SIZE" ]; then
                    echo -e "${GREEN}✓ Success${NC} ($(($SIZE/1024)) KB)"
                    exit 0
                else
                    echo -e "${RED}✗ Too small${NC} ($SIZE bytes)"
                fi
            else
                echo -e "${RED}✗ Failed${NC}"
            fi
        else
            echo -e "${RED}✗ Failed${NC}"
        fi
    done
done

# Fallback: Try to extract from full Gradle distribution
echo ""
echo "Trying full Gradle distribution extraction..."
TEMP_DIR=$(mktemp -d 2>/dev/null || mktemp -d -t 'gradle')
TEMP_ZIP="$TEMP_DIR/gradle.zip"

for tool in "${TOOLS[@]}"; do
    echo -n "  Downloading Gradle $GRADLE_VERSION with $tool... "
    if try_download "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" "$tool"; then
        if [ -f "$TEMP_ZIP" ] && [ $(stat -c%s "$TEMP_ZIP" 2>/dev/null || echo 0) -gt 1000000 ]; then
            echo -e "${GREEN}✓ Downloaded${NC}"
            echo -n "  Extracting gradle-wrapper.jar... "
            
            # Extract the wrapper JAR
            if command -v unzip >/dev/null 2>&1; then
                unzip -q -j "$TEMP_ZIP" "gradle-${GRADLE_VERSION}/lib/plugins/gradle-wrapper-${GRADLE_VERSION}.jar" \
                    -d "$WRAPPER_DIR" 2>/dev/null && \
                    mv "$WRAPPER_DIR/gradle-wrapper-${GRADLE_VERSION}.jar" "$WRAPPER_JAR" 2>/dev/null
            fi
            
            rm -rf "$TEMP_DIR"
            
            if [ -f "$WRAPPER_JAR" ] && [ $(stat -c%s "$WRAPPER_JAR" 2>/dev/null || echo 0) -gt "$MIN_SIZE" ]; then
                SIZE=$(stat -c%s "$WRAPPER_JAR")
                echo -e "${GREEN}✓ Success${NC} ($(($SIZE/1024)) KB)"
                exit 0
            else
                echo -e "${RED}✗ Extraction failed${NC}"
            fi
        else
            echo -e "${RED}✗ Download too small${NC}"
        fi
    else
        echo -e "${RED}✗ Failed${NC}"
    fi
done

rm -rf "$TEMP_DIR" 2>/dev/null

# Final failure message with helpful instructions
echo ""
echo -e "${RED}================================================================${NC}"
echo -e "${RED}✗ Could not download gradle-wrapper.jar automatically${NC}"
echo -e "${RED}================================================================${NC}"
echo ""
echo "Manual download required:"
echo "  1. Download this file (43 KB):"
echo "     https://raw.githubusercontent.com/gradle/gradle/v${GRADLE_VERSION}/gradle/wrapper/gradle-wrapper.jar"
echo ""
echo "  2. Save it to:"
echo "     $WRAPPER_JAR"
echo ""
echo "  3. Or simply open the project in Android Studio - it will download automatically!"
echo ""
exit 1
