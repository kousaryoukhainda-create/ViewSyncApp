package com.youkhainda.viewsync.data.model

import kotlinx.serialization.Serializable

@Serializable
data class YouTubeVideo(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val duration: Long = 0L, // in seconds
    val viewCount: Long = 0L,
    val likeCount: Long = 0L,
    val commentCount: Long = 0L,
)

@Serializable
data class SyncSession(
    val id: String = "", // UUID
    val name: String,
    val videoIds: List<String> = emptyList(),
    val syncCues: List<SyncCue> = emptyList(),
    val videoStats: Map<String, VideoStatistics> = emptyMap(), // videoId -> statistics
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class VideoStatistics(
    val viewCount: Long = 0L,
    val likeCount: Long = 0L,
    val commentCount: Long = 0L,
)

@Serializable
data class SyncCue(
    val videoIndex: Int,
    val cueTime: Long, // milliseconds into video
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis(),
)

@Serializable
data class SyncState(
    val currentPlayPosition: Long = 0L,
    val videoDuration: Long = 0L,
    val isPlaying: Boolean = false,
    val videoOffsets: Map<Int, Long> = emptyMap(), // video index -> offset in ms
    // User social actions (local only, not synced with YouTube)
    val isLiked: Boolean = false,
    val isSubscribed: Boolean = false,
    // Real YouTube statistics (from YouTube API)
    val videoLikeCount: Long = 0L,
    val videoShareCount: Long = 0L,
    val videoCommentCount: Long = 0L,
    val videoViewCount: Long = 0L,
)

@Serializable
data class SocialState(
    val isLiked: Boolean = false,
    val isSubscribed: Boolean = false,
    val likeCount: Int = 0,
    val shareCount: Int = 0,
    val commentCount: Int = 0,
)

// API Response Models
@Serializable
data class YouTubeSearchResponse(
    val items: List<YouTubeSearchItem> = emptyList(),
)

@Serializable
data class YouTubeSearchItem(
    val id: YouTubeItemId,
    val snippet: YouTubeSnippet,
)

@Serializable
data class YouTubeItemId(
    val videoId: String = "",
)

@Serializable
data class YouTubeSnippet(
    val title: String,
    val channelTitle: String,
    val thumbnails: YouTubeThumbnails,
)

@Serializable
data class YouTubeThumbnails(
    val default: YouTubeThumbnail? = null,
    val medium: YouTubeThumbnail? = null,
    val high: YouTubeThumbnail? = null,
)

@Serializable
data class YouTubeThumbnail(
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
data class YouTubeVideoDetailsResponse(
    val items: List<YouTubeVideoDetails> = emptyList(),
)

@Serializable
data class YouTubeVideoDetails(
    val id: String,
    val snippet: YouTubeSnippet? = null,
    val contentDetails: YouTubeContentDetails,
    val statistics: YouTubeStatistics? = null,
)

@Serializable
data class YouTubeStatistics(
    val viewCount: String = "0",
    val likeCount: String = "0",
    val commentCount: String = "0",
)

@Serializable
data class YouTubeContentDetails(
    val duration: String, // ISO 8601 format, e.g., "PT10M30S"
)
