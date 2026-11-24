package dev.hashir.orbittv.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import dev.hashir.orbittv.ui.components.rememberIsTv
import dev.hashir.orbittv.ui.components.tvClickable
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.viewModels
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.hashir.orbittv.R
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
    private val vm: OrbitViewModel by viewModels()
    private val voice = VoiceCatcher()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleVoiceIntent(intent)
        setContent {
            OrbitTheme {
                Box(Modifier.fillMaxSize().background(Ink)) {
                    OrbitRoot(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleVoiceIntent(intent)
    }

    private fun handleVoiceIntent(intent: Intent?) {
        val text = intent?.getStringExtra(EXTRA_VOICE_QUERY)?.takeIf { it.isNotBlank() } ?: return
        intent.removeExtra(EXTRA_VOICE_QUERY)
        vm.voiceQuery(text)
    }

    /**
     * The voice bridge types what you said with `input text`, i.e. as a quick
     * burst of key presses into whatever has focus. Outside the search box,
     * catch that burst and treat it as a voice query.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!vm.searchFieldFocused) {
            val text = voice.printable(event)
            if (text != null) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (voice.isNewBurst(event)) vm.beginVoice(text) else vm.appendVoice(text)
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val EXTRA_VOICE_QUERY = "voice_query"
    }
}

/** Recognises typed characters and groups them into bursts. */
class VoiceCatcher(private val gapMs: Long = 1500) {
    private var last = 0L

    /** The character this key event types, or null for D-pad, Back and other non-text keys. */
    fun printable(event: KeyEvent): String? {
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return null
        if (event.action == KeyEvent.ACTION_MULTIPLE && event.keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            return event.characters
        }
        val c = event.unicodeChar
        if (c == 0 || Character.isISOControl(c)) return null
        return String(Character.toChars(c))
    }

    /**
     * Uses the event's own timestamp, not the time we handle it: the first key
     * of a burst triggers navigation, which can stall the main thread long
     * enough to make the queued rest of the burst look like a new one.
     */
    fun isNewBurst(event: KeyEvent): Boolean {
        val t = event.eventTime
        val fresh = t - last > gapMs
        last = t
        return fresh
    }
}

private enum class Tab(val route: String, val label: String, @DrawableRes val icon: Int) {
    Home("home", "Home", R.drawable.ic_home),
    Browse("browse", "Browse", R.drawable.ic_explore),
    Search("search", "Search", R.drawable.ic_search),
    Favorites("favorites", "Saved", R.drawable.ic_star),
}

@Composable
fun OrbitRoot(vm: OrbitViewModel = viewModel()) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val state by vm.catalogState.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val voiceTick by vm.voiceTick.collectAsStateWithLifecycle()

    var sheetChannel by remember { mutableStateOf<Channel?>(null) }

    // Voice text arrived: show it on the Search tab, whatever screen we were on.
    LaunchedEffect(voiceTick, state is CatalogState.Ready) {
        if (voiceTick == 0 || state !is CatalogState.Ready) return@LaunchedEffect
        sheetChannel = null
        if (nav.currentBackStackEntry?.destination?.route != Tab.Search.route) {
            nav.navigate(Tab.Search.route) {
                popUpTo(Tab.Home.route) { saveState = true }
                launchSingleTop = true
            }
        }
    }

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
    val isTv = rememberIsTv()

    val goTo: (Tab) -> Unit = { tab ->
        if (currentRoute != tab.route) {
            nav.navigate(tab.route) {
                popUpTo(Tab.Home.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Row(Modifier.fillMaxSize()) {
    if (isTv && showBar && state is CatalogState.Ready) {
        // A remote reaches a side rail with one press of LEFT; a bottom bar
        // sits under hundreds of rows of tiles.
        NavigationRail(
            containerColor = Surface1,
            modifier = Modifier.fillMaxHeight().padding(vertical = 0.dp),
        ) {
            Spacer(Modifier.height(18.dp))
            Tab.entries.forEach { tab ->
                val selected = currentRoute == tab.route
                val tint = if (selected) Accent else TextTertiary
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .width(68.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) Accent.copy(alpha = 0.14f) else Surface1)
                        .tvClickable(RoundedCornerShape(14.dp)) { goTo(tab) }
                        .padding(vertical = 10.dp),
                ) {
                    Icon(painterResource(tab.icon), contentDescription = tab.label, tint = tint)
                    Spacer(Modifier.height(4.dp))
                    Text(tab.label, style = MaterialTheme.typography.labelSmall, color = tint)
                }
            }
        }
    }

    Scaffold(
        containerColor = Ink,
        modifier = Modifier.weight(1f),
        bottomBar = {
            if (showBar && !isTv) {
                NavigationBar(containerColor = Surface1, tonalElevation = 0.dp) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { goTo(tab) },
                            icon = { Icon(painterResource(tab.icon), contentDescription = tab.label) },
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
