# Debug Logging System - Implementation Summary

## ✅ What Was Implemented

I've added a **comprehensive in-app debug logging system** to help you identify exactly where video playback is failing, without relying on Android logcat.

### Files Created/Modified:

#### **New Files:**
1. ✅ `app/src/main/java/com/youkhainda/viewsync/util/DebugLogger.kt`
   - Centralized logging utility
   - In-memory log buffer (500 entries max)
   - Color-coded severity levels
   - Step tracking with success/failure markers
   - Export and share functionality

2. ✅ `app/src/main/java/com/youkhainda/viewsync/ui/screen/DebugOverlay.kt`
   - In-app debug panel UI
   - Real-time log display
   - Auto-scrolling
   - Copy/Share/Clear buttons
   - Floating toggle button

3. ✅ `DEBUG_GUIDE.md`
   - Complete usage guide
   - Troubleshooting steps
   - Error code reference

#### **Modified Files:**
4. ✅ `app/src/main/java/com/youkhainda/viewsync/ui/screen/SyncPlayerScreen.kt`
   - Added DebugLogger import
   - Added logging to SyncPlayerScreen (session loading, UI states)
   - Added logging to VideoPlayerCard (video validation, player lifecycle)
   - Added logging to YouTubePlayerViewContainer (initialization, WebView, callbacks)
   - Added logging to PlayerControllerImpl (play/pause/seek/register)
   - Integrated DebugOverlay UI with toggle button

5. ✅ `app/src/main/java/com/youkhainda/viewsync/ui/viewmodel/SyncViewModels.kt`
   - Added DebugLogger import
   - Added step tracking to loadSyncSession()
   - Added logging to all ViewModel methods (play, pause, seek, recordSyncCue, etc.)

6. ✅ `app/src/main/java/com/youkhainda/viewsync/data/repository/SyncRepository.kt`
   - Added DebugLogger import
   - Added logging to initialize()
   - Added logging to searchYouTubeVideos() and fetchVideoDetails()
   - Added logging to all session management methods
   - Added logging to calculateVideoOffsets()

## 🎯 What You'll See Now

### In the App UI:
- **Green bug icon** 🐛 in bottom-right corner of SyncPlayerScreen
- **Tap it** to open the debug overlay panel
- **Panel shows**:
  - Timestamped log entries (HH:MM:SS.mmm)
  - Color-coded by severity:
    - 🔵 Blue = DEBUG (normal operations)
    - 🟢 Green = INFO (successful operations)
    - 🟡 Yellow = WARN (potential issues)
    - 🔴 Red = ERROR (failures)
  - Step indicators (Step 1/3, Step 2/3, etc.)
  - Success markers (✓)
  - Failure markers (✗)

### Log Categories You'll See:

#### **Session Loading:**
```
[INFO] [SyncPlayerScreen] Loading session: <id>
[INFO] [SyncPlayerVM] loadSyncSession - Step 1/3
[INFO] [SyncPlayerVM] Session loaded successfully - Videos: 2, Cues: 1
```

#### **Video Player Initialization:**
```
[DEBUG] [VideoPlayerCard] Video 0: ID=abc123, Offset=0 ms, Valid=true
[INFO] [YouTubePlayerView] Video 0: Initializing player - ID: abc123, Offset: 0ms
[DEBUG] [YouTubePlayerView] Video 0: Creating YouTubePlayerView instance
[DEBUG] [YouTubePlayerView] Video 0: WebView found: true
[DEBUG] [YouTubePlayerView] Video 0: WebView settings configured
[INFO] [YouTubePlayerListener] Video 0: onReady callback triggered
[INFO] [YouTubePlayerListener] Video 0: Cueing video at 0.0s
```

#### **Errors (if any):**
```
[ERROR] [YouTubePlayerListener] Video 0: onError callback - ERROR_100
[ERROR] [VideoPlayerCard] Video 0: Player error - Code: 100, Message: Video not found...
```

#### **Playback Controls:**
```
[INFO] [PlayerController] playAll() called - 2 players registered
[DEBUG] [YouTubePlayerListener] Video 0: State changed to PLAYING
```

## 🔍 How to Use on Your Vivo Y21

### Step 1: Build and Install the Updated APK
```bash
# In AndroidIDE or your build environment:
./gradlew assembleDebug
```

### Step 2: Open a Sync Session
- Launch the app
- Search for videos and create a session
- Navigate to the player screen

### Step 3: Open Debug Overlay
- Look for the **green bug icon** in bottom-right
- Tap it to open the debug panel

### Step 4: Watch What Happens
The panel will show you **exactly** where things are happening:

✅ **If videos load successfully**, you'll see:
- Session loading steps completing
- Video players initializing
- "onReady callback triggered" for each video
- Players registering with controller
- State changes to PLAYING when you press play

❌ **If something fails**, you'll see:
- Where it stopped (last log entry)
- Error code and message in RED
- Which step failed (with ✗ marker)

### Step 5: Identify the Problem
Check the **last few entries** in the log:

**Problem: Video doesn't load at all**
- Look for: `Invalid video ID format` or `WebView not found`
- Solution: Check video ID format

**Problem: Player doesn't start**
- Look for: Missing "onReady callback triggered"
- Solution: Check internet connection

**Problem: Error codes appear**
- **Error 5**: Video can't play in embedded player
- **Error 100**: Video not found/private
- **Error 101/150**: Embedding not allowed
- **Error 152**: Domain/copyright restriction

**Problem: Players don't sync**
- Look for: Offset calculation issues
- Solution: Record sync cues at same moment in all videos

### Step 6: Export Logs (Optional)
- Tap 📋 **Copy** to copy logs to clipboard
- Tap 📤 **Share** to send via email/messaging
- Use this to share with developers or for your own records

## 🎨 Visual Features

### Debug Toggle Button:
- **Green** = Debug overlay is currently visible
- **Gray** = Debug overlay is hidden
- **Location** = Bottom-right corner with 16dp padding

### Debug Panel:
- **70% screen height** dialog
- **Dark theme** (#1E1E1E background)
- **Auto-scrolls** to latest entry
- **Color-coded** entries with icons (V/D/I/W/E)
- **Monospace font** for log text

### Action Buttons:
- 📋 **Copy** - Copies all logs to clipboard
- 📤 **Share** - Opens Android share intent
- 🗑️ **Clear** - Empties the log buffer
- ✕ **Close** - Hides the panel

## 📊 What Gets Logged

### Logged Events (by category):

**UI Layer (SyncPlayerScreen):**
- Session ID being loaded
- UI state transitions (Loading → Success/Error)
- Video card creation with ID/offset/validation
- Player ready/error events
- User actions (retry, watch on YouTube)
- Player registration/unregistration

**ViewModel Layer (SyncPlayerViewModel):**
- Method calls with parameters
- Step-by-step session loading
- Play/pause/seek actions
- Sync cue operations
- Offset recalculations

**Repository Layer (SyncRepository):**
- Repository initialization
- DataStore load/save operations
- YouTube API calls (search, details)
- API response counts
- Session CRUD operations
- Sync cue operations
- Offset calculations

**Player Layer (YouTubePlayerView):**
- Player initialization
- WebView detection and configuration
- IFramePlayerOptions setup
- Listener callbacks (onReady, onError, onStateChange, onCurrentSecond)
- Player release/cleanup

**Controller Layer (PlayerController):**
- playAll/pauseAll/seekAll calls
- Player registration/unregistration
- Player counts

## 🚀 Performance

- **Memory**: ~50-100KB for log buffer
- **CPU**: Negligible (string formatting only)
- **Battery**: No impact (no background processes)
- **Network**: No impact (logs are local)
- **Storage**: No impact (in-memory only)

## 🔧 Customization

### Change Log Buffer Size:
```kotlin
// In DebugLogger.kt, line 18:
private const val MAX_LOG_SIZE = 500 // Change this value
```

### Disable Logging Completely:
```kotlin
// In DebugLogger.kt, line 24:
var isEnabled: Boolean = false // Change to false
```

### Change Log Colors:
```kotlin
// In DebugOverlay.kt, line 167-172:
DebugLogger.LogLevel.ERROR -> Color(0xFFF87171) // Change hex color
```

## 📝 Example Debug Session

Here's what a successful session looks like:

```
[14:23:45.123] [I] [SyncPlayerScreen] Loading session: abc-123-def
[14:23:45.145] [I] [SyncPlayerVM] loadSyncSession - Step 1/3
[14:23:45.167] [I] [SyncPlayerVM] Fetching session from repository - Step 2/3
[14:23:45.189] [D] [SyncRepository] getSyncSession() - ID: abc-123-def
[14:23:45.201] [D] [SyncRepository] Session found - Videos: 2, Cues: 1
[14:23:45.223] [I] [SyncPlayerVM] Calculating video offsets - Step 3/3
[14:23:45.245] [D] [SyncRepository] calculateVideoOffsets() - Session: abc-123-def, BaseIndex: 0
[14:23:45.267] [I] [SyncRepository] Offsets calculated: {0=0, 1=5000}
[14:23:45.289] [I] [SyncPlayerVM] Session loaded successfully - Videos: 2, Cues: 1
[14:23:45.301] [I] [SyncPlayerScreen] UI State: Success - Session: Test Session, Videos: 2, Sync Cues: 1
[14:23:45.323] [D] [VideoPlayerCard] Video 0: ID=dQw4w9WgXcQ, Offset=0 ms, Valid=true
[14:23:45.345] [D] [VideoPlayerCard] Video 1: ID=jNQXAC9IVRw, Offset=5000 ms, Valid=true
[14:23:45.367] [I] [YouTubePlayerView] Video 0: Initializing player - ID: dQw4w9WgXcQ, Offset: 0ms (0.0s)
[14:23:45.389] [I] [YouTubePlayerView] Video 1: Initializing player - ID: jNQXAC9IVRw, Offset: 5000ms (5.0s)
[14:23:46.123] [I] [YouTubePlayerListener] Video 0: onReady callback triggered
[14:23:46.145] [I] [YouTubePlayerListener] Video 0: Cueing video at 0.0s
[14:23:46.167] [I] [VideoPlayerCard] Video 0: Player ready, registered with controller
[14:23:46.189] [D] [PlayerController] Player registered at index 0 - Total: 1
[14:23:46.523] [I] [YouTubePlayerListener] Video 1: onReady callback triggered
[14:23:46.545] [I] [YouTubePlayerListener] Video 1: Cueing video at 5.0s
[14:23:46.567] [I] [VideoPlayerCard] Video 1: Player ready, registered with controller
[14:23:46.589] [D] [PlayerController] Player registered at index 1 - Total: 2
```

Then when you press play:
```
[14:24:10.123] [I] [PlayerController] playAll() called - 2 players registered
[14:24:10.145] [D] [YouTubePlayerListener] Video 0: State changed to PLAYING
[14:24:10.167] [D] [YouTubePlayerListener] Video 1: State changed to PLAYING
```

## 🎉 You're All Set!

Now you can:
1. **See exactly what's happening** at every step
2. **Identify where things fail** with specific error codes
3. **Share logs easily** without logcat
4. **Troubleshoot on your Vivo Y21** without any external tools

The debug overlay works **completely in-app** - no logcat, no ADB, no computer needed!

---

**Happy debugging! 🐛✨**
