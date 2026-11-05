package dev.hashir.orbittv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.hashir.orbittv.R
import dev.hashir.orbittv.data.Channel
import dev.hashir.orbittv.ui.components.Pill
import dev.hashir.orbittv.ui.components.brandGradient
import dev.hashir.orbittv.ui.components.initialsOf
import dev.hashir.orbittv.ui.theme.Accent
import dev.hashir.orbittv.ui.theme.Surface1
import dev.hashir.orbittv.ui.theme.Surface2
import dev.hashir.orbittv.ui.theme.TextSecondary
import dev.hashir.orbittv.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelSheet(
    channel: Channel,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlay: (sourceIndex: Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val gradient = remember(channel.id) { brandGradient(channel.id) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface1,
        dragHandle = null,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 30.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(gradient)
                        .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        initialsOf(channel.name),
                        color = Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    )
                    if (!channel.logo.isNullOrBlank()) {
                        AsyncImage(
                            model = channel.logo,
                            contentDescription = channel.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(10.dp),
                        )
                    }
                }

                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 14.dp)
                ) {
                    Text(
                        channel.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        listOfNotNull(
                            channel.flag.takeIf { it.isNotBlank() },
                            channel.countryName.takeIf { it.isNotBlank() },
                        ).joinToString(" "),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (isFavorite) Accent.copy(alpha = 0.18f) else Surface2)
                        .clickable(onClick = onToggleFavorite),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(if (isFavorite) R.drawable.ic_star else R.drawable.ic_star_border),
                        contentDescription = if (isFavorite) "Remove from favourites" else "Add to favourites",
                        tint = if (isFavorite) Accent else TextSecondary,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }

            if (channel.categoryNames.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(channel.categoryNames) { name -> Pill(name) }
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Accent)
                    .clickable(enabled = channel.streams.isNotEmpty()) { onPlay(0) }
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(7.dp))
                Text(
                    if (channel.streams.isEmpty()) "No stream available" else "Watch now",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }

            if (channel.streams.size > 1) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "Other sources",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "If one stalls, try another — the list mixes broadcasters and mirrors.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
                Spacer(Modifier.height(8.dp))
                channel.streams.forEachIndexed { index, source ->
                    if (index == 0) return@forEachIndexed
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 7.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Surface2)
                            .clickable { onPlay(index) }
                            .padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_play_arrow),
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                source.feed?.takeIf { it.isNotBlank() } ?: "Source ${index + 1}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val meta = listOfNotNull(source.quality, source.label).joinToString(" · ")
                            if (meta.isNotBlank()) {
                                Text(meta, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                            }
                        }
                    }
                }
            }
        }
    }
}
