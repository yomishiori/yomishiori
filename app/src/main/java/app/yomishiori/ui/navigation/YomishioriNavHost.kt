package app.yomishiori.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.yomishiori.rememberReaderOpener
import app.yomishiori.ui.browse.BrowseScreen
import app.yomishiori.ui.details.DetailsScreen
import app.yomishiori.ui.edit.EditEntryScreen
import app.yomishiori.ui.history.HistoryScreen
import app.yomishiori.ui.library.LibraryScreen
import app.yomishiori.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable object LibraryRoute
@Serializable object HistoryRoute
@Serializable object BrowseRoute
@Serializable object SettingsRoute
@Serializable data class DetailsRoute(val entryId: Long)
@Serializable data class EditRoute(val entryId: Long = 0)

private data class Tab(val route: Any, val routeClass: KClass<*>, val label: String, val selected: ImageVector, val unselected: ImageVector)

private val tabs = listOf(
    Tab(LibraryRoute, LibraryRoute::class, "Library", Icons.AutoMirrored.Filled.LibraryBooks, Icons.AutoMirrored.Outlined.LibraryBooks),
    Tab(HistoryRoute, HistoryRoute::class, "History", Icons.Filled.History, Icons.Outlined.History),
    Tab(BrowseRoute, BrowseRoute::class, "Browse", Icons.Filled.Explore, Icons.Outlined.Explore),
    Tab(SettingsRoute, SettingsRoute::class, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
fun YomishioriNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val currentTab = tabs.firstOrNull { tab -> destination?.hasRoute(tab.routeClass) == true }
    val openReader = rememberReaderOpener()

    Scaffold(
        bottomBar = {
            if (currentTab != null) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = tab == currentTab
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigateToTab(tab.route)
                            },
                            icon = { Icon(if (selected) tab.selected else tab.unselected, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        NavHost(nav, startDestination = LibraryRoute, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable<LibraryRoute> {
                LibraryScreen(
                    onOpenEntry = { nav.navigate(DetailsRoute(it)) },
                    onResume = openReader,
                    onAddNovel = { nav.navigateToTab(BrowseRoute) },
                )
            }
            composable<HistoryRoute> {
                HistoryScreen(onOpenEntry = { nav.navigate(DetailsRoute(it)) }, onResume = openReader)
            }
            composable<BrowseRoute> {
                BrowseScreen(
                    onAddManually = { nav.navigate(EditRoute()) },
                    onOpenEntry = { nav.navigate(DetailsRoute(it)) },
                    onOpenSource = openReader,
                )
            }
            composable<SettingsRoute> { SettingsScreen() }
            composable<DetailsRoute> { entry ->
                val route = entry.toRoute<DetailsRoute>()
                DetailsScreen(
                    entryId = route.entryId,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(EditRoute(it)) },
                    onOpenSource = openReader,
                )
            }
            composable<EditRoute> { entry ->
                val route = entry.toRoute<EditRoute>()
                EditEntryScreen(
                    entryId = route.entryId,
                    onClose = { nav.popBackStack() },
                    onSaved = { id ->
                        nav.popBackStack()
                        if (route.entryId == 0L) nav.navigate(DetailsRoute(id))
                    },
                )
            }
        }
    }
}

private fun NavHostController.navigateToTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id)
    launchSingleTop = true
}
