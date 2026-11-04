package com.stellarlogbook.app.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import android.net.Uri
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stellarlogbook.app.R
import com.stellarlogbook.app.core.AppContainer
import com.stellarlogbook.app.ui.onboarding.OnboardingScreen
import com.stellarlogbook.app.ui.screens.DiscoveriesScreen
import com.stellarlogbook.app.ui.screens.ExploreScreen
import com.stellarlogbook.app.ui.screens.HomeScreen
import com.stellarlogbook.app.ui.screens.MapScreen
import com.stellarlogbook.app.ui.screens.SettingsScreen
import com.stellarlogbook.app.ui.screens.SystemDetailScreen
import com.stellarlogbook.app.ui.screens.VisitedScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

/**
 * Bottom nav answers the four core questions in one tap each:
 * Visited / Discoveries / Explore (unvisited + uncharted) / Map, plus Home.
 * Labels always visible, large targets — the "very easy to use" contract.
 */
@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    var showOnboarding by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        showOnboarding = container.userPrefs.commanderName.first()?.isNullOrBlank() ?: true
    }

    val onboarding = showOnboarding
    if (onboarding == null) {
        Box(Modifier.fillMaxSize()) // one frame while prefs load
        return
    }
    if (onboarding) {
        OnboardingScreen(
            onDone = { showOnboarding = false },
            onSaveCredentials = { commander, apiKey ->
                container.repository.setCredentials(commander, apiKey)
            }
        )
        return
    }

    val items = listOf(
        NavItem("home", stringResource(R.string.nav_home), Icons.Filled.Home),
        NavItem("visited", stringResource(R.string.nav_visited), Icons.Filled.List),
        NavItem("discoveries", stringResource(R.string.nav_discoveries), Icons.Filled.Star),
        NavItem("explore", stringResource(R.string.nav_explore), Icons.Filled.Place),
        NavItem("map", stringResource(R.string.nav_map), Icons.Filled.LocationOn)
    )
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            if (items.any { it.route == currentRoute }) {
                NavigationBar {
                    items.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") {
                HomeScreen(container, onOpenSettings = { navController.navigate("settings") })
            }
            composable("visited") {
                VisitedScreen(
                    container,
                    onOpenSystem = { name -> navController.navigate("system/${Uri.encode(name)}") }
                )
            }
            composable("discoveries") { DiscoveriesScreen(container) }
            composable("explore") { ExploreScreen(container) }
            composable("map") { MapScreen(container) }
            composable(
                route = "system/{name}",
                arguments = listOf(navArgument("name") { type = NavType.StringType })
            ) { entry ->
                val name = entry.arguments?.getString("name").orEmpty()
                SystemDetailScreen(container, name, onBack = { navController.popBackStack() })
            }
            composable("settings") {
                SettingsScreen(container, onBack = { navController.popBackStack() })
            }
        }
    }
}
