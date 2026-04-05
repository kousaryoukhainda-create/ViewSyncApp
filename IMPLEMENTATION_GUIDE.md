# ViewSync Android App - Implementation Guide

## Architecture Overview

This app follows clean architecture principles with clear separation of concerns:

### Data Layer (data/)
- **model/** - Serializable data classes for YouTube API responses and app domain models
- **remote/** - Retrofit API service definitions and response parsing
- **repository/** - Business logic that coordinates between API calls and UI state

### UI Layer (ui/)
- **screen/** - Composable screens (search and player)
- **viewmodel/** - ViewModels using Hilt that manage UI state via StateFlow
- **theme/** - Material Design 3 theme definitions and typography

### DI Layer (di/)
- **AppModule** - Provides singleton instances of Retrofit, OkHttp, JSON serializer, and DataStore

## Key Implementation Details

### 1. YouTube Integration

**API Service** (YouTubeApiService.kt):
- Search videos with query string
- Fetch video details including duration
- Uses YouTube Data API v3 endpoints

**Duration Parsing**:
```kotlin
// ISO 8601 format: PT10M30S → 630000ms
fun parseDuration(isoDuration: String): Long
```

**API Key Security**:
- Stored in BuildConfig (generated at compile time)
- Not hardcoded in source
- Should be moved to secure backend in production

### 2. Sync Mechanism

**Sync Cues**:
- Record points in time where videos should align
- Index videos by position in the session
- Store with millisecond precision

**Offset Calculation**:
```
If Video 0 syncs at 15s and Video 1 syncs at 20s:
  offset[0] = 15000 - 15000 = 0ms
  offset[1] = 20000 - 15000 = 5000ms
  
When playing Video 0 at 30s:
  Video 0 plays at 30s
  Video 1 plays at 30s + 5000ms = 35s
```

### 3. Video Player Integration

**AndroidYouTubePlayer Library**:
- Provides WebView-based YouTube embedding
- Handles playback state and seeking
- No native library compile complexity

**Multi-Video Sync**:
- Each video player is independent
- PlaybackControlsSection applies offsets when seeking
- All videos respect the same play/pause state

### 4. State Management

**SyncPlayerViewModel**:
```
UiState (Sealed):
├── Loading
├── Success(session, shareLink)
└── Error(message)

SyncState (Data class):
├── currentPlayPosition
├── isPlaying
└── videoOffsets: Map<Int, Long>
```

**Flow Pattern**:
```
Compose reads StateFlow → onChange emits new state → Compose recomposes
```

### 5. Share Link System

**Format**:
```
https://viewsync.youkhainda.com/?videos=vid1,vid2&cues=0:15000|1:20000&name=UHC
```

**Components**:
- Video IDs (comma-separated)
- Sync cues (pipe-separated, format: `videoIndex:timeMs`)
- Session name (URL encoded)

**Decoding** (future implementation):
```kotlin
fun parseSyncUrl(url: String): SyncSession {
    val videos = url.getQueryParam("videos").split(",")
    val cues = url.getQueryParam("cues").split("|").map { cuePair ->
        val (idx, time) = cuePair.split(":")
        SyncCue(idx.toInt(), time.toLong())
    }
    val name = url.getQueryParam("name").urlDecode()
    return SyncSession(name = name, videoIds = videos, syncCues = cues)
}
```

## Common Tasks

### Adding a New Feature

1. **Create data models** in `data/model/`
2. **Add API endpoints** if needed in `data/remote/`
3. **Update repository** with business logic
4. **Create ViewModel** if new screen or complex state
5. **Build Compose UI** in `ui/screen/`
6. **Add navigation** route if new screen

### Example: Adding Playlists

```kotlin
// 1. Models
@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val sessionIds: List<String>,
    val createdAt: Long = System.currentTimeMillis(),
)

// 2. Repository
@ViewModelScoped
class PlaylistRepository @Inject constructor(...) {
    suspend fun createPlaylist(name: String): Playlist { ... }
    suspend fun addSessionToPlaylist(playlistId: String, sessionId: String) { ... }
}

// 3. ViewModel
@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val playlistRepo: PlaylistRepository,
) : ViewModel() {
    val playlists: StateFlow<List<Playlist>> = ...
    fun createPlaylist(name: String) { ... }
}

// 4. Screen
@Composable
fun PlaylistScreen(viewModel: PlaylistViewModel = hiltViewModel()) {
    val playlists by viewModel.playlists.collectAsState()
    // UI implementation
}

// 5. Navigation
NavigationRoute.PLAYLISTS : NavigationRoute("playlists")
```

### Persisting Data with Room

```kotlin
// 1. Add Room dependency
implementation("androidx.room:room-runtime:2.5.0")
kapt("androidx.room:room-compiler:2.5.0")

// 2. Create entities
@Entity(tableName = "sync_sessions")
data class SyncSessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val videoIds: String, // JSON encoded
    val syncCues: String, // JSON encoded
    val createdAt: Long,
)

// 3. Create DAO
@Dao
interface SyncSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SyncSessionEntity)
    
    @Query("SELECT * FROM sync_sessions WHERE id = :id")
    suspend fun getById(id: String): SyncSessionEntity?
    
    @Query("SELECT * FROM sync_sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<SyncSessionEntity>>
}

// 4. Update repository to use DAO
@ViewModelScoped
class SyncRepository @Inject constructor(
    private val youtubeApi: YouTubeApiService,
    private val dao: SyncSessionDao,
) {
    suspend fun createSyncSession(...): SyncSession {
        val session = ...
        dao.insert(session.toEntity())
        return session
    }
}
```

### Adding Firebase Cloud Messaging

```kotlin
// 1. Add Firebase dependency
implementation(platform("com.google.firebase:firebase-bom:32.3.1"))
implementation("com.google.firebase:firebase-messaging-ktx")

// 2. Create messaging service
class ViewSyncMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Handle new sync session shared via FCM
        val sessionData = remoteMessage.data
        navigateToSession(sessionData["sessionId"])
    }
    
    override fun onNewToken(token: String) {
        // Send token to backend for push notifications
    }
}

// 3. AndroidManifest.xml
<service
    android:name=".messaging.ViewSyncMessagingService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
```

### Improving Sync Accuracy

**Current**: Simple offset-based syncing  
**Next Level**: Waveform analysis with audio fingerprinting

```kotlin
// Pseudo-code for audio fingerprinting
fun generateFingerprint(videoId: String): AudioFingerprint {
    // Extract audio from video
    val audioStream = youtubeApi.getAudioStream(videoId)
    
    // Analyze frequency spectrum
    val fingerprint = SpectrogramAnalyzer.analyze(audioStream)
    
    // Store for fuzzy matching
    return fingerprint
}

fun findSyncPoints(video1: String, video2: String): List<Long> {
    val fp1 = generateFingerprint(video1)
    val fp2 = generateFingerprint(video2)
    
    // Match fingerprints with fuzzy logic
    return FingerprintMatcher.findMatches(fp1, fp2)
}
```

## Testing Strategy

### Unit Tests
```kotlin
class SyncRepositoryTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    
    private val mockYouTubeApi = mockk<YouTubeApiService>()
    private val repository = SyncRepository(mockYouTubeApi)
    
    @Test
    fun testVideoOffsetCalculation() = runTest {
        val offsets = repository.calculateVideoOffsets(sessionId)
        assertEquals(0L, offsets[0])
        assertEquals(5000L, offsets[1])
    }
}
```

### Integration Tests
```kotlin
@RunWith(AndroidJUnit4::class)
class SyncPlayerScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun testPlayButtonSyncsAllVideos() {
        composeTestRule.setContent {
            SyncPlayerScreen(sessionId = "test-session")
        }
        
        composeTestRule.onNodeWithContentDescription("Play").performClick()
        // Assert all videos are playing
    }
}
```

## Performance Optimization

### Memory Management
- Use `LazyColumn` for large lists (not `Column`)
- Recycle YouTube player instances
- Clear unused Bitmaps from image cache

### Network Optimization
- Cache API responses with OkHttp interceptor
- Batch YouTube API calls
- Implement exponential backoff for retries

### UI Performance
- Use `remember` to avoid recompositions
- Move heavy operations to ViewModels
- Implement pagination for search results

## Security Considerations

1. **API Key Management**
   - Use backend proxy for API calls in production
   - Never expose API key in logs or error messages
   - Rotate keys regularly

2. **User Data**
   - Encrypt stored sync sessions
   - Use HTTPS for all network calls
   - Validate all user inputs

3. **Share Links**
   - Add expiration timestamps
   - Implement signing/verification
   - Rate limit link generation

## Debugging Tips

### Logcat Filtering
```bash
# YouTube API calls
adb logcat | grep YouTubeApiService

# Sync calculations
adb logcat | grep SyncRepository

# Player state
adb logcat | grep "YouTubePlayer"
```

### Compose Preview Testing
```kotlin
@Preview(showBackground = true)
@Composable
fun PreviewSyncPlayerScreen() {
    ViewSyncTheme {
        SyncPlayerScreen(sessionId = "preview-session")
    }
}
```

### Network Interceptor Logging
Already configured in `AppModule`:
```kotlin
HttpLoggingInterceptor().apply {
    level = if (BuildConfig.DEBUG) Level.BODY else Level.NONE
}
```

## Release Checklist

- [ ] Replace YouTube API key with production key
- [ ] Update version code/name in build.gradle.kts
- [ ] Run full test suite
- [ ] Test on min API level (24)
- [ ] Test on latest API level
- [ ] Enable ProGuard/R8 obfuscation
- [ ] Sign release APK
- [ ] Create GitHub release notes
- [ ] Upload to Play Store

## Resources

- [YouTube Data API Docs](https://developers.google.com/youtube/v3)
- [Jetpack Compose Docs](https://developer.android.com/jetpack/compose)
- [Hilt Documentation](https://dagger.dev/hilt/)
- [Retrofit Guide](https://square.github.io/retrofit/)
- [Material Design 3](https://m3.material.io/)

---

**Questions? Check the README.md or open an issue!**
