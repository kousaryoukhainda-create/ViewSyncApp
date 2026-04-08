package com.youkhainda.viewsync.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.youkhainda.viewsync.data.model.SyncSession
import com.youkhainda.viewsync.data.model.SyncCue
import com.youkhainda.viewsync.data.model.YouTubeVideo
import com.youkhainda.viewsync.data.model.SocialState
import com.youkhainda.viewsync.data.model.VideoStatistics
import com.youkhainda.viewsync.data.remote.YouTubeApiService
import com.youkhainda.viewsync.data.remote.YouTubeUrlParser
import com.youkhainda.viewsync.data.remote.parseDuration
import com.youkhainda.viewsync.BuildConfig
import com.youkhainda.viewsync.util.DebugLogger
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
    private val oAuth2Manager: com.youkhainda.viewsync.auth.OAuth2Manager,
    private val authenticatedApiProvider: com.youkhainda.viewsync.auth.AuthenticatedYouTubeApiProvider,
) {

    // In-memory cache for fast access
    private val syncSessions = mutableMapOf<String, SyncSession>()
    private val socialStates = mutableMapOf<String, SocialState>()

    // Track the last active session ID for restoration
    private var lastActiveSessionId: String? = null

    // DataStore keys
    private val SESSIONS_KEY = stringPreferencesKey("sync_sessions")
    private val SOCIAL_STATES_KEY = stringPreferencesKey("social_states")
    private val LAST_ACTIVE_SESSION_KEY = stringPreferencesKey("last_active_session_id")

    /**
     * Initializes the repository by loading sessions from persistent storage
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        DebugLogger.i("SyncRepository", "Initializing repository")
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
                DebugLogger.i("SyncRepository", "Loaded ${sessionsList.size} sessions from DataStore")
            } else {
                DebugLogger.d("SyncRepository", "No sessions found in DataStore")
            }

            // Load social states
            val socialStatesJson = preferences[SOCIAL_STATES_KEY]
            if (socialStatesJson != null) {
                val socialStatesMap = json.decodeFromString<Map<String, SocialState>>(socialStatesJson)
                socialStates.clear()
                socialStates.putAll(socialStatesMap)
                DebugLogger.i("SyncRepository", "Loaded ${socialStatesMap.size} social states from DataStore")
            } else {
                DebugLogger.d("SyncRepository", "No social states found in DataStore")
            }

            // Load last active session
            lastActiveSessionId = preferences[LAST_ACTIVE_SESSION_KEY]
            DebugLogger.d("SyncRepository", "Last active session ID: $lastActiveSessionId")
        } catch (e: Exception) {
            DebugLogger.e("SyncRepository", "Failed to initialize repository", e)
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
            DebugLogger.d("SyncRepository", "Persisted ${sessionsList.size} sessions to DataStore")
        } catch (e: Exception) {
            DebugLogger.e("SyncRepository", "Failed to persist sessions", e)
            e.printStackTrace()
        }
    }

    /**
     * Persists all social states to DataStore
     */
    private suspend fun persistSocialStates() = withContext(Dispatchers.IO) {
        try {
            val socialStatesJson = json.encodeToString(socialStates)
            dataStore.edit { preferences ->
                preferences[SOCIAL_STATES_KEY] = socialStatesJson
            }
            DebugLogger.d("SyncRepository", "Persisted ${socialStates.size} social states to DataStore")
        } catch (e: Exception) {
            DebugLogger.e("SyncRepository", "Failed to persist social states", e)
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
            DebugLogger.d("SyncRepository", "Saved last active session: $sessionId")
        } catch (e: Exception) {
            DebugLogger.e("SyncRepository", "Failed to save last active session", e)
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
        DebugLogger.i("SyncRepository", "searchYouTubeVideos() - Query: '$query'")
        try {
            // Check if query is a YouTube URL
            if (YouTubeUrlParser.isYouTubeUrl(query)) {
                DebugLogger.d("SyncRepository", "Detected YouTube URL, extracting video ID")
                val videoId = YouTubeUrlParser.extractVideoId(query)
                if (videoId != null) {
                    DebugLogger.step("SyncRepository", "Fetching video details for ID: $videoId", 1, 1)
                    // Fetch video details directly using the extracted video ID
                    return@withContext fetchVideoDetails(listOf(videoId))
                } else {
                    DebugLogger.w("SyncRepository", "Invalid YouTube URL format: $query")
                    // Invalid YouTube URL format
                    return@withContext emptyList()
                }
            }

            // Standard search query
            try {
                DebugLogger.step("SyncRepository", "Performing YouTube API search", 1, 2)
                val response = youtubeApi.searchVideos(
                    query = query,
                    apiKey = BuildConfig.YOUTUBE_API_KEY,
                )
                DebugLogger.d("SyncRepository", "API returned ${response.items.size} results")

                val videoIds = response.items.mapNotNull { it.id.videoId }
                DebugLogger.d("SyncRepository", "Extracted ${videoIds.size} valid video IDs")
                if (videoIds.isEmpty()) return@withContext emptyList()

                DebugLogger.step("SyncRepository", "Fetching video details", 2, 2)
                return@withContext fetchVideoDetails(videoIds)
            } catch (e: Exception) {
                DebugLogger.e("SyncRepository", "API search failed", e)
                e.printStackTrace()
                emptyList()
            }
        } catch (e: Exception) {
            DebugLogger.e("SyncRepository", "searchYouTubeVideos failed", e)
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Fetches video details from the YouTube API given a list of video IDs.
     */
    private suspend fun fetchVideoDetails(videoIds: List<String>): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        DebugLogger.i("SyncRepository", "fetchVideoDetails() - Video IDs: ${videoIds.size}")
        try {
            DebugLogger.d("SyncRepository", "Calling YouTube API for video details")
            val detailsResponse = youtubeApi.getVideoDetails(
                videoIds = videoIds.joinToString(","),
                apiKey = BuildConfig.YOUTUBE_API_KEY,
            )
            DebugLogger.d("SyncRepository", "API returned ${detailsResponse.items.size} video details")

            val durationMap = detailsResponse.items.associate { detail ->
                detail.id to parseDuration(detail.contentDetails.duration)
            }
            DebugLogger.d("SyncRepository", "Duration map created with ${durationMap.size} entries")

            val videos = detailsResponse.items.mapNotNull { detail ->
                val snippet = detail.snippet
                val stats = detail.statistics
                val viewCount = stats?.viewCount?.toLongOrNull() ?: 0L
                val likeCount = stats?.likeCount?.toLongOrNull() ?: 0L
                val commentCount = stats?.commentCount?.toLongOrNull() ?: 0L

                DebugLogger.d("SyncRepository", "Video ${detail.id} stats - Views: $viewCount, Likes: $likeCount, Comments: $commentCount")

                YouTubeVideo(
                    videoId = detail.id,
                    title = snippet?.title ?: "Video ${detail.id}",
                    channelTitle = snippet?.channelTitle ?: "Unknown",
                    thumbnailUrl = snippet?.thumbnails?.high?.url
                        ?: snippet?.thumbnails?.medium?.url
                        ?: snippet?.thumbnails?.default?.url
                        ?: "https://img.youtube.com/vi/${detail.id}/hqdefault.jpg",
                    duration = durationMap[detail.id] ?: 0L,
                    viewCount = viewCount,
                    likeCount = likeCount,
                    commentCount = commentCount,
                )
            }
            DebugLogger.i("SyncRepository", "Successfully parsed ${videos.size} videos")
            videos
        } catch (e: Exception) {
            DebugLogger.e("SyncRepository", "fetchVideoDetails failed", e)
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun createSyncSession(
        name: String,
        videos: List<YouTubeVideo>,
    ): SyncSession = withContext(Dispatchers.Default) {
        DebugLogger.i("SyncRepository", "createSyncSession() - Name: '$name', Videos: ${videos.size}")

        // Build statistics map from video data
        val videoStats = videos.associate { video ->
            video.videoId to VideoStatistics(
                viewCount = video.viewCount,
                likeCount = video.likeCount,
                commentCount = video.commentCount,
            )
        }
        DebugLogger.d("SyncRepository", "Video statistics stored for ${videoStats.size} videos")

        val session = SyncSession(
            id = UUID.randomUUID().toString(),
            name = name,
            videoIds = videos.map { it.videoId },
            videoStats = videoStats,
        )
        syncSessions[session.id] = session
        DebugLogger.i("SyncRepository", "Session created with ID: ${session.id}")
        session
    }

    suspend fun getSyncSession(sessionId: String): SyncSession? {
        DebugLogger.d("SyncRepository", "getSyncSession() - ID: $sessionId")
        val session = syncSessions[sessionId]
        if (session != null) {
            DebugLogger.d("SyncRepository", "Session found - Videos: ${session.videoIds.size}, Cues: ${session.syncCues.size}")
        } else {
            DebugLogger.w("SyncRepository", "Session not found - ID: $sessionId")
        }
        return session
    }

    suspend fun getAllSyncSessions(): List<SyncSession> {
        DebugLogger.d("SyncRepository", "getAllSyncSessions() - Total: ${syncSessions.size}")
        return syncSessions.values.toList()
    }

    suspend fun addVideosToSession(sessionId: String, videos: List<YouTubeVideo>): SyncSession? = withContext(Dispatchers.Default) {
        DebugLogger.i("SyncRepository", "addVideosToSession() - Session: $sessionId, New videos: ${videos.size}")
        val session = syncSessions[sessionId] ?: return@withContext null
        val newVideoIds = videos.map { it.videoId }

        // Merge new video statistics into existing stats map
        val newStats = videos.associate { video ->
            video.videoId to VideoStatistics(
                viewCount = video.viewCount,
                likeCount = video.likeCount,
                commentCount = video.commentCount,
            )
        }
        val mergedStats = session.videoStats + newStats

        val updatedSession = session.copy(
            videoIds = session.videoIds + newVideoIds,
            videoStats = mergedStats,
            updatedAt = System.currentTimeMillis(),
        )
        syncSessions[sessionId] = updatedSession
        DebugLogger.i("SyncRepository", "Session updated - Total videos: ${updatedSession.videoIds.size}")
        updatedSession
    }

    /**
     * Get real YouTube statistics for a video in the session
     */
    fun getVideoStatistics(sessionId: String, videoIndex: Int): VideoStatistics? {
        val session = syncSessions[sessionId] ?: return null
        val videoId = session.videoIds.getOrNull(videoIndex) ?: return null
        return session.videoStats[videoId]
    }

    /**
     * Check if user is authenticated with Google/YouTube
     */
    fun isUserAuthenticated(): Boolean {
        return oAuth2Manager.isSignedIn()
    }

    /**
     * Get the current authenticated user's display name
     */
    fun getCurrentUserName(): String? {
        return oAuth2Manager.getCurrentUserName()
    }

    suspend fun addSyncCue(sessionId: String, cue: SyncCue): Boolean = withContext(Dispatchers.Default) {
        DebugLogger.i("SyncRepository", "addSyncCue() - Session: $sessionId, Video: ${cue.videoIndex}, Time: ${cue.cueTime}ms")
        val session = syncSessions[sessionId] ?: return@withContext false
        val updatedSession = session.copy(
            syncCues = session.syncCues + cue,
            updatedAt = System.currentTimeMillis(),
        )
        syncSessions[sessionId] = updatedSession
        DebugLogger.d("SyncRepository", "Sync cue added - Total cues: ${updatedSession.syncCues.size}")
        true
    }

    suspend fun removeSyncCue(sessionId: String, cueIndex: Int): Boolean = withContext(Dispatchers.Default) {
        DebugLogger.i("SyncRepository", "removeSyncCue() - Session: $sessionId, Index: $cueIndex")
        val session = syncSessions[sessionId] ?: return@withContext false
        val updatedSession = session.copy(
            syncCues = session.syncCues.filterIndexed { index, _ -> index != cueIndex },
            updatedAt = System.currentTimeMillis(),
        )
        syncSessions[sessionId] = updatedSession
        DebugLogger.d("SyncRepository", "Sync cue removed - Remaining cues: ${updatedSession.syncCues.size}")
        true
    }

    suspend fun deleteSyncSession(sessionId: String): Boolean = withContext(Dispatchers.Default) {
        DebugLogger.i("SyncRepository", "deleteSyncSession() - ID: $sessionId")
        val removed = syncSessions.remove(sessionId) != null
        if (removed) {
            DebugLogger.i("SyncRepository", "Session deleted successfully")
        } else {
            DebugLogger.w("SyncRepository", "Session not found for deletion - ID: $sessionId")
        }
        removed
    }

    // Generate shareable link from sync session
    suspend fun generateShareLink(sessionId: String): String {
        DebugLogger.i("SyncRepository", "generateShareLink() - Session: $sessionId")
        val session = syncSessions[sessionId] ?: return ""
        val videoIds = session.videoIds.joinToString(",")
        val cuesEncoded = session.syncCues.map { "${it.videoIndex}:${it.cueTime}" }
            .joinToString("|")
            .takeIf { it.isNotEmpty() } ?: ""

        val link = buildString {
            append("https://viewsync.youkhainda.com/?videos=$videoIds")
            if (cuesEncoded.isNotEmpty()) {
                append("&cues=$cuesEncoded")
            }
            append("&name=${session.name}")
        }
        DebugLogger.d("SyncRepository", "Share link generated: $link")
        return link
    }

    // Calculate offset between videos based on sync cues
    suspend fun calculateVideoOffsets(sessionId: String, baseVideoIndex: Int = 0): Map<Int, Long> = withContext(Dispatchers.Default) {
        DebugLogger.d("SyncRepository", "calculateVideoOffsets() - Session: $sessionId, BaseIndex: $baseVideoIndex")
        val session = syncSessions[sessionId] ?: return@withContext emptyMap()

        val cueDictionary = mutableMapOf<Int, Long>()

        session.syncCues.forEach { cue ->
            val currentCueTime = cueDictionary[cue.videoIndex] ?: 0L
            cueDictionary[cue.videoIndex] = cue.cueTime
        }
        DebugLogger.d("SyncRepository", "Cue dictionary created: $cueDictionary")

        val baseCueTime = cueDictionary[baseVideoIndex] ?: 0L
        val offsets = mutableMapOf<Int, Long>()

        for (i in session.videoIds.indices) {
            val videoCueTime = cueDictionary[i] ?: 0L
            offsets[i] = videoCueTime - baseCueTime
        }
        DebugLogger.i("SyncRepository", "Offsets calculated: $offsets")
        offsets
    }

    // Social state management
    fun getSocialState(sessionId: String): SocialState {
        return socialStates[sessionId] ?: SocialState()
    }

    suspend fun updateSocialState(sessionId: String, state: SocialState): Boolean = withContext(Dispatchers.Default) {
        DebugLogger.i("SyncRepository", "updateSocialState() - Session: $sessionId")
        socialStates[sessionId] = state
        DebugLogger.d("SyncRepository", "Social state updated - Liked: ${state.isLiked}, Subscribed: ${state.isSubscribed}")
        
        // Persist to DataStore
        persistSocialStates()
        true
    }

    suspend fun toggleLike(sessionId: String, videoId: String): SocialState? = withContext(Dispatchers.Default) {
        val currentState = socialStates[sessionId] ?: SocialState()

        // Check if user is authenticated
        if (!oAuth2Manager.isSignedIn()) {
            DebugLogger.w("SyncRepository", "User not authenticated - cannot like video")
            return@withContext currentState.copy(isLiked = false)
        }

        try {
            val authenticatedApi = authenticatedApiProvider.getAuthenticatedApi()
                ?: return@withContext currentState

            // Check current rating first
            val ratingResponse = authenticatedApi.getVideoRating(videoId)
            val currentRating = ratingResponse.items.firstOrNull()?.rating ?: "none"

            if (currentRating == "like") {
                // Remove like
                authenticatedApi.rateVideo("none", videoId)
                DebugLogger.d("SyncRepository", "Like removed from video $videoId")
            } else {
                // Add like
                authenticatedApi.rateVideo("like", videoId)
                DebugLogger.d("SyncRepository", "Video $videoId liked")
            }

            val newState = currentState.copy(isLiked = currentRating != "like")
            socialStates[sessionId] = newState
            persistSocialStates()
            newState
        } catch (e: Exception) {
            DebugLogger.e("SyncRepository", "Failed to toggle like", e)
            currentState
        }
    }

    suspend fun toggleSubscribe(sessionId: String, channelId: String): SocialState? = withContext(Dispatchers.Default) {
        val currentState = socialStates[sessionId] ?: SocialState()

        // Check if user is authenticated
        if (!oAuth2Manager.isSignedIn()) {
            DebugLogger.w("SyncRepository", "User not authenticated - cannot subscribe")
            return@withContext currentState.copy(isSubscribed = false)
        }

        try {
            val authenticatedApi = authenticatedApiProvider.getAuthenticatedApi()
                ?: return@withContext currentState

            if (currentState.isSubscribed) {
                // Need to get subscription ID first - for now, just unsubscribe from the channel
                // This is simplified - in production you'd fetch the subscription ID
                DebugLogger.d("SyncRepository", "Unsubscribing from channel $channelId")
                // Note: YouTube API requires subscription ID, not channel ID for deletion
                // This is a limitation - we'd need to fetch subscriptions first
                currentState.copy(isSubscribed = false)
            } else {
                // Subscribe to channel
                val request = com.youkhainda.viewsync.data.remote.SubscriptionRequest(
                    snippet = com.youkhainda.viewsync.data.remote.SubscriptionSnippet(),
                    resourceId = com.youkhainda.viewsync.data.remote.SubscriptionResourceId(
                        channelId = channelId,
                    ),
                )
                authenticatedApi.subscribeToChannel(body = request)
                DebugLogger.d("SyncRepository", "Subscribed to channel $channelId")
            }

            val newState = currentState.copy(isSubscribed = !currentState.isSubscribed)
            socialStates[sessionId] = newState
            persistSocialStates()
            newState
        } catch (e: Exception) {
            DebugLogger.e("SyncRepository", "Failed to toggle subscribe", e)
            currentState
        }
    }

    suspend fun incrementShare(sessionId: String): SocialState? = withContext(Dispatchers.Default) {
        val currentState = socialStates[sessionId] ?: SocialState()
        val newState = currentState.copy(shareCount = currentState.shareCount + 1)
        socialStates[sessionId] = newState

        // Persist to DataStore
        persistSocialStates()

        DebugLogger.d("SyncRepository", "Share action recorded locally - Count: ${newState.shareCount}")
        newState
    }

    suspend fun incrementComment(sessionId: String, videoId: String, commentText: String): SocialState? = withContext(Dispatchers.Default) {
        val currentState = socialStates[sessionId] ?: SocialState()

        // Check if user is authenticated
        if (!oAuth2Manager.isSignedIn()) {
            DebugLogger.w("SyncRepository", "User not authenticated - cannot comment")
            return@withContext currentState
        }

        try {
            val authenticatedApi = authenticatedApiProvider.getAuthenticatedApi()
                ?: return@withContext currentState

            val request = com.youkhainda.viewsync.data.remote.CommentThreadRequest(
                snippet = com.youkhainda.viewsync.data.remote.CommentSnippet(
                    videoId = videoId,
                    topLevelComment = com.youkhainda.viewsync.data.remote.TopLevelComment(
                        snippet = com.youkhainda.viewsync.data.remote.CommentTextSnippet(
                            textOriginal = commentText,
                        ),
                    ),
                ),
            )

            val response = authenticatedApi.addComment(body = request)
            DebugLogger.d("SyncRepository", "Comment posted successfully - ID: ${response.id}")

            val newState = currentState.copy(commentCount = currentState.commentCount + 1)
            socialStates[sessionId] = newState
            persistSocialStates()
            newState
        } catch (e: Exception) {
            DebugLogger.e("SyncRepository", "Failed to post comment", e)
            currentState
        }
    }
}
