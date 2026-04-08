# YouTube Error 152-4 Fix Guide

## Problem
Error code 152-4 ("This video is unavailable") typically occurs when an embedded video player is blocked by security, privacy, or ad-blocking tools. YouTube uses scripts to check if resources are being blocked, and if these checks fail, the video is rendered unavailable.

## Root Causes
- **Ad-blockers & Privacy Extensions**: Tools like Adblock Plus, uBlock Origin, or browser privacy features
- **Missing Referrer Headers**: YouTube player expects proper referrer information
- **Origin Mismatch**: Incorrect origin configuration in iframe player
- **WebView Security Settings**: Overly restrictive WebView configuration
- **Cached Data**: Accumulated temporary files triggering playback errors

## Fixes Applied ✅

### 1. ✅ Updated IFrame Player Configuration
**File**: `app/src/main/java/com/youkhainda/viewsync/ui/screen/SyncPlayerScreen.kt`

**Changes**:
- Changed `origin` from `https://www.youtube.com` to `https://localhost` (prevents origin mismatch errors)
- Added `referrer` parameter set to `https://www.youtube.com`
- Added `widget_referrer` parameter for additional compatibility

```kotlin
playerVars: {
    'playsinline': 1,
    'controls': 1,
    'rel': 0,
    'modestbranding': 1,
    'enablejsapi': 1,
    'origin': 'https://localhost',
    'referrer': 'https://www.youtube.com',
    'widget_referrer': 'https://www.youtube.com'
},
```

### 2. ✅ Enhanced WebView Configuration
**Improvements**:
- Changed `cacheMode` from `LOAD_DEFAULT` to `LOAD_NO_CACHE` (prevents cached resource conflicts)
- Added `setAppCacheEnabled(false)` (deprecated but still effective)
- Added `javaScriptCanOpenWindowsAutomatically = true` (required for YouTube IFrame API)
- Added referrer policy meta tag: `<meta name="referrer" content="no-referrer-when-downgrade">`

### 3. ✅ Referrer Injection
**Implementation**:
- Inject YouTube referrer via JavaScript after page load:
```javascript
document.referrer = 'https://www.youtube.com';
```
- Added logging to verify referrer is set correctly

### 4. ✅ Enhanced Error Handling
**Features**:
- Added detailed error messages for all YouTube player error codes
- Visual error banners in UI with actionable solutions
- HTTP error logging to detect blocked resources
- Error code 152-4 specific guidance for users

```kotlin
val errorMessage = when (errorCode) {
    152 -> "Error 152-4: Embedding blocked. Try: disabling ad-blockers, clearing cache, or using test video dQw4w9WgXcQ"
    // ... other error codes
}
```

### 5. ✅ Improved Navigation Blocking
**Changes**:
- Added debug logging when blocking navigation away from YouTube
- Better error handling for HTTP errors (4xx, 5xx responses)
- Maintains player within allowed domains (youtube.com, googlevideo.com)

## User-Facing Solutions

### If Error 152-4 Persists:

1. **Test with Known Working Videos**:
   - `dQw4w9WgXcQ` (Rick Astley - Never Gonna Give You Up)
   - `jNQXAC9IVRw` (Me at the zoo - First YouTube video)
   - `9bZkp7q19f0` (PSY - GANGNAM STYLE)

2. **Clear App Data**:
   ```
   Settings → Apps → ViewSync → Storage → Clear Data
   ```

3. **Check Network Environment**:
   - Some networks (corporate, school) block YouTube embedding
   - Try different WiFi network or mobile data

4. **Disable System-Wide Ad Blockers**:
   - If using DNS-level ad blocking (Pi-hole, AdGuard DNS), temporarily disable
   - Check if device has system-wide ad blocking enabled

5. **Rebuild Project**:
   ```bash
   ./gradlew clean
   ./gradlew assembleDebug
   ```

## Developer Testing Checklist

- [x] Origin set to `https://localhost` (not `https://www.youtube.com`)
- [x] Referrer meta tag added to HTML
- [x] Referrer injected via JavaScript on page load
- [x] Cache mode set to `LOAD_NO_CACHE`
- [x] JavaScript enabled and can open windows automatically
- [x] Error handling displays user-friendly messages
- [x] HTTP errors are logged for debugging
- [x] Navigation blocking prevents leaving YouTube domains
- [ ] Test with multiple videos (some have embedding disabled by owner)
- [ ] Test on physical device (not just emulator)
- [ ] Check Logcat for detailed error messages

## Logcat Filtering

Filter for YouTube-related errors:
```
package:com.youkhainda.viewsync level:error (YouTube|Player|WebView|DirectYouTubeWebView)
```

**Common Error Codes**:
- `-1`: General playback error (authentication/network issue)
- `2`: Invalid parameter
- `5`: Content cannot be played in embedded player
- `100`: Video not found
- `101/150`: Video owner disabled embedding
- `152`: Domain/copyright restriction (THIS IS THE ERROR WE'RE FIXING)

## Additional Troubleshooting

### Verify Video Embedding Permissions
Some videos have embedding restrictions. Check if the video allows embedding:
1. Open video in browser
2. Try to embed it on a test page
3. If it fails, the video owner disabled embedding

### Check Google Cloud Console
1. **API Enabled**: Confirm "YouTube Data API v3" is enabled
2. **Quota Not Exceeded**: Check API quota usage
3. **API Key Valid**: Ensure key hasn't expired or been revoked

### Alternative: Use YouTube's Native Player
If embedding continues to fail, consider using the YouTube Android Player library (though it has limitations):
```kotlin
// Add to build.gradle.kts
implementation("com.google.android.youtube:youtube-android-player:1.2.2")
```

## Testing Procedure

1. **Build and install app**:
   ```bash
   ./gradlew clean assembleDebug installDebug
   ```

2. **Test with known embeddable videos**:
   - Create a sync session with `dQw4w9WgXcQ`
   - Verify video loads and plays

3. **Test with problematic videos**:
   - Try videos that previously showed error 152-4
   - Check if error banner appears with helpful message

4. **Monitor Logcat**:
   - Look for "DirectYouTubeWebView" logs
   - Check for HTTP errors or blocked resources

5. **Test multiple videos simultaneously**:
   - Add 2-3 videos to sync session
   - Verify all players load without errors

## Technical Details

### Why These Changes Work

1. **Origin Change**: Using `https://localhost` instead of `https://www.youtube.com` prevents YouTube from detecting a mismatch between the actual page origin and the declared origin.

2. **Referrer Policy**: The `no-referrer-when-downgrade` policy ensures YouTube receives proper referrer information when loading over HTTPS, preventing security checks from failing.

3. **Cache Disabled**: Using `LOAD_NO_CACHE` prevents stale resources from triggering embedding errors, especially after ad-blocker updates.

4. **JavaScript Window Opening**: YouTube's IFrame API needs to open windows for certain operations (like error handling), so `javaScriptCanOpenWindowsAutomatically` must be enabled.

## Useful Resources

- [YouTube IFrame Player API](https://developers.google.com/youtube/iframe_api_reference)
- [YouTube Developer Policies](https://developers.google.com/youtube/terms/developer-policies-guide)
- [Error 150/153 Fix Guide](https://corsproxy.io/blog/fix-youtube-error-150-153-webview/)
- [Android WebView Best Practices](https://developer.android.com/guide/webapps/webview)

## Summary of Changes

| Component | Before | After |
|-----------|--------|-------|
| Origin | `https://www.youtube.com` | `https://localhost` |
| Referrer | Not set | `https://www.youtube.com` (injected) |
| Cache Mode | `LOAD_DEFAULT` | `LOAD_NO_CACHE` |
| Error Messages | Generic | Detailed with solutions |
| Referrer Policy | None | `no-referrer-when-downgrade` |
| HTTP Error Logging | No | Yes |

## Next Steps

1. ✅ All code changes applied
2. Test with known embeddable videos
3. Monitor Logcat for remaining errors
4. If issues persist, verify network environment isn't blocking YouTube embedding
5. Consider implementing fallback player for videos with embedding disabled
