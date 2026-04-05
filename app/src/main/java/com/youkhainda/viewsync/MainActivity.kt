package com.youkhainda.viewsync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.youkhainda.viewsync.ui.screen.AddVideoScreen
import com.youkhainda.viewsync.ui.screen.SyncPlayerScreen
import com.youkhainda.viewsync.ui.screen.VideoSearchScreen
import com.youkhainda.viewsync.ui.theme.ViewSyncTheme
import com.youkhainda.viewsync.ui.viewmodel.SyncPlayerViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ViewSyncTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ViewSyncApp()
                }
            }
        }
    }
}

@Composable
fun ViewSyncApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = NavigationRoute.SEARCH.route,
    ) {
        addSearchRoute(navController)
        addSessionGraph(navController)
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

private fun NavGraphBuilder.addSessionGraph(navController: NavController) {
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
