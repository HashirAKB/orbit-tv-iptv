package dev.hashir.orbittv.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.hashir.orbittv.data.CatalogState
import dev.hashir.orbittv.data.Channel
import dev.hashir.orbittv.player.PlayerActivity
import dev.hashir.orbittv.ui.screens.BrowseScreen
import dev.hashir.orbittv.ui.screens.ChannelSheet
import dev.hashir.orbittv.ui.screens.CollectionScreen
import dev.hashir.orbittv.ui.screens.FavoritesScreen
import dev.hashir.orbittv.ui.screens.HomeScreen
import dev.hashir.orbittv.ui.screens.LoadingScreen
import dev.hashir.orbittv.ui.screens.SearchScreen
import dev.hashir.orbittv.ui.theme.Accent
import dev.hashir.orbittv.ui.theme.Ink
import dev.hashir.orbittv.ui.theme.OrbitTheme
import dev.hashir.orbittv.ui.theme.Surface1
import dev.hashir.orbittv.ui.theme.TextTertiary
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            OrbitTheme {
                Box(Modifier.fillMaxSize().background(Ink)) {
                    OrbitRoot()
                }
            }
        }
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Rounded.Home),
    Browse("browse", "Browse", Icons.Rounded.Explore),
    Search("search", "Search", Icons.Rounded.Search),
    Favorites("favorites", "Saved", Icons.Rounded.Star),
}

@Composable
fun OrbitRoot(vm: OrbitViewModel = viewModel()) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val state by vm.catalogState.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()

    var sheetChannel by remember { mutableStateOf<Channel?>(null) }

    val openChannel: (Channel) -> Unit = { sheetChannel = it }
    val play: (Channel, Int) -> Unit = { channel, sourceIndex ->
        vm.noteWatched(channel.id)
        sheetChannel = null
        context.startActivity(
            Intent(context, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_CHANNEL_ID, channel.id)
                putExtra(PlayerActivity.EXTRA_SOURCE_INDEX, sourceIndex)
            }
        )
    }

    val backEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backEntry?.destination?.route
    val showBar = Tab.entries.any { it.route == currentRoute }

    Scaffold(
        containerColor = Ink,
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = Surface1, tonalElevation = 0.dp) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    nav.navigate(tab.route) {
                                        popUpTo(Tab.Home.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Accent,
                                selectedTextColor = Accent,
                                indicatorColor = Surface1,
                                unselectedIconColor = TextTertiary,
                                unselectedTextColor = TextTertiary,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is CatalogState.Loading -> LoadingScreen(s.step, s.fraction)
                is CatalogState.Failed -> LoadingScreen(s.message, -1f, onRetry = vm::refresh)
                is CatalogState.Ready -> {
                    NavHost(navController = nav, startDestination = Tab.Home.route) {
                        composable(Tab.Home.route) {
                            HomeScreen(
                                vm = vm,
                                catalog = s.catalog,
                                onChannel = openChannel,
                                onCollection = { kind, key, title ->
                                    nav.navigate("list/$kind/$key/${encode(title)}")
                                },
                            )
                        }
                        composable(Tab.Browse.route) {
                            BrowseScreen(
                                catalog = s.catalog,
                                onCollection = { kind, key, title ->
                                    nav.navigate("list/$kind/$key/${encode(title)}")
                                },
                            )
                        }
                        composable(Tab.Search.route) {
                            SearchScreen(vm = vm, catalog = s.catalog, onChannel = openChannel)
                        }
                        composable(Tab.Favorites.route) {
                            FavoritesScreen(vm = vm, catalog = s.catalog, onChannel = openChannel)
                        }
                        composable(
                            route = "list/{kind}/{key}/{title}",
                            arguments = listOf(
                                navArgument("kind") { type = NavType.StringType },
                                navArgument("key") { type = NavType.StringType },
                                navArgument("title") { type = NavType.StringType },
                            ),
                        ) { entry ->
                            CollectionScreen(
                                vm = vm,
                                catalog = s.catalog,
                                kind = entry.arguments?.getString("kind").orEmpty(),
                                key = entry.arguments?.getString("key").orEmpty(),
                                title = decode(entry.arguments?.getString("title").orEmpty()),
                                onChannel = openChannel,
                                onBack = { nav.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }

    sheetChannel?.let { channel ->
        ChannelSheet(
            channel = channel,
            isFavorite = channel.id in favorites,
            onDismiss = { sheetChannel = null },
            onToggleFavorite = { vm.toggleFavorite(channel.id) },
            onPlay = { index -> play(channel, index) },
        )
    }
}

private fun encode(value: String): String = URLEncoder.encode(value.replace('/', '-'), "UTF-8")

private fun decode(value: String): String = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
