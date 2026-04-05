package com.youkhainda.viewsync.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youkhainda.viewsync.data.model.SyncCue
import com.youkhainda.viewsync.data.model.SyncSession
import com.youkhainda.viewsync.data.model.SyncState
import com.youkhainda.viewsync.data.model.YouTubeVideo
import com.youkhainda.viewsync.data.repository.SyncRepository
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
        viewModelScope.launch {
            _uiState.value = SyncPlayerUiState.Loading

            try {
                val session = repository.getSyncSession(sessionId)
                if (session != null) {
                    currentSessionId = sessionId
                    val offsets = repository.calculateVideoOffsets(sessionId)
                    _videoOffsets.value = offsets
                    _uiState.value = SyncPlayerUiState.Success(session)
                } else {
                    _uiState.value = SyncPlayerUiState.Error("Session not found")
                }
            } catch (e: Exception) {
                _uiState.value = SyncPlayerUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun play() {
        _syncState.value = _syncState.value.copy(isPlaying = true)
    }

    fun pause() {
        _syncState.value = _syncState.value.copy(isPlaying = false)
    }

    fun seekToPosition(positionMs: Long) {
        _syncState.value = _syncState.value.copy(currentPlayPosition = positionMs)
    }

    fun recordSyncCue(videoIndex: Int, cueTimeMs: Long, description: String = "") {
        viewModelScope.launch {
            val sessionId = currentSessionId ?: return@launch
            val cue = SyncCue(
                videoIndex = videoIndex,
                cueTime = cueTimeMs,
                description = description,
            )
            repository.addSyncCue(sessionId, cue)
            
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

    fun removeSyncCue(cueIndex: Int) {
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
        viewModelScope.launch {
            val sessionId = currentSessionId ?: return@launch
            val link = repository.generateShareLink(sessionId)
            _uiState.value = let {
                if (it is SyncPlayerUiState.Success) {
                    it.copy(shareLink = link)
                } else {
                    it
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
            
            try {
                val results = repository.searchYouTubeVideos(query)
                _searchResults.value = results
            } catch (e: Exception) {
                _error.value = e.message ?: "Search failed"
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
