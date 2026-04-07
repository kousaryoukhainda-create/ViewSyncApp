package com.youkhainda.viewsync.ui.screen

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.youkhainda.viewsync.data.model.SyncSession
import com.youkhainda.viewsync.ui.viewmodel.SyncPlayerUiState
import com.youkhainda.viewsync.ui.viewmodel.SyncPlayerViewModel
import com.youkhainda.viewsync.util.DebugLogger

/**
 * Interface to control YouTube players across all video cards
 */
interface PlayerController {
    fun playAll()
    fun pauseAll()
    fun seekAll(positionMs: Long)
    fun registerPlayer(index: Int, player: YouTubePlayer)
    fun unregisterPlayer(index: Int)
}

@Composable
fun SyncPlayerScreen(
    sessionId: String,
    viewModel: SyncPlayerViewModel = hiltViewModel(),
    onAddVideo: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val videoOffsets by viewModel.videoOffsets.collectAsState()
    var showDebugOverlay by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) {
        DebugLogger.i("SyncPlayerScreen", "Loading session: $sessionId")
        viewModel.loadSyncSession(sessionId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (val state = uiState) {
            is SyncPlayerUiState.Loading -> {
                DebugLogger.d("SyncPlayerScreen", "UI State: Loading")
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            is SyncPlayerUiState.Success -> {
                DebugLogger.i("SyncPlayerScreen", "UI State: Success - Session: ${state.session.name}, Videos: ${state.session.videoIds.size}, Sync Cues: ${state.session.syncCues.size}")
                SyncPlayerContent(
                    session = state.session,
                    shareLink = state.shareLink,
                    syncState = syncState,
                    videoOffsets = videoOffsets,
                    onPlay = { viewModel.play() },
                    onPause = { viewModel.pause() },
                    onSeek = { viewModel.seekToPosition(it) },
                    onRecordCue = { videoIdx, time, desc -> viewModel.recordSyncCue(videoIdx, time, desc) },
                    onGenerateLink = { viewModel.generateShareLink() },
                    onAddVideo = onAddVideo,
                )
            }

            is SyncPlayerUiState.Error -> {
                DebugLogger.e("SyncPlayerScreen", "UI State: Error - ${state.message}")
                ErrorScreen(message = state.message)
            }
        }
        
        // Debug toggle button in bottom-right corner
        DebugToggleButton(
            isVisible = showDebugOverlay,
            onToggle = { showDebugOverlay = !showDebugOverlay },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }
    
    // Debug overlay dialog
    DebugOverlay(
        isVisible = showDebugOverlay,
        onDismiss = { showDebugOverlay = false },
    )
}

@Composable
private fun SyncPlayerContent(
    session: SyncSession,
    shareLink: String,
    syncState: com.youkhainda.viewsync.data.model.SyncState,
    videoOffsets: Map<Int, Long>,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onRecordCue: (Int, Long, String) -> Unit,
    onGenerateLink: () -> Unit,
    onAddVideo: () -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Header
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(8.dp),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = session.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${session.videoIds.size} videos | ${session.syncCues.size} sync cues",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    )
                }

                IconButton(
                    onClick = onAddVideo,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add video",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }

        // Video Grid
        val playerController = rememberPlayerController()
        
        LazyVideoGrid(
            videoIds = session.videoIds,
            syncState = syncState,
            videoOffsets = videoOffsets,
            playerController = playerController,
            onRecordCue = onRecordCue,
        )

        // Playback Controls
        PlaybackControlsSection(
            isPlaying = syncState.isPlaying,
            currentPosition = syncState.currentPlayPosition,
            onPlay = { 
                playerController.playAll()
                onPlay()
            },
            onPause = { 
                playerController.pauseAll()
                onPause()
            },
            onSeek = { position ->
                playerController.seekAll(position)
                onSeek(position)
            },
        )

        // Sync Cues List
        if (session.syncCues.isNotEmpty()) {
            SyncCuesSection(session.syncCues)
        }

        // Share Section
        ShareSection(
            shareLink = shareLink,
            onGenerateLink = onGenerateLink,
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun LazyVideoGrid(
    videoIds: List<String>,
    syncState: com.youkhainda.viewsync.data.model.SyncState,
    videoOffsets: Map<Int, Long>,
    playerController: PlayerController,
    onRecordCue: (Int, Long, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        videoIds.forEachIndexed { index, videoId ->
            VideoPlayerCard(
                videoId = videoId,
                videoIndex = index,
                offset = videoOffsets[index] ?: 0L,
                playerController = playerController,
                onRecordCue = { time, desc -> onRecordCue(index, time, desc) },
            )
        }
    }
}

@Composable
private fun VideoPlayerCard(
    videoId: String,
    videoIndex: Int,
    offset: Long,
    playerController: PlayerController,
    onRecordCue: (Long, String) -> Unit,
) {
    var currentTime by remember { mutableLongStateOf(0L) }
    var youtubePlayer by remember { mutableStateOf<YouTubePlayer?>(null) }
    var showCueDialog by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<YouTubeError?>(null) }
    var isPlayerReady by remember { mutableStateOf(false) }

    // Validate video ID before attempting to play
    val isValidVideoId = com.youkhainda.viewsync.data.remote.YouTubeUrlParser.isValidVideoId(videoId)
    val context = LocalContext.current

    DebugLogger.d("VideoPlayerCard", "Video $videoIndex: ID=$videoId, Offset=$offset ms, Valid=$isValidVideoId")

    // Register/unregister player with controller
    DisposableEffect(videoIndex) {
        if (isValidVideoId && isPlayerReady) {
            youtubePlayer?.let { player ->
                playerController.registerPlayer(videoIndex, player)
                DebugLogger.d("VideoPlayerCard", "Video $videoIndex: Registered with controller")
            }
        }
        onDispose {
            playerController.unregisterPlayer(videoIndex)
            DebugLogger.d("VideoPlayerCard", "Video $videoIndex: Unregistered from controller")
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            // YouTube Player
            if (isValidVideoId) {
                if (playerError != null) {
                    // Error state - show error message with retry option
                    DebugLogger.w("VideoPlayerCard", "Video $videoIndex: Showing error view - ${playerError.userMessage}")
                    VideoPlayerErrorView(
                        error = playerError!!,
                        videoId = videoId,
                        onRetry = {
                            DebugLogger.i("VideoPlayerCard", "Video $videoIndex: User clicked retry")
                            playerError = null
                            isPlayerReady = false
                            youtubePlayer = null
                        },
                        onWatchOnYouTube = {
                            DebugLogger.i("VideoPlayerCard", "Video $videoIndex: User clicked Watch on YouTube")
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId"))
                            context.startActivity(intent)
                        },
                    )
                } else {
                    DebugLogger.d("VideoPlayerCard", "Video $videoIndex: Creating YouTubePlayerView")
                    YouTubePlayerViewContainer(
                        videoId = videoId,
                        videoIndex = videoIndex,
                        offset = offset,
                        onPlayerReady = { player ->
                            youtubePlayer = player
                            isPlayerReady = true
                            playerController.registerPlayer(videoIndex, player)
                            DebugLogger.i("VideoPlayerCard", "Video $videoIndex: Player ready, registered with controller")
                        },
                        onError = { error ->
                            val youTubeError = parseYouTubeError(error)
                            playerError = youTubeError
                            isPlayerReady = false
                            DebugLogger.e("VideoPlayerCard", "Video $videoIndex: Player error - Code: ${youTubeError.code}, Message: ${youTubeError.userMessage}")
                        },
                        onCurrentSecond = { second ->
                            currentTime = (second * 1000).toLong()
                        },
                    )
                }
            } else {
                // Invalid video ID - show error placeholder
                DebugLogger.e("VideoPlayerCard", "Video $videoIndex: Invalid video ID format: $videoId")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Invalid video ID",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            // Video Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Video ${videoIndex + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )

                IconButton(
                    onClick = { showCueDialog = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "Record sync cue",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }

    if (showCueDialog) {
        RecordCueDialog(
            onDismiss = { showCueDialog = false },
            onConfirm = { description ->
                onRecordCue(currentTime, description)
                showCueDialog = false
            },
        )
    }
}

/**
 * Represents a YouTube player error with user-friendly message
 */
data class YouTubeError(
    val code: Int,
    val rawError: PlayerConstants.PlayerError,
    val userMessage: String,
) {
    companion object {
        fun fromPlayerError(error: PlayerConstants.PlayerError): YouTubeError {
            val errorString = error.toString()
            val code = extractErrorCode(errorString)
            val message = getUserFriendlyMessage(code)
            return YouTubeError(code, error, message)
        }

        private fun extractErrorCode(errorString: String): Int {
            // Try to extract error code from various formats
            // "Error 100", "Error(100)", "100", etc.
            val regex = Regex("""(\d{2,3})""")
            val match = regex.find(errorString)
            return match?.groupValues?.get(1)?.toIntOrNull() ?: -1
        }

        private fun getUserFriendlyMessage(code: Int): String {
            return when (code) {
                -1 -> "Playback error. Try refreshing or watch on YouTube directly"
                2 -> "Invalid video parameter"
                5 -> "This video cannot't be played in embedded player"
                100 -> "Video not found. It may have been removed or is private"
                101, 150 -> "Video owner doesn't allow embedding"
                152 -> "Video restricted due to domain or copyright settings"
                else -> "This video is unavailable or can't be played"
            }
        }
    }
}

/**
 * Parse YouTube player error into user-friendly format
 */
fun parseYouTubeError(error: PlayerConstants.PlayerError): YouTubeError {
    return YouTubeError.fromPlayerError(error)
}

@Composable
private fun VideoPlayerErrorView(
    error: YouTubeError,
    videoId: String,
    onRetry: () -> Unit,
    onWatchOnYouTube: () -> Unit,
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(MaterialTheme.colorScheme.errorContainer),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = error.userMessage,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Error code: ${error.code}",
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Retry")
                }
                Button(
                    onClick = onWatchOnYouTube,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                    ),
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Watch on YouTube")
                }
            }
            OutlinedButton(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "https://www.youtube.com/watch?v=$videoId")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share video"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Generate Share Link")
            }
        }
    }
}

@Composable
private fun PlaybackControlsSection(
    isPlaying: Boolean,
    currentPosition: Long,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Playback Controls",
                style = MaterialTheme.typography.labelLarge,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onSeek(maxOf(0, currentPosition - 5000)) }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Rewind")
                }

                FloatingActionButton(
                    onClick = if (isPlaying) onPause else onPlay,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                    )
                }

                IconButton(onClick = { onSeek(currentPosition + 5000) }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Fast forward")
                }
            }

            Text(
                text = formatTime(currentPosition),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun SyncCuesSection(
    cues: List<com.youkhainda.viewsync.data.model.SyncCue>,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Sync Cues (${cues.size})",
                style = MaterialTheme.typography.labelLarge,
            )

            cues.forEach { cue ->
                Text(
                    text = "Video ${cue.videoIndex + 1} @ ${formatTime(cue.cueTime)}" +
                        if (cue.description.isNotEmpty()) " - ${cue.description}" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ShareSection(
    shareLink: String,
    onGenerateLink: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onGenerateLink,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate Share Link")
            }

            if (shareLink.isNotEmpty()) {
                SelectionContainer {
                    Text(
                        text = shareLink,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(4.dp))
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordCueDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Sync Cue") },
        text = {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Cue description (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(description) }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ErrorScreen(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = "Error",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000) % 60
    val minutes = (milliseconds / (1000 * 60)) % 60
    val hours = milliseconds / (1000 * 60 * 60)

    return when {
        hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes, seconds)
        else -> String.format("%02d:%02d", minutes, seconds)
    }
}

private fun dpToPx(dp: Int): Int {
    return (dp * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}

/**
 * YouTubePlayerViewContainer - Custom AndroidView with proper lifecycle management
 * 
 * This component properly initializes and releases YouTubePlayerView to prevent
 * surface leaks that cause "Max RTS IDs reached" errors on Vivo/BBK devices.
 */
@Composable
private fun YouTubePlayerViewContainer(
    videoId: String,
    videoIndex: Int,
    offset: Long,
    onPlayerReady: (YouTubePlayer) -> Unit,
    onError: (PlayerConstants.PlayerError) -> Unit,
    onCurrentSecond: (Float) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DebugLogger.i("YouTubePlayerView", "Video $videoIndex: Initializing player - ID: $videoId, Offset: ${offset}ms (${offset / 1000f}s)")

    AndroidView(
        factory = { ctx ->
            DebugLogger.d("YouTubePlayerView", "Video $videoIndex: Creating YouTubePlayerView instance")
            YouTubePlayerView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dpToPx(200),
                )

                // Disable automatic initialization since we're initializing manually
                enableAutomaticInitialization = false
                DebugLogger.d("YouTubePlayerView", "Video $videoIndex: Automatic initialization disabled")

                // Configure WebView with enhanced settings for video playback
                val webView = this.getChildAt(0) as? WebView
                DebugLogger.d("YouTubePlayerView", "Video $videoIndex: WebView found: ${webView != null}")
                webView?.settings?.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    allowFileAccess = false
                    allowContentAccess = false
                    setSupportMultipleWindows(false)
                    setGeolocationEnabled(false)
                    // Enable mixed content (HTTP/HTTPS)
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    // Set user agent to include referrer information
                    userAgentString = "$userAgentString"
                    // Enable caching for better performance
                    cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                    // Enable database storage for WebView
                    databaseEnabled = true
                    // Enable DOM storage
                    domStorageEnabled = true
                    DebugLogger.d("YouTubePlayerView", "Video $videoIndex: WebView settings configured")
                }

                // Set custom WebViewClient to handle URL schemes and referrer
                webView?.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false

                        // Handle intent URLs (YouTube app links)
                        if (url.startsWith("intent://")) {
                            try {
                                val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                                view?.context?.startActivity(intent)
                                DebugLogger.d("YouTubePlayerView", "Video $videoIndex: Intent URL handled")
                                return true
                            } catch (e: Exception) {
                                DebugLogger.w("YouTubePlayerView", "Video $videoIndex: Intent handling failed: ${e.message}")
                                // Intent failed, let WebView handle it normally
                            }
                        }

                        // Allow YouTube to load normally
                        return false
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        DebugLogger.d("YouTubePlayerView", "Video $videoIndex: Page finished loading: $url")
                        // Inject referrer header after page loads
                        view?.loadUrl("javascript:(function() { " +
                            "document.referrer = 'https://www.youtube.com'; " +
                            "})()")
                    }
                }

                // Configure IFramePlayerOptions with proper origin
                // Use https://localhost as origin to avoid embedding restrictions
                val options = IFramePlayerOptions.Builder(ctx)
                    .controls(1)
                    .origin("https://localhost")
                    .autoplay(0)
                    .build()
                DebugLogger.d("YouTubePlayerView", "Video $videoIndex: IFramePlayerOptions configured")

                initialize(object : AbstractYouTubePlayerListener() {
                    override fun onReady(player: YouTubePlayer) {
                        DebugLogger.i("YouTubePlayerListener", "Video $videoIndex: onReady callback triggered")
                        onPlayerReady(player)
                        DebugLogger.i("YouTubePlayerListener", "Video $videoIndex: Cueing video at ${offset / 1000f}s")
                        player.cueVideo(videoId, offset / 1000f)
                    }

                    override fun onCurrentSecond(youtubePlayer: YouTubePlayer, second: Float) {
                        onCurrentSecond(second)
                    }

                    override fun onError(
                        youtubePlayer: YouTubePlayer,
                        error: PlayerConstants.PlayerError,
                    ) {
                        DebugLogger.e("YouTubePlayerListener", "Video $videoIndex: onError callback - ${error.name}")
                        onError(error)
                    }

                    override fun onStateChange(
                        youtubePlayer: YouTubePlayer,
                        state: PlayerConstants.PlayerState,
                    ) {
                        DebugLogger.d("YouTubePlayerListener", "Video $videoIndex: State changed to ${state.name}")
                    }
                }, options)
                DebugLogger.i("YouTubePlayerView", "Video $videoIndex: Player initialized with listener")
            }
        },
        onRelease = { view ->
            // CRITICAL: Properly release YouTubePlayerView to prevent surface leaks
            // This prevents "Max RTS IDs reached" errors on Vivo/BBK devices
            DebugLogger.d("YouTubePlayerView", "Video $videoIndex: Releasing player view")
            try {
                view.removeYouTubePlayerListener()
                view.release()
                DebugLogger.d("YouTubePlayerView", "Video $videoIndex: Player view released successfully")
            } catch (e: Exception) {
                DebugLogger.w("YouTubePlayerView", "Video $videoIndex: Error during release: ${e.message}")
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
    )
}

/**
 * Creates and remembers a PlayerController instance that manages all YouTube players
 */
@Composable
fun rememberPlayerController(): PlayerController {
    return remember { PlayerControllerImpl() }
}

/**
 * Implementation of PlayerController that manages a map of YouTube players
 */
class PlayerControllerImpl : PlayerController {
    private val players = mutableMapOf<Int, YouTubePlayer>()

    override fun playAll() {
        DebugLogger.i("PlayerController", "playAll() called - ${players.size} players registered")
        players.values.forEach { player ->
            player.play()
        }
    }

    override fun pauseAll() {
        DebugLogger.i("PlayerController", "pauseAll() called - ${players.size} players registered")
        players.values.forEach { player ->
            player.pause()
        }
    }

    override fun seekAll(positionMs: Long) {
        DebugLogger.i("PlayerController", "seekAll() called - position: ${positionMs}ms, ${players.size} players")
        players.forEach { (index, player) ->
            DebugLogger.d("PlayerController", "Seeking player $index to ${positionMs}ms")
            player.seekTo(positionMs / 1000f)
        }
    }

    override fun registerPlayer(index: Int, player: YouTubePlayer) {
        players[index] = player
        DebugLogger.d("PlayerController", "Player registered at index $index - Total: ${players.size}")
    }

    override fun unregisterPlayer(index: Int) {
        players.remove(index)
        DebugLogger.d("PlayerController", "Player unregistered at index $index - Total: ${players.size}")
    }
}
