package dev.hashir.orbittv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.hashir.orbittv.data.Catalog
import dev.hashir.orbittv.data.Channel
import dev.hashir.orbittv.ui.OrbitViewModel
import dev.hashir.orbittv.ui.components.ChannelRail
import dev.hashir.orbittv.ui.components.SectionHeader
import dev.hashir.orbittv.ui.components.brandGradient
import dev.hashir.orbittv.ui.components.initialsOf
import dev.hashir.orbittv.ui.theme.Accent
import dev.hashir.orbittv.ui.theme.Ink
import dev.hashir.orbittv.ui.theme.Surface1
import dev.hashir.orbittv.ui.theme.TextSecondary
import dev.hashir.orbittv.ui.theme.TextTertiary
import java.util.concurrent.TimeUnit

private val FEATURED_CATEGORIES = listOf(
    "news" to "Live News",
    "sports" to "Sports",
    "movies" to "Movies",
    "entertainment" to "Entertainment",
    "music" to "Music",
    "kids" to "Kids",
    "documentary" to "Documentaries",
    "series" to "Series",
    "comedy" to "Comedy",
    "culture" to "Culture",
    "science" to "Science",
    "travel" to "Travel",
)

@Composable
fun HomeScreen(
    vm: OrbitViewModel,
    catalog: Catalog,
    onChannel: (Channel) -> Unit,
    onCollection: (kind: String, key: String, title: String) -> Unit,
) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val hideOffline by vm.hideOffline.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val healthVersion by vm.healthVersion.collectAsStateWithLifecycle()

    // Re-derive the rails whenever the catalogue, the pins or what we know
    // about dead links changes.
    val rails = remember(catalog, favorites, recents, hideOffline, healthVersion) {
        buildRails(vm, catalog, favorites, recents)
    }

    val hero = remember(catalog, healthVersion) {
        val pool = vm.visible(
            catalog.byCountry[vm.deviceCountry].orEmpty().ifEmpty { catalog.byCategory["news"].orEmpty() }
        )
        if (pool.isEmpty()) null else pool[(dayStamp() % pool.size.toLong()).toInt()]
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Ink),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            TopRow(
                subtitle = "${catalog.channels.size} channels · ${catalog.countries.size} countries",
                refreshing = refreshing,
                hideOffline = hideOffline,
                onRefresh = vm::refresh,
                onToggleOffline = { vm.setHideOffline(!hideOffline) },
            )
        }

        if (hero != null) {
            item {
                HeroCard(
                    channel = hero,
                    onPlay = { onChannel(hero) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        items(rails, key = { it.title }) { rail ->
            ChannelRail(
                title = rail.title,
                channels = rail.channels,
                favorites = favorites,
                onChannel = onChannel,
                action = rail.collection?.let { "See all" },
                onAction = rail.collection?.let { c -> { onCollection(c.first, c.second, rail.title) } },
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        item {
            SectionHeader("Browse by country")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(catalog.countries.take(24), key = { it.code }) { country ->
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Surface1)
                            .clickable { onCollection("country", country.code, country.name) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Text(country.flag.ifBlank { "🌐" }, fontSize = 22.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            country.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                        )
                        Text(
                            "${country.count} channels",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary,
                        )
                    }
                }
            }
        }
    }
}

private data class Rail(
    val title: String,
    val channels: List<Channel>,
    val collection: Pair<String, String>? = null,
)

private fun buildRails(
    vm: OrbitViewModel,
    catalog: Catalog,
    favorites: Set<String>,
    recents: List<String>,
): List<Rail> {
    val rails = mutableListOf<Rail>()

    val recentChannels = recents.mapNotNull { catalog.byId[it] }
    if (recentChannels.isNotEmpty()) rails += Rail("Continue watching", recentChannels.take(20))

    val pinned = favorites.mapNotNull { catalog.byId[it] }.sortedBy { it.name }
    if (pinned.isNotEmpty()) rails += Rail("Your channels", pinned.take(20))

    val home = vm.visible(catalog.byCountry[vm.deviceCountry].orEmpty())
    if (home.isNotEmpty()) {
        val name = catalog.countries.firstOrNull { it.code == vm.deviceCountry }?.name ?: "your country"
        rails += Rail("From $name", home.take(20), "country" to vm.deviceCountry)
    }

    FEATURED_CATEGORIES.forEach { (id, label) ->
        val list = vm.visible(catalog.byCategory[id].orEmpty())
        if (list.size >= 6) {
            rails += Rail(label, list.shuffled(java.util.Random(dayStamp() + id.hashCode())).take(20), "category" to id)
        }
    }

    return rails
}

/** Changes once a day, so the hero and the shuffles stay stable within a session. */
private fun dayStamp(): Long = System.currentTimeMillis() / TimeUnit.DAYS.toMillis(1)

@Composable
private fun TopRow(
    subtitle: String,
    refreshing: Boolean,
    hideOffline: Boolean,
    onRefresh: () -> Unit,
    onToggleOffline: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Orbit TV",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        }

        IconPill(
            icon = Icons.Rounded.VisibilityOff,
            active = hideOffline,
            contentDescription = if (hideOffline) "Offline channels hidden" else "Showing all channels",
            onClick = onToggleOffline,
        )
        Spacer(Modifier.size(8.dp))
        IconPill(
            icon = Icons.Rounded.Refresh,
            active = refreshing,
            contentDescription = "Refresh channel list",
            onClick = onRefresh,
        )
    }
}

@Composable
private fun IconPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (active) Accent.copy(alpha = 0.18f) else Surface1)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (active) Accent else TextSecondary,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun HeroCard(channel: Channel, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    val gradient = remember(channel.id) { brandGradient(channel.id) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(18.dp))
            .background(gradient)
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
            .clickable(onClick = onPlay),
    ) {
        Text(
            text = initialsOf(channel.name),
            color = Color.White.copy(alpha = 0.10f),
            fontWeight = FontWeight.Black,
            fontSize = 120.sp,
            modifier = Modifier.align(Alignment.Center),
        )

        if (!channel.logo.isNullOrBlank()) {
            AsyncImage(
                model = channel.logo,
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(18.dp)
                    .size(88.dp),
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
        ) {
            Text(
                text = "FEATURED TODAY",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = channel.name,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = listOfNotNull(
                    channel.flag.takeIf { it.isNotBlank() },
                    channel.countryName.takeIf { it.isNotBlank() },
                    channel.categoryNames.firstOrNull(),
                    channel.bestQuality,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onPlay)
                    .padding(horizontal = 18.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Watch", style = MaterialTheme.typography.labelLarge, color = Color.Black)
            }
        }
    }
}
