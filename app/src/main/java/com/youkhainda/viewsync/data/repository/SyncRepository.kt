package com.youkhainda.viewsync.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.youkhainda.viewsync.data.model.SyncSession
import com.youkhainda.viewsync.data.model.SyncCue
import com.youkhainda.viewsync.data.model.YouTubeVideo
import com.youkhainda.viewsync.data.remote.YouTubeApiService
import com.youkhainda.viewsync.data.remote.YouTubeUrlParser
import com.youkhainda.viewsync.data.remote.parseDuration
import com.youkhainda.viewsync.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepository @Inject constructor(
    private val youtubeApi: YouTubeApiService,
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
    @ApplicationContext private val context: Context,
) {

    // In-memory cache for fast access
    private val syncSessions = mutableMapOf<String, SyncSession>()
    
    // Track the last active session ID for restoration
    private var lastActiveSessionId: String? = null

    // DataStore keys
    private val SESSIONS_KEY = stringPreferencesKey("sync_sessions")
    private val LAST_ACTIVE_SESSION_KEY = stringPreferencesKey("last_active_session_id")

    /**
     * Initializes the repository by loading sessions from persistent storage
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            val preferences = dataStore.data.first()
            
            // Load sessions
            val sessionsJson = preferences[SESSIONS_KEY]
            if (sessionsJson != null) {
                val sessionsList = json.decodeFromString<List<SyncSession>>(sessionsJson)
                syncSessions.clear()
                sessionsList.forEach { session ->
                    syncSessions[session.id] = session
                }
            }
            
            // Load last active session
            lastActiveSessionId = preferences[LAST_ACTIVE_SESSION_KEY]
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Persists all sessions to DataStore
     */
    private suspend fun persistSessions() = withContext(Dispatchers.IO) {
        try {
            val sessionsList = syncSessions.values.toList()
            val sessionsJson = json.encodeToString(sessionsList)
            dataStore.edit { preferences ->
                preferences[SESSIONS_KEY] = sessionsJson
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Persists the last active session ID for restoration
     */
    suspend fun saveLastActiveSession(sessionId: String) = withContext(Dispatchers.IO) {
        try {
            lastActiveSessionId = sessionId
            dataStore.edit { preferences ->
                preferences[LAST_ACTIVE_SESSION_KEY] = sessionId
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Returns the last active session ID, if any
     */
    fun getLastActiveSessionId(): String? = lastActiveSessionId

    /**
     * Clears the last active session ID
     */
    suspend fun clearLastActiveSession() = withContext(Dispatchers.IO) {
        try {
            lastActiveSessionId = null
            dataStore.edit { preferences ->
                preferences.remove(LAST_ACTIVE_SESSION_KEY)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Searches for YouTube videos or handles direct YouTube URLs.
     * If the query is a YouTube URL, extracts the video ID and fetches details directly.
     * Otherwise, performs a standard search query.
     */
    suspend fun searchYouTubeVideos(query: String): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        try {
            // Check if query is a YouTube URL
            if (YouTubeUrlParser.isYouTubeUrl(query)) {
                val videoId = YouTubeUrlParser.extractVideoId(query)
                if (videoId != null) {
                    // Fetch video details directly using the extracted video ID
                    return@withContext fetchVideoDetails(listOf(videoId))
                } else {
                    // Invalid YouTube URL format
                    return@withContext emptyList()
                }
            }

            // Standard search query
            try {
                val response = youtubeApi.searchVideos(
                    query = query,
                    apiKey = BuildConfig.YOUTUBE_API_KEY,
                )

                val videoIds = response.items.mapNotNull { it.id.videoId }
                if (videoIds.isEmpty()) return@withContext emptyList()

                return@withContext fetchVideoDetails(videoIds)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Fetches video details from the YouTube API given a list of video IDs.
     */
    private suspend fun fetchVideoDetails(videoIds: List<String>): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        try {
            val detailsResponse = youtubeApi.getVideoDetails(
                videoIds = videoIds.joinToString(","),
                apiKey = BuildConfig.YOUTUBE_API_KEY,
            )

            val durationMap = detailsResponse.items.associate { detail ->
                detail.id to parseDuration(detail.contentDetails.duration)
            }

            detailsResponse.items.mapNotNull { detail ->
                val snippet = detail.snippet
                YouTubeVideo(
                    videoId = detail.id,
                    title = snippet?.title ?: "Video ${detail.id}",
                    channelTitle = snippet?.channelTitle ?: "Unknown",
                    thumbnailUrl = snippet?.thumbnails?.high?.url
                        ?: snippet?.thumbnails?.medium?.url
                        ?: snippet?.thumbnails?.default?.url
                        ?: "https://img.youtube.com/vi/${detail.id}/hqdefault.jpg",
                    duration = durationMap[detail.id] ?: 0L,
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun createSyncSession(
        name: String,
        videos: List<YouTubeVideo>,
    ): SyncSession = withContext(Dispatchers.Default) {
        val session = SyncSession(
            id = UUID.randomUUID().toString(),
            name = name,
            videoIds = videos.map { it.videoId },
        )
        syncSessions[session.id] = session
        session
    }

    suspend fun getSyncSession(sessionId: String): SyncSession? {
        return syncSessions[sessionId]
    }

    suspend fun getAllSyncSessions(): List<SyncSession> {
        return syncSessions.values.toList()
    }

    suspend fun addVideosToSession(sessionId: String, videos: List<YouTubeVideo>): SyncSession? = withContext(Dispatchers.Default) {
        val session = syncSessions[sessionId] ?: return@withContext null
        val newVideoIds = videos.map { it.videoId }
        val updatedSession = session.copy(
            videoIds = session.videoIds + newVideoIds,
            updatedAt = System.currentTimeMillis(),
        )
        syncSessions[sessionId] = updatedSession
        updatedSession
    }

    suspend fun addSyncCue(sessionId: String, cue: SyncCue): Boolean = withContext(Dispatchers.Default) {
        val session = syncSessions[sessionId] ?: return@withContext false
        val updatedSession = session.copy(
            syncCues = session.syncCues + cue,
            updatedAt = System.currentTimeMillis(),
        )
        syncSessions[sessionId] = updatedSession
        true
    }

    suspend fun removeSyncCue(sessionId: String, cueIndex: Int): Boolean = withContext(Dispatchers.Default) {
        val session = syncSessions[sessionId] ?: return@withContext false
        val updatedSession = session.copy(
            syncCues = session.syncCues.filterIndexed { index, _ -> index != cueIndex },
            updatedAt = System.currentTimeMillis(),
        )
        syncSessions[sessionId] = updatedSession
        true
    }

    suspend fun deleteSyncSession(sessionId: String): Boolean = withContext(Dispatchers.Default) {
        syncSessions.remove(sessionId) != null
    }

    // Generate shareable link from sync session
    suspend fun generateShareLink(sessionId: String): String {
        val session = syncSessions[sessionId] ?: return ""
        val videoIds = session.videoIds.joinToString(",")
        val cuesEncoded = session.syncCues.map { "${it.videoIndex}:${it.cueTime}" }
            .joinToString("|")
            .takeIf { it.isNotEmpty() } ?: ""

        return buildString {
            append("https://viewsync.youkhainda.com/?videos=$videoIds")
            if (cuesEncoded.isNotEmpty()) {
                append("&cues=$cuesEncoded")
            }
            append("&name=${session.name}")
        }
    }

    // Calculate offset between videos based on sync cues
    suspend fun calculateVideoOffsets(sessionId: String, baseVideoIndex: Int = 0): Map<Int, Long> = withContext(Dispatchers.Default) {
        val session = syncSessions[sessionId] ?: return@withContext emptyMap()

        val cueDictionary = mutableMapOf<Int, Long>()

        session.syncCues.forEach { cue ->
            val currentCueTime = cueDictionary[cue.videoIndex] ?: 0L
            cueDictionary[cue.videoIndex] = cue.cueTime
        }

        val baseCueTime = cueDictionary[baseVideoIndex] ?: 0L
        val offsets = mutableMapOf<Int, Long>()

        for (i in session.videoIds.indices) {
            val videoCueTime = cueDictionary[i] ?: 0L
            offsets[i] = videoCueTime - baseCueTime
        }

        offsets
    }
}
