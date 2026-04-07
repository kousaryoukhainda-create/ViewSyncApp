# ViewSyncApp Debug Guide

## Overview
ViewSyncApp now includes a comprehensive **in-app debug logging system** that works without relying on Android logcat. This is especially useful on devices like Vivo Y21 where logcat access may be restricted.

## Features

### 1. **DebugLogger** - Centralized Logging Utility
Located at: `app/src/main/java/com/youkhainda/viewsync/util/DebugLogger.kt`

**Capabilities:**
- ✅ Logs to both Android Logcat AND in-memory buffer
- ✅ Color-coded severity levels (VERBOSE, DEBUG, INFO, WARN, ERROR)
- ✅ Timestamped entries (HH:MM:SS.mmm)
- ✅ Step tracking (Step 1/3, Step 2/3, etc.)
- ✅ Success/failure markers (✓ and ✗)
- ✅ Automatic buffer management (max 500 entries)
- ✅ Export and share logs

**Usage Examples:**
```kotlin
// Simple logging
DebugLogger.i("MyTag", "Something happened")
DebugLogger.e("MyTag", "Error occurred", exception)

// Step tracking
DebugLogger.step("MyTag", "Loading data", 1, 3)  // Step 1/3: Loading data
DebugLogger.stepSuccess("MyTag", "Data loaded", "100 items")  // ✓ Data loaded - 100 items
DebugLogger.stepFailed("MyTag", "Load failed", "Network error", exception)  // ✗ Load failed - Network error
```

### 2. **DebugOverlay** - In-App Debug Display
Located at: `app/src/main/java/com/youkhainda/viewsync/ui/screen/DebugOverlay.kt`

**Features:**
- 🟢 Floating debug button (bottom-right corner)
- 🟢 Real-time log display in a dialog
- 🟢 Auto-scrolls to latest entries
- 🟢 Color-coded by severity:
  - **Blue** = DEBUG
  - **Green** = INFO
  - **Yellow** = WARNING
  - **Red** = ERROR
- 🟢 Copy logs to clipboard
- 🟢 Share logs via intent
- 🟢 Clear log buffer

### 3. **Comprehensive Logging Coverage**

Logs have been added to all critical areas:

#### **SyncPlayerScreen** (Video Playback)
- Session loading
- UI state changes (Loading → Success/Error)
- Video player initialization
- Player lifecycle events (ready, error, state changes)
- Player registration/unregistration
- Video ID validation
- Error handling and retry actions

#### **SyncPlayerViewModel** (Business Logic)
- Session loading steps
- Play/pause/seek actions
- Sync cue recording
- Offset calculations
- Share link generation

#### **SyncRepository** (Data & API)
- Repository initialization
- DataStore operations
- YouTube API calls (search, video details)
- Session management (create, add videos, delete)
- Sync cue operations
- Offset calculations

## How to Use

### On Your Vivo Y21 Device:

1. **Open the ViewSyncApp** and navigate to any sync session

2. **Look for the green bug icon** 🐛 in the bottom-right corner
   - Green = Debug overlay is visible
   - Gray = Debug overlay is hidden

3. **Tap the bug icon** to toggle the debug overlay

4. **The debug panel will show:**
   - All logged events with timestamps
   - Color-coded severity levels
   - Step-by-step progress indicators
   - Error details with codes

5. **Use the action buttons:**
   - 📋 **Copy** - Copies all logs to clipboard
   - 📤 **Share** - Opens share dialog to send logs via email/messaging
   - 🗑️ **Delete** - Clears the log buffer
   - ✕ **Close** - Hides the overlay

### What to Look For When Videos Don't Play:

#### **Step 1: Check Session Loading**
Look for:
```
[INFO] [SyncPlayerScreen] Loading session: <session-id>
[INFO] [SyncPlayerScreen] UI State: Success - Session: <name>, Videos: X, Sync Cues: Y
```
✅ If you see this, session loaded correctly  
❌ If you see "Error" instead, check the error message

#### **Step 2: Check Video Player Initialization**
Look for entries like:
```
[DEBUG] [VideoPlayerCard] Video 0: ID=<video-id>, Offset=0 ms, Valid=true
[INFO] [YouTubePlayerView] Video 0: Initializing player - ID: <video-id>, Offset: 0ms (0.0s)
[DEBUG] [YouTubePlayerView] Video 0: Creating YouTubePlayerView instance
[DEBUG] [YouTubePlayerView] Video 0: Automatic initialization disabled
[DEBUG] [YouTubePlayerView] Video 0: WebView found: true
[DEBUG] [YouTubePlayerView] Video 0: WebView settings configured
[DEBUG] [YouTubePlayerView] Video 0: IFramePlayerOptions configured
```
✅ If you see these, player is being created  
❌ If it stops here, WebView initialization failed

#### **Step 3: Check Player Ready State**
Look for:
```
[INFO] [YouTubePlayerListener] Video 0: onReady callback triggered
[INFO] [YouTubePlayerListener] Video 0: Cueing video at 0.0s
[INFO] [VideoPlayerCard] Video 0: Player ready, registered with controller
[DEBUG] [PlayerController] Player registered at index 0 - Total: 1
```
✅ If you see this, player is ready  
❌ If you don't see "onReady", the player failed to initialize

#### **Step 4: Check for Errors**
Look for RED entries:
```
[ERROR] [YouTubePlayerListener] Video 0: onError callback - ERROR_100
[ERROR] [VideoPlayerCard] Video 0: Player error - Code: 100, Message: Video not found...
```

**Common Error Codes:**
- **5** = Video cannot be played in embedded player
- **100** = Video not found (removed/private)
- **101/150** = Video owner doesn't allow embedding
- **152** = Video restricted (domain/copyright)

#### **Step 5: Check Playback Controls**
When you press play:
```
[INFO] [PlayerController] playAll() called - 1 players registered
[DEBUG] [YouTubePlayerListener] Video 0: State changed to PLAYING
```

#### **Step 6: Check API Calls (if searching videos)**
```
[INFO] [SyncRepository] searchYouTubeVideos() - Query: '...'
[DEBUG] [SyncRepository] Performing YouTube API search
[DEBUG] [SyncRepository] API returned 25 results
[INFO] [SyncRepository] Successfully parsed 25 videos
```

## Troubleshooting Common Issues

### Issue: Videos Don't Load at All
**Check:**
1. Is the video ID valid? Look for: `Valid=true`
2. Did the player initialize? Look for: `onReady callback triggered`
3. Any error messages? Check for RED entries

**Solutions:**
- If video ID is invalid → Check the video ID format
- If player doesn't initialize → Check internet connection
- If error 100/101/150 → Video has restrictions, try a different video

### Issue: Videos Load But Don't Play Together
**Check:**
1. Are offsets calculated correctly?
   ```
   [INFO] [SyncRepository] Offsets calculated: {0=0, 1=5000, 2=-3000}
   ```
2. Are all players registered?
   ```
   [INFO] [PlayerController] playAll() called - 3 players registered
   ```

**Solutions:**
- If offsets are wrong → Record sync cues at the same moment in all videos
- If not all players registered → Wait for all players to load before pressing play

### Issue: App Crashes or Freezes
**Check:**
1. Look for exceptions in the logs (any entry with stack traces)
2. Check for repeated error patterns

**Solutions:**
- Copy the logs using the 📋 button
- Share them for analysis using the 📤 button

## Advanced: Export and Analyze Logs

### Export Logs:
1. Open debug overlay
2. Tap 📤 Share button
3. Choose email/messaging app
4. Send to yourself or developer

### Analyze Patterns:
- Look for timestamps gaps (indicates blocking operations)
- Check for repeated errors (indicates systemic issues)
- Verify step completion (all steps should show ✓)

## Performance Notes

- **Log buffer size**: 500 entries (auto-trimmed)
- **Memory impact**: Minimal (~50-100KB)
- **Performance impact**: Negligible (async operations)
- **Battery impact**: None (no background processes)

## Disabling Debug Logging

If you want to disable logging completely (not recommended during troubleshooting):

```kotlin
// In DebugLogger.kt, change:
var isEnabled: Boolean = true
// to:
var isEnabled: Boolean = false
```

## Next Steps

1. **Test with a simple video** - Use a well-known public video
2. **Watch the debug panel** - Observe each step
3. **Note where it fails** - Take a screenshot of the error
4. **Share the logs** - Use the export feature for analysis

---

**Remember**: Technology isn't sad - it's just waiting for the right debug logs! 🐛✨
