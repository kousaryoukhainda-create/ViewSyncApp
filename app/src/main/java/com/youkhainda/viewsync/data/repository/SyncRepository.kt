package com.youkhainda.viewsync.data.repository

import com.youkhainda.viewsync.data.model.SyncSession
import com.youkhainda.viewsync.data.model.SyncCue
import com.youkhainda.viewsync.data.model.YouTubeVideo
import com.youkhainda.viewsync.data.remote.YouTubeApiService
import com.youkhainda.viewsync.data.remote.parseDuration
import com.youkhainda.viewsync.BuildConfig
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

@ViewModelScoped
class SyncRepository @Inject constructor(
    private val youtubeApi: YouTubeApiService,
) {

    // In-memory storage (can be replaced with Room DB for persistence)
    private val syncSessions = mutableMapOf<String, SyncSession>()

    suspend fun searchYouTubeVideos(query: String): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        try {
            val response = youtubeApi.searchVideos(
                query = query,
                apiKey = BuildConfig.YOUTUBE_API_KEY,
            )

            val videoIds = response.items.mapNotNull { it.id.videoId }
            if (videoIds.isEmpty()) return@withContext emptyList()

            // Fetch video details including duration
            val detailsResponse = youtubeApi.getVideoDetails(
                videoIds = videoIds.joinToString(","),
                apiKey = BuildConfig.YOUTUBE_API_KEY,
            )

            val durationMap = detailsResponse.items.associate { detail ->
                detail.id to parseDuration(detail.contentDetails.duration)
            }

            response.items.mapNotNull { item ->
                YouTubeVideo(
                    videoId = item.id.videoId,
                    title = item.snippet.title,
                    channelTitle = item.snippet.channelTitle,
                    thumbnailUrl = item.snippet.thumbnails.high?.url
                        ?: item.snippet.thumbnails.medium?.url
                        ?: item.snippet.thumbnails.default?.url
                        ?: "",
                    duration = durationMap[item.id.videoId] ?: 0L,
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
