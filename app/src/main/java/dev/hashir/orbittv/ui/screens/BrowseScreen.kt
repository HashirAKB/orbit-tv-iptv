package dev.hashir.orbittv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hashir.orbittv.data.Catalog
import dev.hashir.orbittv.ui.components.Pill
import dev.hashir.orbittv.ui.theme.Ink
import dev.hashir.orbittv.ui.theme.Surface1
import dev.hashir.orbittv.ui.theme.TextTertiary

@Composable
fun BrowseScreen(
    catalog: Catalog,
    onCollection: (kind: String, key: String, title: String) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().background(Ink)) {
        Text(
            "Browse",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 10.dp),
        )

        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pill("Countries", selected = tab == 0, onClick = { tab = 0 })
            Pill("Categories", selected = tab == 1, onClick = { tab = 1 })
        }

        if (tab == 0) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(catalog.countries, key = { it.code }) { country ->
                    BrowseCard(
                        emoji = country.flag.ifBlank { "🌐" },
                        title = country.name,
                        subtitle = "${country.count} channels",
                        onClick = { onCollection("country", country.code, country.name) },
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(catalog.categories, key = { it.id }) { category ->
                    val emoji = remember(category.id) { categoryEmoji(category.id) }
                    BrowseCard(
                        emoji = emoji,
                        title = category.name,
                        subtitle = "${category.count} channels",
                        onClick = { onCollection("category", category.id, category.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowseCard(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface1)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Column {
            Text(emoji, fontSize = 24.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        }
    }
}

private fun categoryEmoji(id: String): String = when (id) {
    "animation" -> "🎨"
    "auto" -> "🚗"
    "business" -> "💼"
    "classic" -> "🎞️"
    "comedy" -> "😄"
    "cooking" -> "🍳"
    "culture" -> "🏛️"
    "documentary" -> "🎬"
    "education" -> "📚"
    "entertainment" -> "🎭"
    "family" -> "👨‍👩‍👧"
    "general" -> "📺"
    "kids" -> "🧸"
    "legislative" -> "⚖️"
    "lifestyle" -> "✨"
    "movies" -> "🍿"
    "music" -> "🎵"
    "news" -> "📰"
    "outdoor" -> "🏕️"
    "relax" -> "🌿"
    "religious" -> "🕊️"
    "science" -> "🔬"
    "series" -> "📼"
    "shop" -> "🛍️"
    "sports" -> "⚽"
    "travel" -> "✈️"
    "weather" -> "🌤️"
    else -> "📡"
}
