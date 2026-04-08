package com.youkhainda.viewsync.ui.screen

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
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
import org.json.JSONObject

/**
 * Interface to control YouTube players across all video cards
 */
interface PlayerController {
    fun playAll()
    fun pauseAll()
    fun seekAll(positionMs: Long)
    fun registerPlayer(index: Int, player: WebView)
    fun unregisterPlayer(index: Int)
    fun getPlayerTime(index: Int): Float
    fun isPlayerPlaying(index: Int): Boolean
    fun setOnPlaybackStateChangeListener(listener: PlaybackStateChangeListener?)
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
                    onToggleLike = { viewModel.toggleLike() },
                    onToggleSubscribe = { viewModel.toggleSubscribe() },
                    onIncrementShare = { viewModel.incrementShare() },
                    onIncrementComment = { viewModel.incrementComment() },
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
                        viewModel.updatePlaybackState(positionMs, durationMs)
                    }
                }

                override fun onPlayingChanged(index: Int, isPlaying: Boolean) {
                    // Sync play state from actual player
                    if (isPlaying) {
                        viewModel.play()
                    } else {
                        viewModel.pause()
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
            likeCount = syncState.likeCount,
            shareCount = syncState.shareCount,
            commentCount = syncState.commentCount,
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
    var showCueDialog by remember { mutableStateOf(false) }
    var isPlayerReady by remember { mutableStateOf(false) }

    // Validate video ID before attempting to play
    val isValidVideoId = com.youkhainda.viewsync.data.remote.YouTubeUrlParser.isValidVideoId(videoId)
    val context = LocalContext.current

    DebugLogger.d("VideoPlayerCard", "Video $videoIndex: ID=$videoId, Offset=$offset ms, Valid=$isValidVideoId")

    // Register/unregister player with controller
    DisposableEffect(videoIndex) {
        if (isValidVideoId && isPlayerReady) {
            webView?.let { player ->
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
            // YouTube Player - Direct WebView
            if (isValidVideoId) {
                DebugLogger.d("VideoPlayerCard", "Video $videoIndex: Creating WebView for direct YouTube playback")
                DirectYouTubeWebView(
                    videoId = videoId,
                    offset = offset,
                    onWebViewReady = { view ->
                        webView = view
                        isPlayerReady = true
                        playerController.registerPlayer(videoIndex, view)
                        DebugLogger.i("VideoPlayerCard", "Video $videoIndex: WebView ready, registered with controller")
                    },
                    onCurrentSecond = { second ->
                        currentTime = (second * 1000).toLong()
                    },
                    onError = {
                        DebugLogger.e("VideoPlayerCard", "Video $videoIndex: WebView error loading video")
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
    likeCount: Int,
    shareCount: Int,
    commentCount: Int,
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
                // Like button
                ActionButtonWithCount(
                    icon = Icons.Default.Favorite,
                    count = likeCount,
                    isActive = isLiked,
                    activeColor = MaterialTheme.colorScheme.primary,
                    onClick = onToggleLike,
                    label = "Like",
                )

                // Share button
                ActionButtonWithCount(
                    icon = Icons.Default.Share,
                    count = shareCount,
                    isActive = false,
                    onClick = onIncrementShare,
                    label = "Share",
                )

                // Comment button
                ActionButtonWithCount(
                    icon = Icons.Default.Comment,
                    count = commentCount,
                    isActive = false,
                    onClick = onIncrementComment,
                    label = "Comment",
                )

                // Subscribe button
                SubscribeButton(
                    isSubscribed = isSubscribed,
                    onClick = onToggleSubscribe,
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
    count: Int,
    isActive: Boolean,
    activeColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.error,
    onClick: () -> Unit,
    label: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (count > 0) {
                Text(
                    text = if (count >= 1000) "${count / 1000}k" else count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SubscribeButton(
    isSubscribed: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isSubscribed) {
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

private fun dpToPx(dp: Int): Int {
    return (dp * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}

/**
 * DirectYouTubeWebView - WebView that loads YouTube videos directly to bypass embedding restrictions
 * 
 * This loads the full YouTube watch page instead of using the embed player,
 * which avoids embedding restrictions set by video owners.
 */
@Composable
private fun DirectYouTubeWebView(
    videoId: String,
    offset: Long,
    onWebViewReady: (WebView) -> Unit,
    onCurrentSecond: (Float) -> Unit,
    onError: () -> Unit,
) {
    val context = LocalContext.current
    
    DebugLogger.i("DirectYouTubeWebView", "Video: Initializing - ID: $videoId, Offset: ${offset}ms")
    
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
                    cacheMode = WebSettings.LOAD_DEFAULT
                    databaseEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    DebugLogger.d("DirectYouTubeWebView", "Video: WebView settings configured")
                }
                
                // Set WebViewClient to handle page loading
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        DebugLogger.d("DirectYouTubeWebView", "Video: Page finished loading: $url")
                        onWebViewReady(view ?: return)
                    }
                    
                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        super.onReceivedError(view, errorCode, description, failingUrl)
                        DebugLogger.e("DirectYouTubeWebView", "Video: Error loading page - $description")
                        onError()
                    }
                }
                
                // Load YouTube video directly
                val youtubeUrl = "https://www.youtube.com/watch?v=$videoId"
                DebugLogger.i("DirectYouTubeWebView", "Video: Loading YouTube URL: $youtubeUrl")
                loadUrl(youtubeUrl)
            }
        },
        onRelease = { view ->
            DebugLogger.d("DirectYouTubeWebView", "Video: Releasing WebView")
            try {
                view.stopLoading()
                view.loadUrl("about:blank")
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
            while (isActive) {
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
        coroutineScope.cancel()
        players.clear()
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
     * Poll a single player for its current state
     */
    private suspend fun pollPlayer(index: Int, webView: WebView) {
        val script = """
            (function() {
                var video = document.querySelector('video');
                if (video) {
                    JSON.stringify({
                        currentTime: video.currentTime || 0,
                        duration: video.duration || 0,
                        paused: video.paused !== false,
                        ended: video.ended !== false
                    });
                } else {
                    JSON.stringify({error: 'no_video'});
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
            val playScript = """
                (function() {
                    var playButton = document.querySelector('.ytp-play-button') ||
                                     document.querySelector('.ytp-play-btn') ||
                                     document.querySelector('button[aria-label*="Play"]') ||
                                     document.querySelector('button[aria-label*="play"]') ||
                                     document.querySelector('.html5-video-player button[data-title*="Play"]');

                    if (playButton) {
                        playButton.click();
                        return 'Play button clicked';
                    }

                    var video = document.querySelector('video');
                    if (video && video.paused) {
                        video.play();
                        return 'Video play() called';
                    }

                    return 'No play control found';
                })()
            """.trimIndent()

            webView.evaluateJavascript(playScript) { result ->
                DebugLogger.d("PlayerController", "Video $index play result: $result")
            }
        }
    }

    override fun pauseAll() {
        DebugLogger.i("PlayerController", "pauseAll() called - ${players.size} players registered")
        players.forEach { (index, webView) ->
            DebugLogger.d("PlayerController", "Pausing video $index")
            val pauseScript = """
                (function() {
                    var pauseButton = document.querySelector('.ytp-play-button') ||
                                      document.querySelector('.ytp-play-btn') ||
                                      document.querySelector('button[aria-label*="Pause"]') ||
                                      document.querySelector('button[aria-label*="pause"]') ||
                                      document.querySelector('.html5-video-player button[data-title*="Pause"]');

                    if (pauseButton) {
                        pauseButton.click();
                        return 'Pause button clicked';
                    }

                    var video = document.querySelector('video');
                    if (video && !video.paused) {
                        video.pause();
                        return 'Video pause() called';
                    }

                    return 'No pause control found';
                })()
            """.trimIndent()

            webView.evaluateJavascript(pauseScript) { result ->
                DebugLogger.d("PlayerController", "Video $index pause result: $result")
            }
        }
    }

    override fun seekAll(positionMs: Long) {
        DebugLogger.i("PlayerController", "seekAll() called - position: ${positionMs}ms, ${players.size} players")
        players.forEach { (index, webView) ->
            DebugLogger.d("PlayerController", "Seeking player $index to ${positionMs}ms")
            val positionSeconds = positionMs / 1000f
            val seekScript = """
                (function() {
                    var video = document.querySelector('video');
                    if (video && video.duration > 0) {
                        video.currentTime = Math.min($positionSeconds, video.duration);
                        return 'Seeked to $positionSeconds seconds';
                    }
                    return 'No video element found';
                })()
            """.trimIndent()

            webView.evaluateJavascript(seekScript) { result ->
                DebugLogger.d("PlayerController", "Video $index seek result: $result")
            }
        }
    }

    override fun getPlayerTime(index: Int): Float {
        return playerStates[index]?.currentTime ?: 0f
    }

    override fun isPlayerPlaying(index: Int): Boolean {
        return playerStates[index]?.isPlaying ?: false
    }

    override fun setOnPlaybackStateChangeListener(listener: PlaybackStateChangeListener?) {
        playbackListener = listener
    }

    override fun registerPlayer(index: Int, player: WebView) {
        players[index] = player
        playerStates[index] = PlayerState()
        DebugLogger.d("PlayerController", "Player registered at index $index - Total: ${players.size}")

        // Start polling if this is the first player
        if (players.size == 1) {
            startPolling()
        }
    }

    override fun unregisterPlayer(index: Int) {
        players.remove(index)
        playerStates.remove(index)
        DebugLogger.d("PlayerController", "Player unregistered at index $index - Total: ${players.size}")

        // Stop polling if no players left
        if (players.isEmpty()) {
            stopPolling()
        }
    }
}
