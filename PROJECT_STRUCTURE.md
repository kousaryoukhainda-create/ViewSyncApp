# ViewSync Android App - Project Structure

## Complete File Tree

```
ViewSyncApp/
├── build.gradle.kts                    # App-level build configuration
├── README.md                           # Quick start guide
├── IMPLEMENTATION_GUIDE.md             # Detailed implementation docs
│
├── src/main/
│   ├── AndroidManifest.xml             # App manifest with permissions
│   │
│   └── java/com/youkhainda/viewsync/
│       ├── MainActivity.kt             # Entry point & Navigation
│       │
│       ├── data/
│       │   ├── model/
│       │   │   └── Models.kt           # YouTubeVideo, SyncSession, SyncCue, API responses
│       │   │
│       │   ├── remote/
│       │   │   └── YouTubeApiService.kt # Retrofit API interface & parsers
│       │   │
│       │   └── repository/
│       │       └── SyncRepository.kt   # Business logic & data coordination
│       │
│       ├── ui/
│       │   ├── screen/
│       │   │   ├── SyncPlayerScreen.kt # Main player with multi-video sync
│       │   │   └── VideoSearchScreen.kt # Search and session creation
│       │   │
│       │   ├── viewmodel/
│       │   │   └── SyncViewModels.kt   # SyncPlayerViewModel & SearchViewModel
│       │   │
│       │   └── theme/
│       │       ├── Theme.kt            # Material Design 3 color scheme
│       │       └── Type.kt             # Typography definitions
│       │
│       └── di/
│           └── AppModule.kt            # Hilt DI bindings & singletons
```

## File Descriptions

### Core Files (Root Level)

| File | Purpose |
|------|---------|
| `build.gradle.kts` | Dependencies, SDK versions, build config |
| `README.md` | Setup instructions and feature overview |
| `IMPLEMENTATION_GUIDE.md` | Advanced development guide |

### Data Layer (data/)

#### Models (data/model/Models.kt)
```kotlin
YouTubeVideo          // YouTube video with metadata
SyncSession           // Collection of videos + sync cues
SyncCue              // Single sync point with time + description
SyncState            // Current playback state
// API Response models for YouTube v3 API
YouTubeSearchResponse
YouTubeVideoDetails
YouTubeVideoDetailsResponse
```

#### Remote (data/remote/YouTubeApiService.kt)
```kotlin
YouTubeApiService    // Retrofit interface
  - searchVideos()   // Search YouTube
  - getVideoDetails()// Get duration and metadata
parseDuration()      // ISO 8601 → milliseconds
```

#### Repository (data/repository/SyncRepository.kt)
```kotlin
SyncRepository
  - searchYouTubeVideos()    // API + parsing
  - createSyncSession()      // New session
  - addSyncCue()             // Record sync point
  - calculateVideoOffsets()  // Compute playback offsets
  - generateShareLink()      // Create shareable URL
```

### UI Layer (ui/)

#### Screens (ui/screen/)

**SyncPlayerScreen.kt**
- `SyncPlayerScreen()` - Main player screen (entry point)
- `SyncPlayerContent()` - Video grid + controls
- `VideoPlayerCard()` - Single video player with bookmark button
- `PlaybackControlsSection()` - Play/pause/seek controls
- `SyncCuesSection()` - List of recorded sync points
- `ShareSection()` - Generate and display share link

**VideoSearchScreen.kt**
- `VideoSearchScreen()` - Search & selection UI
- `SearchBar()` - Query input field
- `VideoSearchResultItem()` - Selectable video result card
- `SelectedVideosSummary()` - Display chosen videos
- `CreateSessionDialog()` - Session name input

#### ViewModels (ui/viewmodel/SyncViewModels.kt)

```kotlin
@HiltViewModel
SyncPlayerViewModel
  Properties:
  - uiState: StateFlow<SyncPlayerUiState>
  - syncState: StateFlow<SyncState>
  - videoOffsets: StateFlow<Map<Int, Long>>
  
  Methods:
  - loadSyncSession(sessionId)
  - play() / pause()
  - seekToPosition(ms)
  - recordSyncCue(videoIndex, cueTime, description)
  - generateShareLink()

@HiltViewModel
SearchViewModel
  Properties:
  - searchResults: StateFlow<List<YouTubeVideo>>
  - isLoading: StateFlow<Boolean>
  - error: StateFlow<String?>
  
  Methods:
  - searchVideos(query)
  - createSyncSession(name, videos)
```

#### Theme (ui/theme/)

**Theme.kt**
- Color schemes (light + dark)
- Material Design 3 colors
- Status bar styling
- Theme composition

**Type.kt**
- Material Design 3 typography
- 15 text styles (display, headline, body, label)
- Font sizes and weights

### Dependency Injection (di/AppModule.kt)

Provides singletons for:
- `Json` - Kotlinx Serialization
- `OkHttpClient` - HTTP client with logging
- `YouTubeApiService` - Retrofit service
- `DataStore<Preferences>` - Local data storage

### Navigation (MainActivity.kt)

Routes:
- `SEARCH` → VideoSearchScreen
- `PLAYER/{sessionId}` → SyncPlayerScreen

Flow: Search → Select Videos → Create Session → Navigate to Player

## Key Dependencies

```gradle
// Compose (UI)
androidx.compose:compose-bom:2024.02.00
androidx.compose.ui:ui
androidx.compose.material3:material3
androidx.activity:activity-compose

// Navigation
androidx.navigation:navigation-compose

// Hilt (DI)
com.google.dagger:hilt-android
androidx.hilt:hilt-navigation-compose

// Retrofit (API)
com.squareup.retrofit2:retrofit
com.squareup.okhttp3:okhttp
com.squareup.okhttp3:logging-interceptor

// Serialization
org.jetbrains.kotlinx:kotlinx-serialization-json

// YouTube Player
com.pierfrancescosoffritti.androidyoutubeplayer:core

// DataStore
androidx.datastore:datastore-preferences

// Coroutines
org.jetbrains.kotlinx:kotlinx-coroutines-android
```

## Data Flow Diagram

```
VideoSearchScreen
    ↓
SearchViewModel
    ↓
[User searches] → YouTubeApiService
                  (HTTP GET to YouTube API)
                  ↓
              Retrofit response parsing
                  ↓
              SyncRepository.searchYouTubeVideos()
                  ↓
              StateFlow updates
                  ↓
              Compose recomposes with results
                  ↓
              [User selects videos and creates session]
                  ↓
              SyncRepository.createSyncSession()
                  ↓
              Navigate to SyncPlayerScreen
                  ↓
SyncPlayerScreen
    ↓
SyncPlayerViewModel
    ↓
[Load session] → SyncRepository.getSyncSession()
                  ↓
              SyncRepository.calculateVideoOffsets()
                  ↓
              StateFlow updates (uiState, syncState, videoOffsets)
                  ↓
              YouTubePlayers load & initialize
                  ↓
[User records sync cues] → SyncRepository.addSyncCue()
                           ↓
                       Recalculate offsets
                           ↓
                       Update StateFlow
                           ↓
                       Compose recomposes with cue list
                           ↓
[Generate share link] → SyncRepository.generateShareLink()
                        ↓
                    Encode URL with cues
                        ↓
                    Copy to clipboard (future)
```

## State Management Flow

### UiState (Sealed Class)
```
Loading
    ↓
Success (session + optional shareLink)
    ↓
Error (error message)
```

### SyncState (Data Class)
```
currentPlayPosition: Long (ms)
isPlaying: Boolean
videoOffsets: Map<Int, Long> (video index → offset in ms)
```

### Update Triggers
- User initiates play → `play()` → syncState updated
- User seeks video → `seekToPosition()` → syncState updated
- User records cue → `recordSyncCue()` → Session + offsets updated

## Compose Recomposition Strategy

```kotlin
// Level 1: Root Composable reads StateFlow
val uiState by viewModel.uiState.collectAsState()

// Level 2: Conditional recomposition based on state
when (uiState) {
    Loading → show spinner
    Success → show SyncPlayerContent
    Error → show error
}

// Level 3: Child composables read subscribed flows
val syncState by viewModel.syncState.collectAsState()
val videoOffsets by viewModel.videoOffsets.collectAsState()

// Level 4: onClick/onChange handlers → ViewModel → StateFlow updates → recomposition
```

## Testing Files (To Add)

```
test/
├── java/com/youkhainda/viewsync/
│   ├── data/
│   │   └── repository/
│   │       └── SyncRepositoryTest.kt
│   └── ui/
│       └── viewmodel/
│           └── SyncPlayerViewModelTest.kt

androidTest/
├── java/com/youkhainda/viewsync/
│   └── ui/
│       └── screen/
│           └── SyncPlayerScreenTest.kt
```

## Configuration Files (To Add)

```
src/main/
├── res/
│   ├── values/
│   │   ├── strings.xml
│   │   ├── colors.xml
│   │   ├── dimens.xml
│   │   └── styles.xml
│   ├── drawable/
│   │   └── ic_launcher_foreground.xml
│   └── mipmap/
│       ├── ic_launcher.png
│       └── ic_launcher_round.png

src/debug/
├── AndroidManifest.xml (for debug-only config)
```

## Build Variants

```gradle
buildTypes {
    debug {
        debuggable = true
        versionNameSuffix = "-debug"
    }
    release {
        minifyEnabled = true
        proguardFiles(...)
    }
}
```

## Performance Profiles

### APK Size
- Compose UI: ~2MB
- Retrofit + OkHttp: ~1MB
- YouTube Player: ~3MB
- **Target**: <10MB base APK

### Memory Usage
- Active video players: ~50MB per player
- Cached images: ~10MB
- StateFlow/ViewModel: <5MB
- **Target**: <200MB at rest

### Network
- Search API call: ~50KB
- Video details API: ~10KB per video
- YouTube video stream: Depends on resolution

## Version History

| Version | Changes |
|---------|---------|
| 1.0.0 | Initial release with core features |
| 1.1.0 (planned) | Room persistence, offline support |
| 1.2.0 (planned) | User accounts, cloud sync |
| 2.0.0 (planned) | Audio fingerprinting, advanced sync |

---

**Next Steps**: 
1. Clone or download this project
2. Add YouTube API key to `build.gradle.kts`
3. Sync Gradle and build
4. Run on emulator or device
5. Refer to IMPLEMENTATION_GUIDE.md for extensions
