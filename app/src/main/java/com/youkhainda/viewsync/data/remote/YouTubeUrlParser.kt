package com.youkhainda.viewsync.data.remote

/**
 * Parses YouTube URLs and extracts video IDs.
 * Supports various YouTube URL formats.
 */
object YouTubeUrlParser {

    /**
     * Checks if the given string is a YouTube URL.
     */
    fun isYouTubeUrl(input: String): Boolean {
        val trimmed = input.trim()
        val lower = trimmed.lowercase()
        return lower.contains("youtu.be") ||
               lower.contains("youtube.com") ||
               lower.contains("youtube.be")
    }

    /**
     * Extracts the video ID from a YouTube URL.
     * Returns null if the input is not a valid YouTube URL or extraction fails.
     *
     * Supported formats:
     * - https://youtu.be/VIDEO_ID
     * - https://youtu.be/VIDEO_ID?si=...
     * - https://www.youtube.com/watch?v=VIDEO_ID
     * - https://www.youtube.com/watch?v=VIDEO_ID&list=...
     * - https://www.youtube.com/embed/VIDEO_ID
     * - https://www.youtube.com/v/VIDEO_ID
     * - https://www.youtube.com/shorts/VIDEO_ID
     * - https://m.youtube.com/watch?v=VIDEO_ID
     * - http:// variants
     */
    fun extractVideoId(url: String): String? {
        val trimmed = url.trim()

        return try {
            when {
                // Short URL: youtu.be/VIDEO_ID
                trimmed.contains("youtu.be/") -> {
                    extractFromShortUrl(trimmed)
                }

                // Standard URL: youtube.com/watch?v=VIDEO_ID
                trimmed.contains("/watch") -> {
                    extractFromWatchUrl(trimmed)
                }

                // Embed URL: youtube.com/embed/VIDEO_ID
                trimmed.contains("/embed/") -> {
                    extractFromPath(trimmed, "/embed/")
                }

                // V URL: youtube.com/v/VIDEO_ID
                trimmed.contains("/v/") -> {
                    extractFromPath(trimmed, "/v/")
                }

                // Shorts URL: youtube.com/shorts/VIDEO_ID
                trimmed.contains("/shorts/") -> {
                    extractFromPath(trimmed, "/shorts/")
                }

                // Live URL: youtube.com/live/VIDEO_ID
                trimmed.contains("/live/") -> {
                    extractFromPath(trimmed, "/live/")
                }

                else -> null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractFromShortUrl(url: String): String? {
        // Format: https://youtu.be/VIDEO_ID or https://youtu.be/VIDEO_ID?param=value
        val regex = Regex("youtu\\.be/([a-zA-Z0-9_-]{11})")
        return regex.find(url)?.groupValues?.get(1)
    }

    private fun extractFromWatchUrl(url: String): String? {
        // Format: https://www.youtube.com/watch?v=VIDEO_ID&other=params
        val regex = Regex("[?&]v=([a-zA-Z0-9_-]{11})")
        return regex.find(url)?.groupValues?.get(1)
    }

    private fun extractFromPath(url: String, pathSegment: String): String? {
        // Format: https://www.youtube.com/segment/VIDEO_ID or .../segment/VIDEO_ID?params
        val index = url.indexOf(pathSegment)
        if (index == -1) return null

        val startIndex = index + pathSegment.length
        if (startIndex >= url.length) return null

        val remaining = url.substring(startIndex)
        // Video ID is 11 characters, stop at query params or end of string
        val videoId = remaining.takeWhile { it != '?' && it != '&' && it != '/' && it != '#' }
        
        return if (videoId.length == 11 && videoId.matches(Regex("[a-zA-Z0-9_-]{11}"))) {
            videoId
        } else {
            null
        }
    }

    /**
     * Validates if a string is a valid YouTube video ID.
     * Video IDs are exactly 11 characters containing [a-zA-Z0-9_-]
     */
    fun isValidVideoId(id: String): Boolean {
        return id.matches(Regex("^[a-zA-Z0-9_-]{11}$"))
    }
}
