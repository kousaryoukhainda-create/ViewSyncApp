package com.youkhainda.viewsync.auth

import com.youkhainda.viewsync.data.remote.YouTubeApiService
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory that creates an authenticated YouTubeApiService instance.
 * The authenticated service uses an OAuth token interceptor for write operations.
 */
@Singleton
class AuthenticatedYouTubeApiProvider @Inject constructor(
    private val oAuth2Manager: OAuth2Manager,
    private val json: Json,
) {

    private var _authenticatedApi: YouTubeApiService? = null

    /**
     * Get the authenticated YouTube API service.
     * Creates a new instance with the current access token.
     */
    suspend fun getAuthenticatedApi(): YouTubeApiService? {
        val token = oAuth2Manager.getAccessToken()
            ?: throw IllegalStateException("User not authenticated - no access token available")

        val authInterceptor = AuthInterceptor { token }

        val authenticatedClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()

        val contentType = "application/json".toMediaType()

        _authenticatedApi = Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/youtube/v3/")
            .client(authenticatedClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(YouTubeApiService::class.java)

        return _authenticatedApi
    }

    /**
     * Check if we can create an authenticated API instance
     */
    fun isAuthenticated(): Boolean {
        return oAuth2Manager.isSignedIn()
    }
}
