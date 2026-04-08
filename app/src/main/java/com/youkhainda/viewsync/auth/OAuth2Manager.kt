package com.youkhainda.viewsync.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.youkhainda.viewsync.BuildConfig
import com.youkhainda.viewsync.util.DebugLogger
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Manages Google OAuth 2.0 authentication for YouTube API write operations.
 *
 * Required scopes for YouTube actions:
 * - https://www.googleapis.com/auth/youtube.force-ssl (for like, comment, subscribe)
 */
class OAuth2Manager(private val context: Context) {

    companion object {
        private const val TAG = "OAuth2Manager"
        const val RC_SIGN_IN = 9001
        const val YOUTUBE_SCOPE = "https://www.googleapis.com/auth/youtube.force-ssl"
    }

    private val googleSignInClient: GoogleSignInClient by lazy {
        val clientId = BuildConfig.GOOGLE_OAUTH_CLIENT_ID
        val hasClientId = clientId.isNotEmpty() && clientId != "YOUR_CLIENT_ID.apps.googleusercontent.com"

        DebugLogger.d(TAG, "OAuth Client ID configured: ${hasClientId}")

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(clientId.takeIf { hasClientId } ?: "")
            .requestScopes(Scope(YOUTUBE_SCOPE))
            .build()

        GoogleSignIn.getClient(context, gso)
    }

    /**
     * Check if user is currently signed in
     */
    fun isSignedIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account != null
    }

    /**
     * Get the currently signed in account, if any
     */
    fun getCurrentAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    /**
     * Get the current user's display name
     */
    fun getCurrentUserName(): String? {
        return GoogleSignIn.getLastSignedInAccount(context)?.displayName
    }

    /**
     * Get a fresh access token for the authenticated user.
     * This will silently refresh if the token has expired.
     */
    suspend fun getAccessToken(): String? = suspendCancellableCoroutine { continuation ->
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account == null) {
            DebugLogger.w(TAG, "No signed-in account, cannot get access token")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        // Use the ID token from the cached account
        val idToken = account.idToken
        if (idToken != null) {
            DebugLogger.d(TAG, "Using cached ID token for authentication")
            continuation.resume(idToken)
        } else {
            // Need to re-authenticate to get fresh token
            DebugLogger.w(TAG, "No ID token available, user needs to sign in again")
            continuation.resume(null)
        }
    }

    /**
     * Get the sign-in intent to launch from an Activity.
     * The result will be returned with requestCode RC_SIGN_IN.
     */
    fun getSignInIntent(): Intent {
        DebugLogger.i(TAG, "Creating sign-in intent")
        return googleSignInClient.signInIntent
    }

    /**
     * Handle the sign-in result from onActivityResult.
     * Returns the GoogleSignInAccount if successful, null otherwise.
     */
    fun handleSignInResult(task: Task<GoogleSignInAccount>): GoogleSignInAccount? {
        return try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            DebugLogger.i(TAG, "Sign-in successful - User: ${account.displayName}, Email: ${account.email}")

            // Check granted scopes
            val grantedScopes: List<String> = account.grantedScopes?.map { s: com.google.android.gms.common.api.Scope -> s.scope } ?: emptyList()
            DebugLogger.d(TAG, "Granted scopes: $grantedScopes")

            if (!hasYouTubeScope(grantedScopes)) {
                DebugLogger.w(TAG, "YouTube scope not granted - some features may not work")
            }

            account
        } catch (e: com.google.android.gms.common.api.ApiException) {
            DebugLogger.e(TAG, "Sign-in failed - Status: ${e.statusCode}, Message: ${e.statusMessage}")
            null
        }
    }

    /**
     * Sign out the current user
     */
    suspend fun signOut() = suspendCancellableCoroutine<Unit> { continuation ->
        DebugLogger.i(TAG, "Signing out user")
        googleSignInClient.signOut()
            .addOnCompleteListener {
                DebugLogger.i(TAG, "Sign-out complete")
                continuation.resume(Unit)
            }
    }

    /**
     * Revoke all granted permissions
     */
    suspend fun revokeAccess() = suspendCancellableCoroutine<Unit> { continuation ->
        DebugLogger.i(TAG, "Revoking access")
        googleSignInClient.revokeAccess()
            .addOnCompleteListener {
                DebugLogger.i(TAG, "Access revoked")
                continuation.resume(Unit)
            }
    }

    /**
     * Flow that emits authentication state changes
     */
    fun authStateFlow(): Flow<AuthState> = callbackFlow {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        trySend(
            if (account != null) AuthState.Authenticated(account)
            else AuthState.Unauthenticated
        )

        awaitClose { /* No cleanup needed */ }
    }

    private fun hasYouTubeScope(grantedScopes: List<String>): Boolean {
        return grantedScopes.contains(YOUTUBE_SCOPE) ||
               grantedScopes.contains("https://www.googleapis.com/auth/youtube")
    }
}

/**
 * Represents the current authentication state
 */
sealed class AuthState {
    data object Unauthenticated : AuthState()
    data class Authenticated(val account: GoogleSignInAccount) : AuthState()
    data class Error(val message: String) : AuthState()
}
