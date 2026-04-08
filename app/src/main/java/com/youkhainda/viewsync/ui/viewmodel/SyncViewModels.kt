package com.youkhainda.viewsync.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youkhainda.viewsync.data.model.SyncCue
import com.youkhainda.viewsync.data.model.SyncSession
import com.youkhainda.viewsync.data.model.SyncState
import com.youkhainda.viewsync.data.model.YouTubeVideo
import com.youkhainda.viewsync.data.remote.YouTubeUrlParser
import com.youkhainda.viewsync.data.repository.SyncRepository
import com.youkhainda.viewsync.util.DebugLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncPlayerViewModel @Inject constructor(
    private val repository: SyncRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SyncPlayerUiState>(SyncPlayerUiState.Loading)
    val uiState: StateFlow<SyncPlayerUiState> = _uiState.asStateFlow()

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _videoOffsets = MutableStateFlow<Map<Int, Long>>(emptyMap())
    val videoOffsets: StateFlow<Map<Int, Long>> = _videoOffsets.asStateFlow()

    private var currentSessionId: String? = null

    fun loadSyncSession(sessionId: String) {
        DebugLogger.step("SyncPlayerVM", "loadSyncSession", 1, 3)
        viewModelScope.launch {
            _uiState.value = SyncPlayerUiState.Loading

            try {
                DebugLogger.step("SyncPlayerVM", "Fetching session from repository", 2, 3)
                val session = repository.getSyncSession(sessionId)
                if (session != null) {
                    currentSessionId = sessionId
                    
                    // Load social state from repository
                    val socialState = repository.getSocialState(sessionId)
                    _syncState.value = _syncState.value.copy(
                        isLiked = socialState.isLiked,
                        isSubscribed = socialState.isSubscribed,
                        likeCount = socialState.likeCount,
                        shareCount = socialState.shareCount,
                        commentCount = socialState.commentCount
                    )
                    DebugLogger.d("SyncPlayerVM", "Social state loaded - Liked: ${socialState.isLiked}, Likes: ${socialState.likeCount}")
                    
                    DebugLogger.step("SyncPlayerVM", "Calculating video offsets", 3, 3)
                    val offsets = repository.calculateVideoOffsets(sessionId)
                    _videoOffsets.value = offsets
                    DebugLogger.stepSuccess("SyncPlayerVM", "Session loaded successfully",
                        "Videos: ${session.videoIds.size}, Cues: ${session.syncCues.size}")
                    _uiState.value = SyncPlayerUiState.Success(session)
                } else {
                    DebugLogger.stepFailed("SyncPlayerVM", "Session not found", "Session ID: $sessionId")
                    _uiState.value = SyncPlayerUiState.Error("Session not found")
                }
            } catch (e: Exception) {
                DebugLogger.stepFailed("SyncPlayerVM", "loadSyncSession", e.message ?: "Unknown error", e)
                _uiState.value = SyncPlayerUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun play() {
        DebugLogger.i("SyncPlayerVM", "play() called")
        _syncState.value = _syncState.value.copy(isPlaying = true)
    }

    fun pause() {
        DebugLogger.i("SyncPlayerVM", "pause() called")
        _syncState.value = _syncState.value.copy(isPlaying = false)
    }

    fun seekToPosition(positionMs: Long) {
        DebugLogger.d("SyncPlayerVM", "seekToPosition() called - position: ${positionMs}ms")
        _syncState.value = _syncState.value.copy(currentPlayPosition = positionMs)
    }

    fun updatePlaybackState(positionMs: Long, durationMs: Long) {
        DebugLogger.d("SyncPlayerVM", "updatePlaybackState() - position: ${positionMs}ms, duration: ${durationMs}ms")
        _syncState.value = _syncState.value.copy(
            currentPlayPosition = positionMs,
            videoDuration = durationMs
        )
    }

    fun toggleLike() {
        DebugLogger.i("SyncPlayerVM", "toggleLike() called")
        viewModelScope.launch {
            val sessionId = currentSessionId ?: return@launch
            val newState = repository.toggleLike(sessionId)
            if (newState != null) {
                _syncState.value = _syncState.value.copy(
                    isLiked = newState.isLiked,
                    likeCount = newState.likeCount
                )
                DebugLogger.d("SyncPlayerVM", "Like state updated - Liked: ${newState.isLiked}, Count: ${newState.likeCount}")
            }
        }
    }

    fun toggleSubscribe() {
        DebugLogger.i("SyncPlayerVM", "toggleSubscribe() called")
        viewModelScope.launch {
            val sessionId = currentSessionId ?: return@launch
            val newState = repository.toggleSubscribe(sessionId)
            if (newState != null) {
                _syncState.value = _syncState.value.copy(isSubscribed = newState.isSubscribed)
                DebugLogger.d("SyncPlayerVM", "Subscribe state updated - Subscribed: ${newState.isSubscribed}")
            }
        }
    }

    fun incrementShare() {
        DebugLogger.i("SyncPlayerVM", "incrementShare() called")
        viewModelScope.launch {
            val sessionId = currentSessionId ?: return@launch
            val newState = repository.incrementShare(sessionId)
            if (newState != null) {
                _syncState.value = _syncState.value.copy(shareCount = newState.shareCount)
                DebugLogger.d("SyncPlayerVM", "Share count updated - Count: ${newState.shareCount}")
            }
        }
    }

    fun incrementComment() {
        DebugLogger.i("SyncPlayerVM", "incrementComment() called")
        viewModelScope.launch {
            val sessionId = currentSessionId ?: return@launch
            val newState = repository.incrementComment(sessionId)
            if (newState != null) {
                _syncState.value = _syncState.value.copy(commentCount = newState.commentCount)
                DebugLogger.d("SyncPlayerVM", "Comment count updated - Count: ${newState.commentCount}")
            }
        }
    }

    fun recordSyncCue(videoIndex: Int, cueTimeMs: Long, description: String = "") {
        DebugLogger.i("SyncPlayerVM", "recordSyncCue() - Video: $videoIndex, Time: ${cueTimeMs}ms, Desc: $description")
        viewModelScope.launch {
            val sessionId = currentSessionId ?: return@launch
            val cue = SyncCue(
                videoIndex = videoIndex,
                cueTime = cueTimeMs,
                description = description,
            )
            repository.addSyncCue(sessionId, cue)
            DebugLogger.d("SyncPlayerVM", "Sync cue added to repository")

            // Recalculate offsets
            val offsets = repository.calculateVideoOffsets(sessionId)
            _videoOffsets.value = offsets
            DebugLogger.d("SyncPlayerVM", "Offsets recalculated: $offsets")

            // Refresh session
            val session = repository.getSyncSession(sessionId)
            if (session != null) {
                _uiState.value = SyncPlayerUiState.Success(session)
            }
        }
    }

    fun removeSyncCue(cueIndex: Int) {
        DebugLogger.i("SyncPlayerVM", "removeSyncCue() - Index: $cueIndex")
        viewModelScope.launch {
            val sessionId = currentSessionId ?: return@launch
            repository.removeSyncCue(sessionId, cueIndex)

            // Recalculate offsets
            val offsets = repository.calculateVideoOffsets(sessionId)
            _videoOffsets.value = offsets

            // Refresh session
            val session = repository.getSyncSession(sessionId)
            if (session != null) {
                _uiState.value = SyncPlayerUiState.Success(session)
            }
        }
    }

    fun generateShareLink() {
        DebugLogger.i("SyncPlayerVM", "generateShareLink() called")
        viewModelScope.launch {
            val sessionId = currentSessionId ?: return@launch
            val link = repository.generateShareLink(sessionId)
            DebugLogger.d("SyncPlayerVM", "Share link generated: $link")
            val currentState = _uiState.value
            _uiState.value = when (currentState) {
                is SyncPlayerUiState.Success -> currentState.copy(shareLink = link)
                else -> currentState
            }
        }
    }

    fun addVideosToSession(sessionId: String, videos: List<YouTubeVideo>) {
        DebugLogger.i("SyncPlayerVM", "addVideosToSession() - Session: $sessionId, Videos: ${videos.size}")
        viewModelScope.launch {
            val updatedSession = repository.addVideosToSession(sessionId, videos)
            if (updatedSession != null) {
                // Only update offsets and UI if this is the current session
                if (currentSessionId == sessionId) {
                    val offsets = repository.calculateVideoOffsets(sessionId)
                    _videoOffsets.value = offsets
                    _uiState.value = SyncPlayerUiState.Success(updatedSession)
                    DebugLogger.i("SyncPlayerVM", "Videos added successfully - Total videos: ${updatedSession.videoIds.size}")
                }
            }
        }
    }
}

sealed class SyncPlayerUiState {
    data object Loading : SyncPlayerUiState()
    data class Success(
        val session: SyncSession,
        val shareLink: String = "",
    ) : SyncPlayerUiState()
    data class Error(val message: String) : SyncPlayerUiState()
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SyncRepository,
) : ViewModel() {

    private val _searchResults = MutableStateFlow<List<YouTubeVideo>>(emptyList())
    val searchResults: StateFlow<List<YouTubeVideo>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun searchVideos(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            // Validate input
            if (query.isBlank()) {
                _error.value = "Please enter a search query or YouTube URL"
                _isLoading.value = false
                return@launch
            }

            // Check if it's a YouTube URL but invalid format
            if (YouTubeUrlParser.isYouTubeUrl(query)) {
                val videoId = YouTubeUrlParser.extractVideoId(query)
                if (videoId == null) {
                    _error.value = "Invalid YouTube URL format. Please check the URL and try again."
                    _isLoading.value = false
                    _searchResults.value = emptyList()
                    return@launch
                }
            }

            try {
                val results = repository.searchYouTubeVideos(query)
                _searchResults.value = results
                
                // Provide feedback if no results found
                if (results.isEmpty()) {
                    if (YouTubeUrlParser.isYouTubeUrl(query)) {
                        _error.value = "Video not found. The video may be private or deleted."
                    } else {
                        _error.value = "No videos found for \"$query\""
                    }
                }
            } catch (e: Exception) {
                _error.value = if (YouTubeUrlParser.isYouTubeUrl(query)) {
                    "Failed to load video: ${e.message ?: "Unknown error"}"
                } else {
                    "Search failed: ${e.message ?: "Unknown error"}"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private val _createdSessionId = MutableStateFlow<String?>(null)
    val createdSessionId: StateFlow<String?> = _createdSessionId.asStateFlow()

    fun createSyncSession(name: String, videos: List<YouTubeVideo>) {
        viewModelScope.launch {
            try {
                val session = repository.createSyncSession(name, videos)
                _createdSessionId.value = session.id
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to create session"
                _createdSessionId.value = null
            }
        }
    }

    fun clearCreatedSessionId() {
        _createdSessionId.value = null
    }
}
