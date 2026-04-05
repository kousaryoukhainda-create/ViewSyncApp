package com.youkhainda.viewsync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.youkhainda.viewsync.ui.screen.SyncPlayerScreen
import com.youkhainda.viewsync.ui.screen.VideoSearchScreen
import com.youkhainda.viewsync.ui.theme.ViewSyncTheme
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
        addPlayerRoute(navController)
    }
}

private fun NavGraphBuilder.addSearchRoute(navController: NavController) {
    composable(NavigationRoute.SEARCH.route) {
        VideoSearchScreen(
            onSessionCreated = { sessionId ->
                navController.navigate(NavigationRoute.PLAYER.route.replace("{sessionId}", sessionId)) {
                    popUpTo(NavigationRoute.SEARCH.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
        )
    }
}

private fun NavGraphBuilder.addPlayerRoute(navController: NavController) {
    composable(
        route = NavigationRoute.PLAYER.route,
    ) { backStackEntry ->
        val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
        SyncPlayerScreen(sessionId = sessionId)
    }
}

sealed class NavigationRoute(val route: String) {
    data object SEARCH : NavigationRoute("search")
    data object PLAYER : NavigationRoute("player/{sessionId}")
}
