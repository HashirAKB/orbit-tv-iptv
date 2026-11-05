package dev.hashir.orbittv.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.hashir.orbittv.data.Channel
import dev.hashir.orbittv.ui.theme.Accent
import dev.hashir.orbittv.ui.theme.Surface1
import dev.hashir.orbittv.ui.theme.TextSecondary
import dev.hashir.orbittv.ui.theme.TextTertiary
import kotlin.math.abs

/**
 * Channel logos are inconsistent in size, shape and transparency, so every
 * tile sits on a deterministic gradient derived from the channel id. Same
 * channel, same colours, every launch -- which is what makes a wall of them
 * read as designed rather than random.
 */
fun brandGradient(seed: String): Brush {
    val h = abs(seed.hashCode())
    val hue = (h % 360).toFloat()
    val hue2 = (hue + 38f) % 360f
    val top = Color.hsl(hue, 0.42f, 0.24f)
    val bottom = Color.hsl(hue2, 0.48f, 0.12f)
    return Brush.linearGradient(listOf(top, bottom))
}

fun initialsOf(name: String): String {
    val words = name.split(' ', '-', '_').filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

@Composable
fun ChannelTile(
    channel: Channel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    isOffline: Boolean = false,
) {
    val gradient = remember(channel.id) { brandGradient(channel.id) }
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(12.dp))
                .background(gradient)
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            // Shows through whenever the logo is missing or fails to decode.
            Text(
                text = initialsOf(channel.name),
                color = Color.White.copy(alpha = 0.5f),
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            )

            if (!channel.logo.isNullOrBlank()) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                )
            }

            if (isFavorite) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(16.dp),
                )
            }

            channel.bestQuality?.let { q ->
                Text(
                    text = q,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }

            if (isOffline) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("OFFLINE", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = channel.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = listOfNotNull(
                channel.flag.takeIf { it.isNotBlank() },
                channel.countryName.takeIf { it.isNotBlank() },
            ).joinToString(" "),
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (action != null && onAction != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.clickable(onClick = onAction),
            )
        }
    }
}

@Composable
fun ChannelRail(
    title: String,
    channels: List<Channel>,
    favorites: Set<String>,
    onChannel: (Channel) -> Unit,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    if (channels.isEmpty()) return
    Column(modifier = modifier) {
        SectionHeader(title, action = action, onAction = onAction)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(channels, key = { it.id }) { channel ->
                ChannelTile(
                    channel = channel,
                    onClick = { onChannel(channel) },
                    isFavorite = channel.id in favorites,
                    modifier = Modifier.width(150.dp),
                )
            }
        }
    }
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) Accent else Surface1)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
