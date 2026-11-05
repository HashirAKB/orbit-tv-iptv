package dev.hashir.orbittv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hashir.orbittv.R
import dev.hashir.orbittv.data.Catalog
import dev.hashir.orbittv.data.Channel
import dev.hashir.orbittv.ui.OrbitViewModel
import dev.hashir.orbittv.ui.components.ChannelGrid
import dev.hashir.orbittv.ui.components.EmptyState
import dev.hashir.orbittv.ui.components.Pill
import dev.hashir.orbittv.ui.theme.Accent
import dev.hashir.orbittv.ui.theme.Ink
import dev.hashir.orbittv.ui.theme.Surface1
import dev.hashir.orbittv.ui.theme.TextSecondary

private val SUGGESTIONS = listOf("BBC", "Al Jazeera", "Sports", "News", "Movies", "Music", "Kids", "CNN", "Discovery")

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SearchScreen(
    vm: OrbitViewModel,
    catalog: Catalog,
    onChannel: (Channel) -> Unit,
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val healthVersion by vm.healthVersion.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    val results = remember(query, catalog, healthVersion) { vm.search(catalog, query) }

    // Nudge health checks along for whatever the user is actually looking at.
    LaunchedEffect(results) {
        if (results.isNotEmpty()) vm.probe(results.take(40), limit = 40)
    }

    Column(Modifier.fillMaxSize().background(Ink)) {
        OutlinedTextField(
            value = query,
            onValueChange = vm::setQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
            placeholder = { Text("Search 10,000+ channels", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = TextSecondary) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.setQuery("") }) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Clear", tint = TextSecondary)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Surface1,
                unfocusedContainerColor = Surface1,
                focusedBorderColor = Accent,
                unfocusedBorderColor = Surface1,
                cursorColor = Accent,
            ),
        )

        if (query.isBlank()) {
            LazyRow(
                modifier = Modifier.padding(bottom = 6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(SUGGESTIONS) { term ->
                    Pill(term, onClick = { vm.setQuery(term) })
                }
            }
            Box(Modifier.fillMaxSize()) {
                EmptyState(
                    title = "Find a channel",
                    body = "Search by name, country or category — try “Malayalam”, “BBC” or “Sports”.",
                )
            }
        } else if (query.trim().length < 2) {
            EmptyState("Keep typing", "Two characters or more, please.")
        } else {
            Text(
                text = if (results.isEmpty()) "No matches" else "${results.size} result${if (results.size == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
            ChannelGrid(
                channels = results,
                favorites = favorites,
                onChannel = onChannel,
                emptyTitle = "No channels found",
                emptyBody = "Nothing matched “$query”. Try a shorter word.",
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
