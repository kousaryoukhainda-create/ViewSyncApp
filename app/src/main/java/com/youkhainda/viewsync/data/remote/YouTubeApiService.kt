package com.youkhainda.viewsync.data.remote

import com.youkhainda.viewsync.data.model.YouTubeSearchResponse
import com.youkhainda.viewsync.data.model.YouTubeVideoDetailsResponse
import retrofit2.http.GET
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
        @Query("part") part: String = "snippet,contentDetails",
        @Query("id") videoIds: String,
        @Query("key") apiKey: String,
    ): YouTubeVideoDetailsResponse
}

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
