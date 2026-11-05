package dev.hashir.orbittv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hashir.orbittv.data.Catalog
import dev.hashir.orbittv.data.Channel
import dev.hashir.orbittv.ui.OrbitViewModel
import dev.hashir.orbittv.ui.components.ChannelGrid
import dev.hashir.orbittv.ui.components.Pill
import dev.hashir.orbittv.ui.theme.Ink

@Composable
fun FavoritesScreen(
    vm: OrbitViewModel,
    catalog: Catalog,
    onChannel: (Channel) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()

    val pinned = remember(favorites, catalog) {
        favorites.mapNotNull { catalog.byId[it] }.sortedBy { it.name.lowercase() }
    }
    val history = remember(recents, catalog) { recents.mapNotNull { catalog.byId[it] } }

    Column(Modifier.fillMaxSize().background(Ink)) {
        Text(
            "Saved",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 10.dp),
        )

        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pill("Favourites (${pinned.size})", selected = tab == 0, onClick = { tab = 0 })
            Pill("Recent (${history.size})", selected = tab == 1, onClick = { tab = 1 })
        }

        ChannelGrid(
            channels = if (tab == 0) pinned else history,
            favorites = favorites,
            onChannel = onChannel,
            emptyTitle = if (tab == 0) "No favourites yet" else "Nothing watched yet",
            emptyBody = if (tab == 0) {
                "Tap a channel and hit the star to pin it here."
            } else {
                "Channels you watch show up here so you can jump straight back in."
            },
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            modifier = Modifier.fillMaxSize(),
        )
    }
}
