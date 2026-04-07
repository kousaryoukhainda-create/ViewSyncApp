package com.youkhainda.viewsync.util

import android.content.Context
import android.util.Log

/**
 * Utility functions for clearing app cache to help with playback glitches.
 */
object CacheClearingUtil {
    
    private const val TAG = "CacheClearingUtil"
    
    /**
     * Clears the application cache.
     * This can help resolve playback glitches caused by corrupted cache data.
     */
    fun clearAppCache(context: Context) {
        try {
            context.cacheDir.deleteRecursively()
            Log.i(TAG, "App cache cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear app cache: ${e.message}", e)
        }
    }
    
    /**
     * Clears WebView cache (both disk and memory).
     * This can help resolve YouTube embedding issues caused by stale cache.
     */
    fun clearWebViewCache(context: Context) {
        try {
            // Clear application cache
            context.cacheDir.deleteRecursively()
            
            // Clear shared preferences cache (if any)
            context.getSharedPreferences("webview_cache", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
            
            Log.i(TAG, "WebView cache cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear WebView cache: ${e.message}", e)
        }
    }
}
