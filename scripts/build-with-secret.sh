#!/bin/bash

# Build script that securely uses your YouTube API key without storing it in local.properties
# This script prompts for the API key at build time

echo "========================================="
echo "ViewSyncApp - Secure Build Script"
echo "========================================="
echo ""

# Check if YOUTUBE_API_KEY is already set in environment
if [ -n "$YOUTUBE_API_KEY" ]; then
    echo "✓ Using YOUTUBE_API_KEY from environment variable"
    API_KEY="$YOUTUBE_API_KEY"
else
    # Prompt for API key
    echo "⚠ Enter your YouTube API Key (input will be hidden):"
    read -s API_KEY
    echo ""
    
    if [ -z "$API_KEY" ]; then
        echo "❌ Error: API key cannot be empty"
        echo "Build cancelled."
        exit 1
    fi
fi

echo "✓ API key received (length: ${#API_KEY} characters)"
echo ""

# Validate API key format (should be alphanumeric with possible underscores/hyphens)
if [[ ! "$API_KEY" =~ ^[A-Za-z0-9_-]+$ ]]; then
    echo "⚠ Warning: API key format looks unusual"
    echo "Continue anyway? (y/n)"
    read -r response
    if [[ ! "$response" =~ ^[Yy]$ ]]; then
        echo "Build cancelled."
        exit 1
    fi
fi

# Create temporary local.properties
echo "youtube.api.key=$API_KEY" > local.properties
echo "✓ Created temporary local.properties"
echo ""

# Build the app
echo "🔨 Building debug APK..."
./gradlew assembleDebug --stacktrace

# Capture the exit code
BUILD_EXIT_CODE=$?

# Clean up the API key from local.properties
echo ""
echo "🔒 Cleaning up sensitive data..."
echo "youtube.api.key=CLEANED" > local.properties
echo "✓ API key removed from local.properties"
echo ""

if [ $BUILD_EXIT_CODE -eq 0 ]; then
    echo "✅ Build successful!"
    echo ""
    echo "APK location: app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "To install on connected device:"
    echo "  adb install app/build/outputs/apk/debug/app-debug.apk"
else
    echo "❌ Build failed with exit code $BUILD_EXIT_CODE"
    echo "Check the error messages above for details."
fi

exit $BUILD_EXIT_CODE
