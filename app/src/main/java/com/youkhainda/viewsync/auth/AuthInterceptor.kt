package com.youkhainda.viewsync.auth

import com.youkhainda.viewsync.util.DebugLogger
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp Interceptor that adds OAuth 2.0 Authorization header to requests.
 * Used for YouTube API write operations that require authentication.
 */
class AuthInterceptor(
    private val tokenProvider: suspend () -> String?
) : Interceptor {

    companion object {
        private const val TAG = "AuthInterceptor"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Only add auth header to requests that need it
        val needsAuth = originalRequest.url.encodedPath.let { path ->
            path.contains("rate") ||
            path.contains("subscription") ||
            path.contains("comment") ||
            path.contains("getRating")
        }

        if (!needsAuth) {
            DebugLogger.d(TAG, "Request to ${originalRequest.url.encodedPath} - no auth needed")
            return chain.proceed(originalRequest)
        }

        // Get the access token
        val token = runCatching {
            kotlinx.coroutines.runBlocking { tokenProvider() }
        }.getOrNull()

        if (token == null) {
            DebugLogger.w(TAG, "No access token available for authenticated request")
            return chain.proceed(originalRequest)
        }

        DebugLogger.d(TAG, "Adding Authorization header for ${originalRequest.url.encodedPath}")

        val authenticatedRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        return chain.proceed(authenticatedRequest)
    }
}
