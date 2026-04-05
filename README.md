# ViewSync - Android Multiple YouTube Video Viewer

A modern Android app that allows users to watch multiple YouTube videos simultaneously with synchronized playback, inspired by ViewSync.net.

## Features

✅ **Multiple Video Playback** - Watch multiple YouTube videos side by side  
✅ **Synchronized Playback** - Play, pause, and seek all videos together  
✅ **Audio Cue Syncing** - Record and manage sync points between videos  
✅ **Share Links** - Generate shareable links with pre-synced videos  
✅ **YouTube Search Integration** - Search and select videos directly in the app  
✅ **Material Design 3** - Modern UI with dark/light theme support  
✅ **Jetpack Compose** - Fully declarative UI with state management  
✅ **Hilt Dependency Injection** - Clean architecture with DI  
✅ **Retrofit API** - YouTube API v3 integration  

## Architecture

```
ViewSyncApp/
├── data/
│   ├── model/           # Data classes (YouTubeVideo, SyncSession, etc.)
│   ├── remote/          # YouTube API service & parsers
│   └── repository/      # Business logic & sync calculations
├── ui/
│   ├── screen/          # Compose UI screens
│   ├── viewmodel/       # ViewModels for state management
│   └── theme/           # Material Design 3 theme
├── di/                  # Hilt dependency injection
└── MainActivity.kt      # Entry point & navigation
```

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM + Repository Pattern
- **Dependency Injection**: Hilt
- **API Client**: Retrofit + OkHttp
- **Video Player**: AndroidYouTubePlayer (Pierfrancesco Soffritti)
- **Serialization**: Kotlinx Serialization
- **Navigation**: Jetpack Navigation Compose
- **Theme**: Material Design 3

## Setup Instructions

### 1. Get YouTube API Key

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project
3. Enable YouTube Data API v3
4. Create an API key (Credentials → Create Credentials → API Key)
5. Copy your API key

### 2. Update build.gradle.kts

```kotlin
buildConfigField("String", "YOUTUBE_API_KEY", "\"YOUR_API_KEY_HERE\"")
```

### 3. Install Dependencies

All dependencies are configured in `build.gradle.kts`. Android Studio will automatically download them.

### 4. Build & Run

```bash
./gradlew build
./gradlew installDebug  # Or run through Android Studio
```

## Usage

### Searching & Creating Sessions

1. Launch the app → Search for YouTube videos
2. Select multiple videos you want to sync
3. Name your sync session
4. Videos load in the player

### Syncing Videos

1. Play videos to find matching audio cues (words, sounds, etc.)
2. Stop each video at the same moment
3. Click the **Bookmark** button for each video to record a sync cue
4. Add optional descriptions to cues
5. The system automatically calculates offsets

### Playback Controls

- **Play/Pause** - Synchronized across all videos
- **Seek** - Adjust playback position (respects video offsets)
- **Skip** - ±5 second jumps
- **Share** - Generate a shareable link with all sync data encoded

## Key Components

### SyncRepository
Handles:
- YouTube video search
- Sync session CRUD operations
- Cue management
- Offset calculation
- Share link generation

### SyncPlayerViewModel
Manages:
- Playback state (position, playing)
- Video offsets
- Sync cue recording
- Share link generation

### SyncPlayerScreen
Displays:
- Multiple YouTube players
- Synchronized playback controls
- Sync cue list
- Share section

### VideoSearchScreen
Provides:
- YouTube video search
- Video selection/multi-select
- Search result thumbnails
- Session creation dialog

## Share Link Format

```
https://viewsync.youkhainda.com/?videos=vid1,vid2,vid3&cues=0:15000|1:20000&name=Session%20Name
```

Parameters:
- `videos` - Comma-separated video IDs
- `cues` - Pipe-separated cues (format: `videoIndex:cueTimeMs`)
- `name` - Session name (URL encoded)

## Video Offset Calculation

When syncing videos with multiple cues:

```
offsets[videoIndex] = (videoIndex cue time) - (base video cue time)
```

This ensures all videos align at sync points while maintaining the offset throughout playback.

## Future Enhancements

🔄 Room Database persistence  
📱 Persistent sync sessions  
🎨 Custom theme/accent colors  
📊 Analytics & usage tracking  
💬 Comments on sync cues  
🔐 User accounts & cloud sync  
📹 Support for other video platforms  
🎯 Recommended sync videos  
🎵 Audio waveform visualization  
⏱️ Detailed sync statistics  

## Troubleshooting

### Videos Not Loading
- Check YouTube API key in BuildConfig
- Verify API key has YouTube Data API v3 enabled
- Check internet connection

### Sync Cues Not Recording
- Ensure you're in a valid sync session
- Check that video index matches the video being played
- Verify the app has permission to store data

### Player Crashes
- Update YouTube API key permissions
- Check device API level (min 24)
- Clear app cache

## API Reference

### YouTubeApiService

```kotlin
suspend fun searchVideos(
    query: String,
    maxResults: Int = 25,
    apiKey: String,
): YouTubeSearchResponse

suspend fun getVideoDetails(
    videoIds: String,
    apiKey: String,
): YouTubeVideoDetailsResponse
```

### SyncRepository

```kotlin
suspend fun searchYouTubeVideos(query: String): List<YouTubeVideo>
suspend fun createSyncSession(name: String, videos: List<YouTubeVideo>): SyncSession
suspend fun addSyncCue(sessionId: String, cue: SyncCue): Boolean
suspend fun calculateVideoOffsets(sessionId: String, baseVideoIndex: Int = 0): Map<Int, Long>
suspend fun generateShareLink(sessionId: String): String
```

## Testing

Run tests with:
```bash
./gradlew test
./gradlew connectedAndroidTest
```

## License

MIT License - Feel free to use and modify!

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Open a Pull Request

## Support

For issues, feature requests, or questions, please open an issue on the repository.

---

**Built with ❤️ using Kotlin & Jetpack Compose**
