# ViewSyncApp - First Time Setup

## Automatic Setup

When you open this project in **Android Studio**, it will automatically:
1. Detect the missing `gradle-wrapper.jar`
2. Download Gradle 8.5 from the official distribution
3. Set up the wrapper automatically

**Just open the project and wait for the sync to complete!**

## Manual Setup (If Automatic Fails)

If Android Studio cannot download Gradle automatically, run one of these commands:

### Option 1: Run Bootstrap Script
```bash
cd /path/to/ViewSyncApp
bash gradle/bootstrap-gradle-wrapper.sh
```

### Option 2: Download Manually
Download `gradle-wrapper.jar` (43 KB) from:
```
https://raw.githubusercontent.com/gradle/gradle/v8.5.0/gradle/wrapper/gradle-wrapper.jar
```

Place it in:
```
gradle/wrapper/gradle-wrapper.jar
```

### Option 3: Install Gradle System-Wide
```bash
# Ubuntu/Debian
sudo apt install gradle

# macOS
brew install gradle

# Then run
gradle wrapper
```

## Verify Setup

After setup, verify the file exists:
```bash
ls -lh gradle/wrapper/gradle-wrapper.jar
# Should show ~43 KB
```

Then build the project:
```bash
./gradlew build
```

## Troubleshooting

### "gradle-wrapper.jar not found"
- Run: `bash gradle/bootstrap-gradle-wrapper.sh`
- Or download manually as shown above

### "Connection refused" errors
- Check your internet connection
- Try a different network
- Use a VPN if GitHub is blocked in your region

### "SSL certificate problem"
- Update your system's CA certificates:
  ```bash
  sudo apt install ca-certificates
  sudo update-ca-certificates
  ```

### Still having issues?
1. Open Android Studio
2. File → Settings → Build, Execution, Deployment → Build Tools → Gradle
3. Change "Gradle JDK" to a different JDK
4. Click "Apply" and try syncing again

---

**Note:** This only needs to be done **once**. After the initial setup, the project will work offline.
