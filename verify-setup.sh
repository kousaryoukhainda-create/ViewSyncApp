#!/bin/bash
# Verify all required files are present

echo "=== PROJECT STRUCTURE ==="
echo ""
echo "Source Files:"
find app/src/main -type f \( -name "*.kt" -o -name "*.xml" \) | sort | sed 's|^app/src/main/||'

echo ""
echo "Build Files:"
ls -1 *.kts *.properties gradle/wrapper/*.properties gradlew* 2>/dev/null

echo ""
echo "=== FILE COUNTS ==="
echo "Kotlin files: $(find app/src/main -name '*.kt' | wc -l)"
echo "XML resources: $(find app/src/main/res -name '*.xml' | wc -l)"

echo ""
echo "=== MISSING FILES CHECK ==="
MISSING=0
for f in \
    "settings.gradle.kts" \
    "build.gradle.kts" \
    "app/build.gradle.kts" \
    "gradle.properties" \
    "gradle/wrapper/gradle-wrapper.properties" \
    "gradlew" \
    "gradlew.bat" \
    "app/proguard-rules.pro" \
    "app/src/main/res/values/strings.xml" \
    "app/src/main/res/values/colors.xml" \
    "app/src/main/res/values/themes.xml" \
    "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml" \
    "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml" \
    "app/src/main/res/drawable/ic_launcher_foreground.xml" \
    "app/src/main/java/com/youkhainda/viewsync/ViewSyncApplication.kt" \
    "app/src/main/AndroidManifest.xml" \
    "app/src/main/java/com/youkhainda/viewsync/data/model/Models.kt" \
    "app/src/main/java/com/youkhainda/viewsync/data/remote/YouTubeApiService.kt" \
    "app/src/main/java/com/youkhainda/viewsync/data/repository/SyncRepository.kt" \
    "app/src/main/java/com/youkhainda/viewsync/di/AppModule.kt"
do
    if [ -f "$f" ]; then
        echo "✓ $f"
    else
        echo "✗ $f MISSING"
        MISSING=$((MISSING + 1))
    fi
done

echo ""
if [ $MISSING -eq 0 ]; then
    echo "✅ ALL REQUIRED FILES PRESENT!"
else
    echo "⚠️  $MISSING files missing"
fi

echo ""
echo "=== GRADLE WRAPPER JAR STATUS ==="
if [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    SIZE=$(stat -c%s "gradle/wrapper/gradle-wrapper.jar" 2>/dev/null || echo 0)
    if [ "$SIZE" -gt 10000 ]; then
        echo "✓ gradle-wrapper.jar present ($(($SIZE/1024)) KB)"
    else
        echo "! gradle-wrapper.jar exists but too small ($SIZE bytes)"
        echo "  Run: bash gradle/wrapper/download-gradle-wrapper.sh"
    fi
else
    echo "! gradle-wrapper.jar not present (will auto-download in Android Studio)"
    echo "  Or run: bash gradle/wrapper/download-gradle-wrapper.sh"
fi
