# Setup for /storage/internal_new/project/ViewSyncApp

Your project directory is at a specific location on your system. Here's how to extract the archive there.

---

## 📍 Your Target Directory

```
/storage/internal_new/project/ViewSyncApp
```

---

## 🔽 Download the Archive

Download **one of these** from Claude outputs:
- `ViewSyncApp.zip` (42 KB) - **Recommended for quick setup**
- `ViewSyncApp.tar.gz` (78 KB) - If you want git history included

---

## 💾 Extract to Your Location

### Method 1: Extract the ZIP (Easiest)

**Using File Manager:**
1. Download `ViewSyncApp.zip`
2. Navigate to `/storage/internal_new/project/`
3. Right-click `ViewSyncApp.zip` → Extract
4. Wait for extraction to complete

**Using Command Line:**

```bash
# Navigate to the project directory
cd /storage/internal_new/project

# If there's already a ViewSyncApp folder, back it up first
# mv ViewSyncApp ViewSyncApp.backup

# Extract the ZIP
unzip ~/Downloads/ViewSyncApp.zip

# Or if you downloaded it elsewhere
unzip /path/to/ViewSyncApp.zip

# Verify
ls -la ViewSyncApp/
```

### Method 2: Extract TAR.GZ (With Git History)

```bash
cd /storage/internal_new/project

# Extract
tar -xzf ~/Downloads/ViewSyncApp.tar.gz

# Or
tar -xzf /path/to/ViewSyncApp.tar.gz

# Verify
ls -la ViewSyncApp/
```

---

## ✅ Verify Extraction

After extracting, verify everything is in place:

```bash
cd /storage/internal_new/project/ViewSyncApp

# List key files
ls -la

# Should show:
# - build.gradle.kts
# - README.md
# - src/ (directory)
# - .gitignore
# - QUICK_START.md
# - IMPLEMENTATION_GUIDE.md
# - etc.

# Verify source code
ls -la src/main/java/com/youkhainda/viewsync/

# Should show:
# - MainActivity.kt
# - data/ (directory)
# - ui/ (directory)
# - di/ (directory)
```

---

## 🔧 Initialize Git (if using ZIP)

If you extracted the ZIP file, initialize git:

```bash
cd /storage/internal_new/project/ViewSyncApp

# Initialize
git init

# Configure
git config user.name "Your Name"
git config user.email "your.email@github.com"

# Add all files
git add -A

# Create initial commit
git commit -m "Initial commit: ViewSync Android App

Features:
- Multi-video YouTube player with synchronized playback
- Audio cue-based syncing with offset calculation
- YouTube API v3 integration
- Material Design 3 UI with Jetpack Compose
- MVVM architecture with Hilt DI
- Complete documentation"
```

---

## 📤 Push to GitHub

Once extracted and in place:

```bash
cd /storage/internal_new/project/ViewSyncApp

# Add GitHub remote
git remote add origin https://github.com/kousaryoukhainda-create/ViewSyncApp.git

# Rename branch to main
git branch -M main

# Push to GitHub
git push -u origin main
```

When prompted for password:
1. Go to https://github.com/settings/tokens
2. Generate new token (classic)
3. Select `repo` scope
4. Copy token
5. Paste when prompted

---

## 📂 Directory Structure After Extraction

```
/storage/internal_new/project/ViewSyncApp/
├── README.md
├── QUICK_START.md
├── IMPLEMENTATION_GUIDE.md
├── PROJECT_STRUCTURE.md
├── GITHUB_SETUP.md
├── EXTRACTION_INSTRUCTIONS.md
├── COMMIT_SUMMARY.md
├── build.gradle.kts
├── AndroidManifest.xml
├── .gitignore
├── .git/ (if TAR.GZ or after git init)
└── src/
    └── main/
        ├── java/com/youkhainda/viewsync/
        │   ├── MainActivity.kt
        │   ├── data/
        │   ├── ui/
        │   └── di/
        └── AndroidManifest.xml
```

---

## 🚀 Next Steps After Extraction

### 1. Open in Android Studio

```
Android Studio:
  File → Open → Navigate to:
  /storage/internal_new/project/ViewSyncApp → Select → OK
```

Or from terminal:

```bash
cd /storage/internal_new/project/ViewSyncApp
# Open with Android Studio
open -a "Android Studio" .  # Mac
# or just open the folder in Android Studio manually
```

### 2. Sync Gradle

Android Studio will prompt automatically, or:
```
Gradle → Sync Now
```

### 3. Get YouTube API Key

1. Go to https://console.cloud.google.com
2. Create new project
3. Search "YouTube Data API v3"
4. Click Enable
5. Go to Credentials
6. Create API Key
7. Copy the key

### 4. Add API Key

Edit `/storage/internal_new/project/ViewSyncApp/build.gradle.kts`:

Find this line:
```kotlin
buildConfigField("String", "YOUTUBE_API_KEY", "\"YOUR_YOUTUBE_API_KEY\"")
```

Replace with your actual key:
```kotlin
buildConfigField("String", "YOUTUBE_API_KEY", "\"AIzaSyC_YOUR_ACTUAL_KEY_HERE\"")
```

### 5. Build

```bash
cd /storage/internal_new/project/ViewSyncApp
./gradlew build
```

### 6. Run

Android Studio: Run → Run 'app' (or Shift+F10)

---

## 🔄 Quick Command Reference

```bash
cd /storage/internal_new/project/ViewSyncApp

# Check git status
git status

# View commit history
git log --oneline

# View remotes
git remote -v

# Check gradle
./gradlew --version

# Build
./gradlew build

# Run tests
./gradlew test
```

---

## 🐛 Troubleshooting

### "File not found" error
Make sure you're in the correct directory:
```bash
pwd  # Should show: /storage/internal_new/project/ViewSyncApp
ls -la  # Should show project files
```

### "Permission denied" on Linux
```bash
chmod -R 755 /storage/internal_new/project/ViewSyncApp
```

### Android Studio can't find gradle
```bash
cd /storage/internal_new/project/ViewSyncApp
chmod +x gradlew
```

### Git command not found
Install git:
```bash
# Ubuntu/Debian
sudo apt install git

# Mac
brew install git

# Or download from https://git-scm.com
```

### Cannot push to GitHub
1. Create repository on https://github.com/new
2. Use personal access token (not password)
3. Generate at https://github.com/settings/tokens

---

## 📋 Extraction Checklist

- [ ] Download `ViewSyncApp.zip` or `ViewSyncApp.tar.gz`
- [ ] Navigate to `/storage/internal_new/project/`
- [ ] Extract archive there
- [ ] Verify all files present: `ls -la ViewSyncApp/`
- [ ] Verify git (if TAR.GZ): `cd ViewSyncApp && git log`
- [ ] If ZIP, initialize git: `git init && git add . && git commit -m "..."`
- [ ] Add remote: `git remote add origin https://...`
- [ ] Configure API key in `build.gradle.kts`
- [ ] Open in Android Studio
- [ ] Sync Gradle
- [ ] Build: `./gradlew build`
- [ ] Run on emulator/device

---

## ✨ Final Check

Once extracted, run this to verify everything:

```bash
cd /storage/internal_new/project/ViewSyncApp

echo "Checking files..."
test -f build.gradle.kts && echo "✓ build.gradle.kts"
test -f README.md && echo "✓ README.md"
test -d src && echo "✓ src directory"
test -f .gitignore && echo "✓ .gitignore"

echo ""
echo "Checking git..."
git log --oneline -1

echo ""
echo "Checking kotlin files..."
find src -name "*.kt" | wc -l
echo "Kotlin files found (should be 11)"

echo ""
echo "✅ All good! Ready to build."
```

---

## 🎉 You're Ready!

Once extracted to `/storage/internal_new/project/ViewSyncApp`, you have:
- ✅ Complete Android app source code
- ✅ Build configuration (gradle)
- ✅ All dependencies configured
- ✅ Material Design 3 theme
- ✅ Complete documentation
- ✅ Git repository (ready to push)

**Next:** Open in Android Studio and follow `QUICK_START.md` 📖

---

**Questions?** Check the included documentation files!
