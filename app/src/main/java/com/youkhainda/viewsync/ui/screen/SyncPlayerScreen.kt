package com.youkhainda.viewsync.ui.screen

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
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
import com.youkhainda.viewsync.data.model.SyncSession
import com.youkhainda.viewsync.ui.viewmodel.SyncPlayerUiState
import com.youkhainda.viewsync.ui.viewmodel.SyncPlayerViewModel
import com.youkhainda.viewsync.util.CacheClearingUtil
import com.youkhainda.viewsync.util.DebugLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.cancel
import org.json.JSONObject

/**
 * Banner that prompts user to sign in with Google.
 * Shown when the user is not authenticated for social actions.
 */
@Composable
private fun GoogleSignInBanner(onSignInClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sign in to YouTube",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = "Enable real likes, subscribes, and comments",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Button(
                onClick = onSignInClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text("Sign In")
            }
        }
    }
}

/**
 * JavaScript interface to receive callbacks from YouTube IFrame API
 */
class YouTubePlayerInterface(
    private val onPlayerReady: () -> Unit,
    private val onStateChange: (Int, Float, Float) -> Unit,
    private val onError: (Int) -> Unit,
) {
    @JavascriptInterface
    fun onPlayerReady() {
        onPlayerReady()
    }

    @JavascriptInterface
    fun onStateChange(stateJson: String) {
        try {
            val json = org.json.JSONObject(stateJson)
            val state = json.optInt("state", -1)
            val currentTime = json.optDouble("currentTime", 0.0).toFloat()
            val duration = json.optDouble("duration", 0.0).toFloat()
            onStateChange(state, currentTime, duration)
        } catch (e: Exception) {
            DebugLogger.w("YouTubePlayerInterface", "Error parsing state: ${e.message}")
        }
    }

    @JavascriptInterface
    fun onError(errorCode: Int) {
        DebugLogger.e("YouTubePlayerInterface", "YouTube Player Error - Code: $errorCode")
        
        // Provide helpful error messages for common error codes
        val errorMessage = when (errorCode) {
            2 -> "Invalid video ID or parameter"
            5 -> "Content cannot be played in embedded player (restriction)"
            100 -> "Video not found or removed"
            101, 150 -> "Video owner has disabled embedding"
            152 -> "Embedding blocked by security/privacy settings. Try disabling ad-blockers."
            -1 -> "Player initialization failed or network error"
            else -> "Unknown error (code: $errorCode)"
        }
        
        DebugLogger.e("YouTubePlayerInterface", errorMessage)
        onError(errorCode)
    }
}

/**
 * Interface to control YouTube players across all video cards
 */
interface PlayerController {
    fun playAll()
    fun pauseAll()
    fun seekAll(positionMs: Long)
    fun registerPlayer(index: Int, player: WebView, playerInterface: YouTubePlayerInterface)
    fun unregisterPlayer(index: Int)
    fun getPlayerTime(index: Int): Float
    fun isPlayerPlaying(index: Int): Boolean
    fun setOnPlaybackStateChangeListener(listener: PlaybackStateChangeListener?)
    fun isPlayerReady(index: Int): Boolean
}

/**
 * Listener for playback state changes from WebView players
 */
interface PlaybackStateChangeListener {
    fun onTimeUpdate(index: Int, currentTimeSeconds: Float, durationSeconds: Float)
    fun onPlayingChanged(index: Int, isPlaying: Boolean)
}

@Composable
fun SyncPlayerScreen(
    sessionId: String,
    viewModel: SyncPlayerViewModel = hiltViewModel(),
    onAddVideo: () -> Unit = {},
    onSignInRequired: () -> Unit = {},
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

                Column(modifier = Modifier.fillMaxSize()) {
                    // Google Sign-In Banner (if not authenticated)
                    if (!viewModel.isUserAuthenticated()) {
                        GoogleSignInBanner(
                            onSignInClick = onSignInRequired,
                        )
                    }

                    SyncPlayerContent(
                        session = state.session,
                        shareLink = state.shareLink,
                        syncState = syncState,
                        videoOffsets = videoOffsets,
                        isAuthenticated = viewModel.isUserAuthenticated(),
                        userName = viewModel.getCurrentUserName(),
                        onPlay = { viewModel.play() },
                        onPause = { viewModel.pause() },
                        onSeek = { viewModel.seekToPosition(it) },
                        onUpdatePlaybackState = { pos, dur -> viewModel.updatePlaybackState(pos, dur) },
                        onRecordCue = { videoIdx, time, desc -> viewModel.recordSyncCue(videoIdx, time, desc) },
                        onGenerateLink = { viewModel.generateShareLink() },
                        onAddVideo = onAddVideo,
                        onToggleLike = { viewModel.toggleLike() },
                        onToggleSubscribe = { viewModel.toggleSubscribe() },
                        onIncrementShare = { viewModel.incrementShare() },
                        onIncrementComment = { viewModel.incrementComment() },
                    )
                }
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
    isAuthenticated: Boolean,
    userName: String?,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onUpdatePlaybackState: (Long, Long) -> Unit,
    onRecordCue: (Int, Long, String) -> Unit,
    onGenerateLink: () -> Unit,
    onAddVideo: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onIncrementShare: () -> Unit,
    onIncrementComment: () -> Unit,
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
                    // Show auth status
                    if (isAuthenticated && userName != null) {
                        Text(
                            text = "Signed in as: $userName",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                        )
                    }
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
        val lifecycleOwner = LocalLifecycleOwner.current

        // Playback state listener to sync time to UI
        val playbackListener = remember {
            object : PlaybackStateChangeListener {
                override fun onTimeUpdate(index: Int, currentTimeSeconds: Float, durationSeconds: Float) {
                    // Update syncState with current playback position (use first video as master)
                    if (index == 0) {
                        val positionMs = (currentTimeSeconds * 1000).toLong()
                        val durationMs = (durationSeconds * 1000).toLong()
                        onUpdatePlaybackState(positionMs, durationMs)
                    }
                }

                override fun onPlayingChanged(index: Int, isPlaying: Boolean) {
                    // Sync play state from actual player
                    if (isPlaying) {
                        onPlay()
                    } else {
                        onPause()
                    }
                }
            }
        }

        // Register playback listener
        LaunchedEffect(playerController) {
            playerController.setOnPlaybackStateChangeListener(playbackListener)
        }

        DisposableEffect(lifecycleOwner, playerController) {
            onDispose {
                playerController.setOnPlaybackStateChangeListener(null)
            }
        }

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
            duration = syncState.videoDuration,
            isLiked = syncState.isLiked,
            isSubscribed = syncState.isSubscribed,
            videoLikeCount = syncState.videoLikeCount,
            videoShareCount = syncState.videoShareCount,
            videoCommentCount = syncState.videoCommentCount,
            videoViewCount = syncState.videoViewCount,
            isAuthenticated = isAuthenticated,
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
            onToggleLike = onToggleLike,
            onToggleSubscribe = onToggleSubscribe,
            onIncrementShare = onIncrementShare,
            onIncrementComment = onIncrementComment,
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
    var webView by remember { mutableStateOf<WebView?>(null) }
    var playerInterface by remember { mutableStateOf<YouTubePlayerInterface?>(null) }
    var showCueDialog by remember { mutableStateOf(false) }
    var isPlayerReady by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<Int?>(null) }

    // Validate video ID before attempting to play
    val isValidVideoId = com.youkhainda.viewsync.data.remote.YouTubeUrlParser.isValidVideoId(videoId)
    val context = LocalContext.current

    DebugLogger.d("VideoPlayerCard", "Video $videoIndex: ID=$videoId, Offset=$offset ms, Valid=$isValidVideoId")

    // Register/unregister player with controller
    DisposableEffect(videoIndex) {
        if (isValidVideoId && isPlayerReady) {
            webView?.let { player ->
                playerInterface?.let { iface ->
                    playerController.registerPlayer(videoIndex, player, iface)
                    DebugLogger.d("VideoPlayerCard", "Video $videoIndex: Registered with controller")
                }
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
            // Error banner (if any)
            playerError?.let { errorCode ->
                val errorMessage = when (errorCode) {
                    2 -> "Invalid video ID or parameter"
                    5 -> "Content cannot be played in embedded player"
                    100 -> "Video not found or removed"
                    101, 150 -> "Video owner has disabled embedding"
                    152 -> "Error 152-4: Embedding blocked. Try: disabling ad-blockers, clearing cache, or using test video dQw4w9WgXcQ"
                    -1 -> "Player initialization failed. Check internet connection."
                    else -> "Player error (code: $errorCode)"
                }
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            
            // YouTube Player - Direct WebView
            if (isValidVideoId) {
                DebugLogger.d("VideoPlayerCard", "Video $videoIndex: Creating WebView for direct YouTube playback")
                DirectYouTubeWebView(
                    videoId = videoId,
                    offset = offset,
                    onWebViewReady = { view, iface ->
                        webView = view
                        playerInterface = iface
                        isPlayerReady = true
                        playerError = null // Clear error when player is ready
                        playerController.registerPlayer(videoIndex, view, iface)
                        DebugLogger.i("VideoPlayerCard", "Video $videoIndex: WebView ready, registered with controller")
                    },
                    onCurrentSecond = { second ->
                        currentTime = (second * 1000).toLong()
                    },
                    onError = { errorCode ->
                        playerError = errorCode
                        DebugLogger.e("VideoPlayerCard", "Video $videoIndex: Player error - Code: $errorCode")
                    },
                )
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

@Composable
private fun PlaybackControlsSection(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    isLiked: Boolean,
    isSubscribed: Boolean,
    videoLikeCount: Long,
    videoShareCount: Long,
    videoCommentCount: Long,
    videoViewCount: Long,
    isAuthenticated: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleLike: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onIncrementShare: () -> Unit,
    onIncrementComment: () -> Unit,
) {
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    // Update slider when position changes (but not during drag)
    LaunchedEffect(currentPosition, isDragging) {
        if (!isDragging && duration > 0) {
            sliderPosition = (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        }
    }

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

            // Progress slider
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Slider(
                    value = sliderPosition,
                    onValueChange = {
                        isDragging = true
                        sliderPosition = it
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        val seekPosition = (sliderPosition * duration).toLong()
                        onSeek(seekPosition)
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatTime(currentPosition),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatTime(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Play/Pause button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FloatingActionButton(
                    onClick = if (isPlaying) onPause else onPlay,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                    )
                }
            }

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Like button - shows real YouTube like count
                ActionButtonWithCount(
                    icon = Icons.Default.Favorite,
                    count = videoLikeCount,
                    isActive = isLiked,
                    activeColor = MaterialTheme.colorScheme.primary,
                    onClick = onToggleLike,
                    label = "Like",
                    enabled = isAuthenticated,
                )

                // Share button - shows view count as proxy
                ActionButtonWithCount(
                    icon = Icons.Default.Share,
                    count = videoViewCount,
                    isActive = false,
                    onClick = onIncrementShare,
                    label = "Views",
                    enabled = true, // Views is always enabled (local only)
                )

                // Comment button - shows real YouTube comment count
                ActionButtonWithCount(
                    icon = Icons.Default.Comment,
                    count = videoCommentCount,
                    isActive = false,
                    onClick = onIncrementComment,
                    label = "Comments",
                    enabled = isAuthenticated,
                )

                // Subscribe button
                SubscribeButton(
                    isSubscribed = isSubscribed,
                    onClick = onToggleSubscribe,
                    enabled = isAuthenticated,
                )
            }
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
private fun ActionButtonWithCount(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Long,
    isActive: Boolean,
    activeColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.error,
    onClick: () -> Unit,
    label: String,
    enabled: Boolean = true,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            enabled = enabled,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (!enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                } else if (isActive) {
                    activeColor
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp),
            )
        }
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (count > 0) {
                Text(
                    text = formatCount(count),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (!enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    } else if (isActive) {
                        activeColor
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (!enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun SubscribeButton(
    isSubscribed: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (!enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                } else if (isSubscribed) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                }
            ),
            modifier = Modifier.height(36.dp),
        ) {
            Icon(
                imageVector = if (isSubscribed) Icons.Default.Check else Icons.Default.Notifications,
                contentDescription = if (isSubscribed) "Subscribed" else "Subscribe",
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isSubscribed) "Subscribed" else "Subscribe",
                style = MaterialTheme.typography.labelMedium,
            )
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

/**
 * Formats large counts with k/M suffixes for display
 */
private fun formatCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 10_000 -> String.format("%.0fk", count / 1_000.0)
        count >= 1_000 -> String.format("%.1fk", count / 1_000.0)
        else -> count.toString()
    }
}

private fun dpToPx(dp: Int): Int {
    return (dp * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}

/**
 * DirectYouTubeWebView - WebView that loads YouTube videos using the IFrame Player API
 *
 * This uses the official YouTube IFrame Player API which provides reliable
 * JavaScript methods for playback control (playVideo, pauseVideo, seekTo, etc.)
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DirectYouTubeWebView(
    videoId: String,
    offset: Long,
    onWebViewReady: (WebView, YouTubePlayerInterface) -> Unit,
    onCurrentSecond: (Float) -> Unit,
    onError: () -> Unit,
) {
    val context = LocalContext.current

    // Create JavaScript interface once
    val playerInterface = remember {
        YouTubePlayerInterface(
            onPlayerReady = {
                DebugLogger.i("DirectYouTubeWebView", "YouTube IFrame API is ready")
            },
            onStateChange = { state, currentTime, duration ->
                DebugLogger.d("DirectYouTubeWebView", "State changed - State: $state, Time: $currentTime/$duration")
                if (state == 1) { // Playing
                    onCurrentSecond(currentTime)
                }
            },
            onError = { errorCode ->
                DebugLogger.e("DirectYouTubeWebView", "Player error - Code: $errorCode")
                onError()
            },
        )
    }

    DebugLogger.i("DirectYouTubeWebView", "Video: Initializing - ID: $videoId, Offset: ${offset}ms")

    // Create the HTML page with YouTube IFrame Player API
    val htmlContent = remember(videoId) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <meta name="referrer" content="no-referrer-when-downgrade">
            <style>
                body { margin: 0; padding: 0; background: #000; overflow: hidden; }
                #player { width: 100%; height: 100vh; }
            </style>
        </head>
        <body>
            <div id="player"></div>
            <script>
                var player;
                var isReady = false;
                var statePollingInterval = null;
                var initTimeout = null;

                // Load YouTube IFrame API
                var tag = document.createElement('script');
                tag.src = "https://www.youtube.com/iframe_api";
                var firstScriptTag = document.getElementsByTagName('script')[0];
                firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

                // Set timeout to detect if API fails to load
                initTimeout = setTimeout(function() {
                    if (!isReady) {
                        console.error('YouTube IFrame API initialization timeout');
                        try {
                            Android.onError(-1);
                        } catch(e) {}
                    }
                }, 15000); // 15 second timeout

                function onYouTubeIframeAPIReady() {
                    console.log('YouTube IFrame API loaded');
                    if (initTimeout) {
                        clearTimeout(initTimeout);
                        initTimeout = null;
                    }
                    player = new YT.Player('player', {
                        height: '100%',
                        width: '100%',
                        videoId: '$videoId',
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
                        events: {
                            'onReady': onPlayerReady,
                            'onStateChange': onPlayerStateChange,
                            'onError': onPlayerError
                        }
                    });
                }

                function onPlayerReady(event) {
                    isReady = true;
                    console.log('Player is ready');
                    // Notify Android via JavaScript interface
                    try {
                        Android.onPlayerReady();
                    } catch(e) {
                        console.log('Failed to notify Android: ' + e);
                    }

                    // Start polling state as backup
                    startStatePolling();
                }

                function onPlayerStateChange(event) {
                    var state = {
                        state: event.data,
                        currentTime: player.getCurrentTime(),
                        duration: player.getDuration()
                    };
                    console.log('State changed: ' + JSON.stringify(state));

                    // Notify Android via JavaScript interface
                    try {
                        Android.onStateChange(JSON.stringify(state));
                    } catch(e) {
                        console.log('Failed to notify Android: ' + e);
                    }
                }

                function onPlayerError(event) {
                    console.log('Player error: ' + event.data);
                    try {
                        Android.onError(event.data);
                    } catch(e) {
                        console.log('Failed to notify Android: ' + e);
                    }
                }

                function startStatePolling() {
                    if (statePollingInterval) return;
                    statePollingInterval = setInterval(function() {
                        if (isReady && player) {
                            var state = {
                                state: player.getPlayerState(),
                                currentTime: player.getCurrentTime(),
                                duration: player.getDuration()
                            };
                            try {
                                Android.onStateChange(JSON.stringify(state));
                            } catch(e) {}
                        }
                    }, 500);
                }

                function stopStatePolling() {
                    if (statePollingInterval) {
                        clearInterval(statePollingInterval);
                        statePollingInterval = null;
                    }
                }

                // Expose player methods to Android (kept for backward compatibility)
                window.playVideo = function() {
                    if (player && isReady && player.playVideo) {
                        player.playVideo();
                        return 'played';
                    }
                    return 'not_ready';
                };

                window.pauseVideo = function() {
                    if (player && isReady && player.pauseVideo) {
                        player.pauseVideo();
                        return 'paused';
                    }
                    return 'not_ready';
                };

                window.seekTo = function(seconds) {
                    if (player && isReady && player.seekTo) {
                        player.seekTo(seconds, true);
                        return 'seeked';
                    }
                    return 'not_ready';
                };

                window.getCurrentTime = function() {
                    if (player && isReady && player.getCurrentTime) {
                        return player.getCurrentTime();
                    }
                    return 0;
                };

                window.getDuration = function() {
                    if (player && isReady && player.getDuration) {
                        return player.getDuration();
                    }
                    return 0;
                };

                window.getPlayerState = function() {
                    if (player && isReady && player.getPlayerState) {
                        return player.getPlayerState();
                    }
                    return -1;
                };

                window.isPlayerReady = function() {
                    return isReady;
                };

                // Cleanup on page unload
                window.addEventListener('beforeunload', function() {
                    stopStatePolling();
                });
            </script>
        </body>
        </html>
    """.trimIndent()
    }

    AndroidView(
        factory = { ctx ->
            DebugLogger.d("DirectYouTubeWebView", "Video: Creating WebView instance")
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dpToPx(200),
                )

                // Configure WebView settings for video playback
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    allowFileAccess = false
                    allowContentAccess = false
                    setSupportMultipleWindows(false)
                    setGeolocationEnabled(false)
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    databaseEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setAppCacheEnabled(false)
                    javaScriptCanOpenWindowsAutomatically = true
                    DebugLogger.d("DirectYouTubeWebView", "Video: WebView settings configured")
                }

                // Add JavaScript interface
                addJavascriptInterface(playerInterface, "Android")

                // Set WebViewClient to handle page loading
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        DebugLogger.d("DirectYouTubeWebView", "Video: HTML wrapper loaded, IFrame API will initialize")
                        
                        // Inject referrer to help bypass security blocks
                        view?.loadUrl("javascript:(function() { " +
                            "document.referrer = 'https://www.youtube.com'; " +
                            "console.log('Referrer set to: ' + document.referrer); " +
                            "})()")
                        
                        onWebViewReady(view ?: return, playerInterface)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        super.onReceivedError(view, errorCode, description, failingUrl)
                        DebugLogger.e("DirectYouTubeWebView", "Video: Error loading page - $description (Code: $errorCode)")
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: android.webkit.WebResourceResponse?
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        val statusCode = errorResponse?.statusCode
                        val url = request?.url?.toString()
                        if (statusCode != null && url != null) {
                            DebugLogger.w("DirectYouTubeWebView", "HTTP error: $statusCode for $url")
                        }
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        // Prevent navigation away from YouTube player
                        val url = request?.url?.toString()
                        if (url != null && !url.contains("youtube.com") && !url.contains("googlevideo.com")) {
                            DebugLogger.d("DirectYouTubeWebView", "Blocking navigation to: $url")
                            return true
                        }
                        return false
                    }
                }

                // Load the HTML content with IFrame API
                DebugLogger.i("DirectYouTubeWebView", "Video: Loading IFrame Player HTML")
                loadDataWithBaseURL("https://www.youtube.com", htmlContent, "text/html", "UTF-8", null)
            }
        },
        update = { view ->
            // This is called on recomposition - do nothing to prevent WebView recreation
        },
        onRelease = { view ->
            DebugLogger.d("DirectYouTubeWebView", "Video: Releasing WebView")
            try {
                view.stopLoading()
                view.loadUrl("about:blank")
                view.removeJavascriptInterface("Android")
                view.destroy()
                DebugLogger.d("DirectYouTubeWebView", "Video: WebView released successfully")
            } catch (e: Exception) {
                DebugLogger.w("DirectYouTubeWebView", "Video: Error during release: ${e.message}")
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
 * Implementation of PlayerController that manages a map of WebViews
 */
class PlayerControllerImpl : PlayerController {
    private val players = mutableMapOf<Int, WebView>()
    private val playerInterfaces = mutableMapOf<Int, YouTubePlayerInterface>()
    private val playerReadyStates = mutableMapOf<Int, Boolean>()
    private val playerStates = mutableMapOf<Int, PlayerState>()
    private var playbackListener: PlaybackStateChangeListener? = null
    private var pollingJob: Job? = null
    private val coroutineScope = kotlinx.coroutines.CoroutineScope(
        Dispatchers.Main + SupervisorJob()
    )

    data class PlayerState(
        val currentTime: Float = 0f,
        val duration: Float = 0f,
        val isPlaying: Boolean = false
    )

    /**
     * Start polling all players for playback state updates
     */
    fun startPolling(intervalMs: Long = 500L) {
        pollingJob?.cancel()
        pollingJob = coroutineScope.launch {
            while (currentCoroutineContext().isActive) {
                pollAllPlayers()
                delay(intervalMs)
            }
        }
    }

    /**
     * Stop polling players
     */
    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /**
     * Cleanup resources when controller is no longer needed
     */
    fun cleanup() {
        stopPolling()
        coroutineScope.coroutineContext.cancel()
        players.clear()
        playerInterfaces.clear()
        playerReadyStates.clear()
        playerStates.clear()
        playbackListener = null
    }

    /**
     * Poll all players for current playback state
     */
    private suspend fun pollAllPlayers() {
        players.forEach { (index, webView) ->
            pollPlayer(index, webView)
        }
    }

    /**
     * Poll a single player for its current state using IFrame API
     */
    private suspend fun pollPlayer(index: Int, webView: WebView) {
        val script = """
            (function() {
                if (window.isPlayerReady && window.isPlayerReady()) {
                    var state = window.getPlayerState();
                    var currentTime = window.getCurrentTime();
                    var duration = window.getDuration();
                    JSON.stringify({
                        currentTime: currentTime || 0,
                        duration: duration || 0,
                        paused: state !== 1,
                        ended: state === 0
                    });
                } else {
                    JSON.stringify({error: 'not_ready'});
                }
            })()
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->
            try {
                if (result != null && result != "null") {
                    val json = JSONObject(result)
                    if (!json.has("error")) {
                        val currentTime = json.optDouble("currentTime", 0.0).toFloat()
                        val duration = json.optDouble("duration", 0.0).toFloat()
                        val isPlaying = !json.optBoolean("paused", true)
                        val isEnded = json.optBoolean("ended", false)

                        val newState = PlayerState(
                            currentTime = currentTime,
                            duration = duration,
                            isPlaying = if (isEnded) false else isPlaying
                        )

                        val oldState = playerStates[index]
                        playerStates[index] = newState

                        // Notify listener of time updates
                        if (duration > 0) {
                            playbackListener?.onTimeUpdate(index, currentTime, duration)
                        }

                        // Notify listener of playing state changes
                        if (oldState?.isPlaying != newState.isPlaying) {
                            playbackListener?.onPlayingChanged(index, newState.isPlaying)
                        }
                    }
                }
            } catch (e: Exception) {
                DebugLogger.w("PlayerController", "Error parsing state for player $index: ${e.message}")
            }
        }
    }

    override fun playAll() {
        DebugLogger.i("PlayerController", "playAll() called - ${players.size} players registered")
        players.forEach { (index, webView) ->
            DebugLogger.d("PlayerController", "Playing video $index")

            // Check if player is ready first
            webView.evaluateJavascript("window.isPlayerReady && window.isPlayerReady()") { readyResult ->
                if (readyResult == "true") {
                    val playScript = """
                        (function() {
                            if (window.playVideo) {
                                window.playVideo();
                            } else {
                                'no_api';
                            }
                        })()
                    """.trimIndent()

                    webView.evaluateJavascript(playScript) { result ->
                        DebugLogger.d("PlayerController", "Video $index play result: $result")
                    }
                } else {
                    DebugLogger.w("PlayerController", "Video $index not ready yet, skipping play")
                }
            }
        }
    }

    override fun pauseAll() {
        DebugLogger.i("PlayerController", "pauseAll() called - ${players.size} players registered")
        players.forEach { (index, webView) ->
            DebugLogger.d("PlayerController", "Pausing video $index")

            // Check if player is ready first
            webView.evaluateJavascript("window.isPlayerReady && window.isPlayerReady()") { readyResult ->
                if (readyResult == "true") {
                    val pauseScript = """
                        (function() {
                            if (window.pauseVideo) {
                                window.pauseVideo();
                            } else {
                                'no_api';
                            }
                        })()
                    """.trimIndent()

                    webView.evaluateJavascript(pauseScript) { result ->
                        DebugLogger.d("PlayerController", "Video $index pause result: $result")
                    }
                } else {
                    DebugLogger.w("PlayerController", "Video $index not ready yet, skipping pause")
                }
            }
        }
    }

    override fun seekAll(positionMs: Long) {
        DebugLogger.i("PlayerController", "seekAll() called - position: ${positionMs}ms, ${players.size} players")
        players.forEach { (index, webView) ->
            DebugLogger.d("PlayerController", "Seeking player $index to ${positionMs}ms")
            val positionSeconds = positionMs / 1000f

            // Check if player is ready first
            webView.evaluateJavascript("window.isPlayerReady && window.isPlayerReady()") { readyResult ->
                if (readyResult == "true") {
                    val seekScript = """
                        (function() {
                            if (window.seekTo) {
                                window.seekTo($positionSeconds);
                            } else {
                                'no_api';
                            }
                        })()
                    """.trimIndent()

                    webView.evaluateJavascript(seekScript) { result ->
                        DebugLogger.d("PlayerController", "Video $index seek result: $result")
                    }
                } else {
                    DebugLogger.w("PlayerController", "Video $index not ready yet, skipping seek")
                }
            }
        }
    }

    override fun getPlayerTime(index: Int): Float {
        return playerStates[index]?.currentTime ?: 0f
    }

    override fun isPlayerPlaying(index: Int): Boolean {
        return playerStates[index]?.isPlaying ?: false
    }

    override fun isPlayerReady(index: Int): Boolean {
        return playerReadyStates[index] ?: false
    }

    override fun setOnPlaybackStateChangeListener(listener: PlaybackStateChangeListener?) {
        playbackListener = listener
    }

    override fun registerPlayer(index: Int, player: WebView, playerInterface: YouTubePlayerInterface) {
        players[index] = player
        playerInterfaces[index] = playerInterface
        playerReadyStates[index] = false // Will be set to true when IFrame API is ready
        playerStates[index] = PlayerState()
        DebugLogger.d("PlayerController", "Player registered at index $index - Total: ${players.size}")

        // Start polling if this is the first player
        if (players.size == 1) {
            startPolling()
        }
    }

    override fun unregisterPlayer(index: Int) {
        players.remove(index)
        playerInterfaces.remove(index)
        playerReadyStates.remove(index)
        playerStates.remove(index)
        DebugLogger.d("PlayerController", "Player unregistered at index $index - Total: ${players.size}")

        // Stop polling if no players left
        if (players.isEmpty()) {
            stopPolling()
        }
    }
}
