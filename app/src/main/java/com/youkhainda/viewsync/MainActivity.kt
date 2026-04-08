package com.youkhainda.viewsync

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.youkhainda.viewsync.auth.OAuth2Manager
import com.youkhainda.viewsync.ui.screen.AddVideoScreen
import com.youkhainda.viewsync.ui.screen.SyncPlayerScreen
import com.youkhainda.viewsync.ui.screen.VideoSearchScreen
import com.youkhainda.viewsync.ui.theme.ViewSyncTheme
import com.youkhainda.viewsync.ui.viewmodel.SyncPlayerViewModel
import com.youkhainda.viewsync.util.DebugLogger
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var oAuth2Manager: OAuth2Manager

    // Activity result launcher for Google Sign-In
    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        DebugLogger.i("MainActivity", "Sign-in result received")
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        val account = oAuth2Manager.handleSignInResult(task)
        if (account != null) {
            DebugLogger.i("MainActivity", "Sign-in successful - ${account.displayName}")
        } else {
            DebugLogger.w("MainActivity", "Sign-in failed")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize OAuth2Manager
        oAuth2Manager = OAuth2Manager(this)

        setContent {
            ViewSyncTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ViewSyncApp(
                        onSignInResult = { intent ->
                            signInLauncher.launch(intent)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun ViewSyncApp(onSignInResult: (Intent) -> Unit = {}) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = NavigationRoute.SEARCH.route,
    ) {
        addSearchRoute(navController)
        addSessionGraph(navController, onSignInResult)
    }
}

private fun NavGraphBuilder.addSearchRoute(navController: NavController) {
    composable(NavigationRoute.SEARCH.route) {
        VideoSearchScreen(
            onSessionCreated = { sessionId ->
                navController.navigate(NavigationRoute.SESSION_GRAPH.route.replace("{sessionId}", sessionId)) {
                    popUpTo(NavigationRoute.SEARCH.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
        )
    }
}

private fun NavGraphBuilder.addSessionGraph(navController: NavController, onSignInResult: (Intent) -> Unit) {
    navigation(
        startDestination = NavigationRoute.PLAYER.route,
        route = NavigationRoute.SESSION_GRAPH.route,
        arguments = listOf(
            navArgument("sessionId") { type = NavType.StringType },
        ),
    ) {
        composable(NavigationRoute.PLAYER.route) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
            val viewModel: SyncPlayerViewModel = hiltViewModel()
            SyncPlayerScreen(
                sessionId = sessionId,
                viewModel = viewModel,
                onAddVideo = {
                    navController.navigate(NavigationRoute.ADD_VIDEO.route.replace("{sessionId}", sessionId))
                },
                onSignInRequired = {
                    // This will be handled by the OAuth2Manager in the UI
                },
            )
        }

        composable(
            route = NavigationRoute.ADD_VIDEO.route,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
            val viewModel: SyncPlayerViewModel = hiltViewModel()
            AddVideoScreen(
                onAddVideos = { videos ->
                    viewModel.addVideosToSession(sessionId, videos)
                    navController.popBackStack()
                },
            )
        }
    }
}

sealed class NavigationRoute(val route: String) {
    data object SEARCH : NavigationRoute("search")
    data object SESSION_GRAPH : NavigationRoute("session_graph/{sessionId}")
    data object PLAYER : NavigationRoute("player/{sessionId}")
    data object ADD_VIDEO : NavigationRoute("add_video/{sessionId}")
}
