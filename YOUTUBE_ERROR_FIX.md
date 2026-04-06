# YouTube Error Code -1 Fix Guide

## Problem
Error code: -1 typically indicates a failure in communication between the YouTube player and YouTube's servers. This is commonly caused by:
- Authentication issues
- Embedding restrictions
- Incorrect WebView configuration
- API key misconfiguration

## Fixes Applied

### 1. ✅ Updated IFramePlayerOptions Configuration
**File**: `app/src/main/java/com/youkhainda/viewsync/ui/screen/SyncPlayerScreen.kt`

**Changes**:
- Changed `origin` from `https://www.youtube.com` to `https://localhost`
- Added explicit `referrer` parameter set to `https://www.youtube.com`

```kotlin
val options = IFramePlayerOptions.Builder(ctx)
    .controls(1)
    .origin("https://localhost")
    .autoplay(0)
    .referrer("https://www.youtube.com")
    .build()
```

### 2. ✅ Enhanced WebView Configuration
**Improvements**:
- Enabled database storage
- Added DOM storage (duplicate check removed)
- Added referrer injection via JavaScript after page load

```kotlin
override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)
    view?.loadUrl("javascript:(function() { " +
        "document.referrer = 'https://www.youtube.com'; " +
        "})()")
}
```

## Required Actions

### Step 1: Configure Your YouTube API Key

**You're using GitHub Secrets** ✅ - Your CI/CD is already configured to use `secrets.YOUTUBE_API_KEY`.

**For Local Development**, you have two options:

#### Option A: Create local.properties (Recommended for Development)

1. **Get a YouTube Data API v3 Key**:
   - Go to [Google Cloud Console](https://console.cloud.google.com/apis/credentials)
   - Create a new project or select existing one
   - Enable "YouTube Data API v3"
   - Create credentials → API Key

2. **Restrict Your API Key** (Recommended):
   - Click on your API key
   - Under "Application restrictions", select "Android apps"
   - Add your package name: `com.youkhainda.viewsync`
   - Add your SHA-1 fingerprint (see below)
   - Under "API restrictions", select "Restrict key" and select "YouTube Data API v3"

3. **Get Your SHA-1 Fingerprint**:
   ```bash
   # For debug keystore (default password: android)
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   
   # For release keystore (use your own keystore path and password)
   keytool -list -v -keystore /path/to/your/release.keystore -alias your_alias
   ```

4. **Add API Key to local.properties**:
   ```bash
   # Copy the template
   cp local.properties.template local.properties
   
   # Edit and add your actual API key
   youtube.api.key=YOUR_ACTUAL_API_KEY_HERE
   ```

#### Option B: Build with Environment Variable

If you prefer not to store the key locally:

```bash
# Build with environment variable (won't create local.properties file)
YOUTUBE_API_KEY="your_actual_key" ./gradlew assembleDebug
```

Or use the helper script:
```bash
./scripts/build-with-secret.sh
```

### Step 2: Verify Video IDs

Some videos have embedding restrictions. Test with known embeddable videos:

**Test Video IDs** (known to allow embedding):
- `dQw4w9WgXcQ` (Rick Astley - Never Gonna Give You Up)
- `jNQXAC9IVRw` (Me at the zoo - First YouTube video)
- `9bZkp7q19f0` (PSY - GANGNAM STYLE)

Update your sync session to use these test videos to verify the fix works.

### Step 3: Check Logcat for Detailed Errors

In Android Studio:
1. Open Logcat (View → Tool Windows → Logcat)
2. Filter by your app: `package:com.youkhainda.viewsync`
3. Filter by errors: `level:error`
4. Look for YouTube-related errors: `YouTube|Player|WebView`

**Common Error Codes**:
- `-1`: General playback error (authentication/network issue)
- `2`: Invalid parameter
- `5`: Content cannot be played in embedded player
- `100`: Video not found
- `101/150`: Video owner disabled embedding
- `152`: Domain/copyright restriction

### Step 4: Test in Different Scenarios

1. **Test all videos**: Does the error occur for all videos or just specific ones?
2. **Test in browser**: Open the same video in Chrome/Firefox on the same device
3. **Test network**: Ensure stable internet connection
4. **Test on different devices**: Emulator vs. physical device

### Step 5: Verify Google Cloud Console Settings

1. **API Enabled**: Confirm "YouTube Data API v3" is enabled
2. **Billing Enabled**: Some APIs require billing (though YouTube Data API v3 is free)
3. **Quota Not Exceeded**: Check your API quota usage in Google Cloud Console
4. **Package Name Match**: Ensure the package name in restrictions matches exactly: `com.youkhainda.viewsync`

## Additional Troubleshooting

### If Error Persists

1. **Clear App Data**:
   ```
   Settings → Apps → ViewSync → Storage → Clear Data
   ```

2. **Rebuild Project**:
   ```bash
   ./gradlew clean
   ./gradlew assembleDebug
   ```

3. **Check Internet Permission**: Already present in `AndroidManifest.xml` ✅

4. **Test with Minimal Implementation**:
   Create a simple test activity with just one YouTubePlayerView to isolate the issue

### Alternative: Use ExoPlayer as Fallback

The project already includes ExoPlayer dependencies. If YouTube embedding continues to fail:

```kotlin
// You can implement ExoPlayer as a fallback for videos that fail with YouTube player
// Note: This requires extracting video URLs which may violate YouTube ToS
// Only use for videos you have rights to embed
```

## Testing Checklist

- [ ] API key is valid and active
- [ ] API key has correct Android restrictions (package name + SHA-1)
- [ ] YouTube Data API v3 is enabled in Google Cloud Console
- [ ] Test video IDs are known to allow embedding
- [ ] Device has stable internet connection
- [ ] App has INTERNET permission (verified in AndroidManifest.xml) ✅
- [ ] WebView JavaScript is enabled ✅
- [ ] Origin and referrer are properly configured ✅
- [ ] Tested with multiple videos
- [ ] Checked Logcat for detailed error messages

## Useful Resources

- [YouTube IFrame Player API](https://developers.google.com/youtube/iframe_api_reference)
- [Android YouTube Player Library](https://github.com/PierfrancescoSoffritti/android-youtube-player)
- [YouTube Developer Policies](https://developers.google.com/youtube/terms/developer-policies-guide)
- [Google Cloud Console](https://console.cloud.google.com/)
- [Error 150/153 Fix Guide](https://corsproxy.io/blog/fix-youtube-error-150-153-webview/)

## Next Steps

1. Apply the fixes above (code changes already applied ✅)
2. Configure your API key in `local.properties`
3. Rebuild and test with known embeddable videos
4. Check Logcat for any remaining errors
5. If issues persist, verify your Google Cloud Console configuration

## Support

If you're still experiencing issues after following this guide:
1. Share the Logcat output filtered for YouTube errors
2. Confirm your API key configuration (without exposing the key itself)
3. Test with the specific video IDs mentioned above
4. Check if the issue occurs on emulator, physical device, or both
