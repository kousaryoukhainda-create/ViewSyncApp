package com.youkhainda.viewsync.data.remote

import com.youkhainda.viewsync.data.model.YouTubeSearchResponse
import com.youkhainda.viewsync.data.model.YouTubeVideoDetailsResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.regex.Pattern

interface YouTubeApiService {

    @GET("search")
    suspend fun searchVideos(
        @Query("part") part: String = "snippet",
        @Query("q") query: String,
        @Query("maxResults") maxResults: Int = 25,
        @Query("type") type: String = "video",
        @Query("key") apiKey: String,
    ): YouTubeSearchResponse

    @GET("videos")
    suspend fun getVideoDetails(
        @Query("part") part: String = "snippet,contentDetails,statistics",
        @Query("id") videoIds: String,
        @Query("key") apiKey: String,
    ): YouTubeVideoDetailsResponse

    // ===== OAuth 2.0 Write Endpoints =====

    /**
     * Rate a video (like/dislike). Requires OAuth 2.0 with youtube.force-ssl scope.
     * @param rating One of: "like", "dislike", "none" (remove rating)
     * @param id The video ID to rate
     */
    @POST("videos/rate")
    suspend fun rateVideo(
        @Query("rating") rating: String,
        @Query("id") id: String,
    ): Unit

    /**
     * Subscribe to a channel. Requires OAuth 2.0 with youtube.force-ssl scope.
     * @param body The subscription request body
     */
    @POST("subscriptions")
    suspend fun subscribeToChannel(
        @Query("part") part: String = "snippet",
        @Body body: SubscriptionRequest,
    ): SubscriptionResponse

    /**
     * Unsubscribe from a channel. Requires OAuth 2.0 with youtube.force-ssl scope.
     * @param id The subscription ID to delete
     */
    @DELETE("subscriptions")
    suspend fun unsubscribeFromChannel(
        @Query("id") id: String,
    ): Unit

    /**
     * Add a comment to a video. Requires OAuth 2.0 with youtube.force-ssl scope.
     * @param body The comment thread request body
     */
    @POST("commentThreads")
    suspend fun addComment(
        @Query("part") part: String = "snippet",
        @Body body: CommentThreadRequest,
    ): CommentThreadResponse

    /**
     * Check if user has liked a video. Requires OAuth 2.0.
     * Returns the user's rating for the video.
     */
    @GET("videos/getRating")
    suspend fun getVideoRating(
        @Query("id") videoId: String,
    ): VideoRatingResponse

    /**
     * Get user's subscriptions. Requires OAuth 2.0.
     */
    @GET("subscriptions")
    suspend fun getSubscriptions(
        @Query("part") part: String = "snippet",
        @Query("channelId") channelId: String,
        @Query("maxResults") maxResults: Int = 50,
    ): SubscriptionListResponse
}

// ===== Request/Response Models =====

data class SubscriptionRequest(
    val snippet: SubscriptionSnippet,
    val resourceId: SubscriptionResourceId,
)

data class SubscriptionSnippet(
    val channelId: String = "",
)

data class SubscriptionResourceId(
    val kind: String = "youtube#channel",
    val channelId: String,
)

data class SubscriptionResponse(
    val id: String,
    val snippet: SubscriptionSnippetResponse,
)

data class SubscriptionSnippetResponse(
    val title: String = "",
    val channelId: String = "",
)

data class CommentThreadRequest(
    val snippet: CommentSnippet,
)

data class CommentSnippet(
    val videoId: String,
    val topLevelComment: TopLevelComment,
)

data class TopLevelComment(
    val snippet: CommentTextSnippet,
)

data class CommentTextSnippet(
    val textOriginal: String,
)

data class CommentThreadResponse(
    val id: String,
    val snippet: CommentThreadSnippet,
)

data class CommentThreadSnippet(
    val topLevelComment: TopLevelCommentResponse,
)

data class TopLevelCommentResponse(
    val id: String,
    val snippet: CommentTextSnippet,
)

data class VideoRatingResponse(
    val items: List<VideoRatingItem> = emptyList(),
)

data class VideoRatingItem(
    val id: String = "",
    val rating: String = "none", // "like", "dislike", "none"
)

data class SubscriptionListResponse(
    val items: List<SubscriptionResponse> = emptyList(),
)

/**
 * Parses ISO 8601 duration to milliseconds
 * Example: PT10M30S -> 630000ms
 */
fun parseDuration(isoDuration: String): Long {
    val pattern = Pattern.compile("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?")
    val matcher = pattern.matcher(isoDuration)

    if (matcher.matches()) {
        val hours = matcher.group(1)?.toLongOrNull() ?: 0L
        val minutes = matcher.group(2)?.toLongOrNull() ?: 0L
        val seconds = matcher.group(3)?.toLongOrNull() ?: 0L

        return (hours * 3600 + minutes * 60 + seconds) * 1000
    }

    return 0L
}
