package dev.hashir.orbittv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hashir.orbittv.data.Catalog
import dev.hashir.orbittv.data.Channel
import dev.hashir.orbittv.ui.OrbitViewModel
import dev.hashir.orbittv.ui.components.ChannelGrid
import dev.hashir.orbittv.ui.theme.Ink
import dev.hashir.orbittv.ui.theme.Surface1
import dev.hashir.orbittv.ui.theme.TextSecondary
import dev.hashir.orbittv.ui.theme.TextTertiary

/** One country or one category, as a full-screen grid. */
@Composable
fun CollectionScreen(
    vm: OrbitViewModel,
    catalog: Catalog,
    kind: String,
    key: String,
    title: String,
    onChannel: (Channel) -> Unit,
    onBack: () -> Unit,
) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val hideOffline by vm.hideOffline.collectAsStateWithLifecycle()
    val healthVersion by vm.healthVersion.collectAsStateWithLifecycle()

    val channels = remember(kind, key, catalog, hideOffline, healthVersion) {
        val source = when (kind) {
            "country" -> catalog.byCountry[key].orEmpty()
            "category" -> catalog.byCategory[key].orEmpty()
            else -> emptyList()
        }
        vm.visible(source)
    }

    LaunchedEffect(kind, key) {
        vm.probe(channels.take(60), limit = 60)
    }

    Column(Modifier.fillMaxSize().background(Ink)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Surface1)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = TextSecondary,
                    modifier = Modifier.size(19.dp),
                )
            }
            Column(Modifier.padding(start = 10.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${channels.size} channels" + if (hideOffline) " · offline hidden" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
            }
        }

        ChannelGrid(
            channels = channels,
            favorites = favorites,
            onChannel = onChannel,
            emptyTitle = "Nothing playable here",
            emptyBody = "Every stream in this group looks offline. Turn off the offline filter on Home to see them anyway.",
            modifier = Modifier.fillMaxSize(),
        )
    }
}
