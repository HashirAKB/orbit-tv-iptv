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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import dev.hashir.orbittv.data.SmartResult
import kotlinx.coroutines.delay
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
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

    val smart by vm.smart.collectAsStateWithLifecycle()
    val localResults = remember(query, catalog, healthVersion) { vm.search(catalog, query) }

    // Plain-English requests go to the LLM once typing (or voice text) settles.
    LaunchedEffect(query, catalog) {
        if (vm.smartAvailable && query.trim().length >= 2) {
            delay(650)
            vm.runSmart(catalog, query)
        } else {
            vm.clearSmart()
        }
    }

    val smartDone = (smart as? OrbitViewModel.SmartState.Done)?.takeIf { it.text == query.trim() }
    val results = smartDone?.result?.channels ?: localResults

    // After a voice request, put the remote on the first result: OK plays it.
    val voiceTick by vm.voiceTick.collectAsStateWithLifecycle()
    val firstResult = remember { FocusRequester() }
    var focusedForTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(voiceTick, smartDone) {
        if (voiceTick > focusedForTick && smartDone != null && results.isNotEmpty()) {
            focusedForTick = voiceTick
            delay(120)
            runCatching { firstResult.requestFocus() }
        }
    }

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
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)
                .onFocusChanged { vm.searchFieldFocused = it.isFocused },
            placeholder = {
                Text(
                    if (vm.smartAvailable) "Say or type what you want — “Hindi movie channels”, “Pakistani news”"
                    else "Search 10,000+ channels",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
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
                    title = if (vm.smartAvailable) "Hold the mic and ask" else "Find a channel",
                    body = if (vm.smartAvailable) {
                        "Hold the remote's mic button and say something like “I want to watch a movie channel in Hindi” or “football in Spanish”. Typing works too."
                    } else {
                        "Search by name, country or category — try “Malayalam”, “BBC” or “Sports”."
                    },
                )
            }
        } else if (query.trim().length < 2) {
            EmptyState("Keep typing", "Two characters or more, please.")
        } else {
            when {
                smartDone != null -> SmartHeader(smartDone.result, catalog)
                smart is OrbitViewModel.SmartState.Thinking ->
                    StatusLine("Understanding “${query.trim()}”…   ${countLabel(localResults.size)} by name meanwhile")
                smart is OrbitViewModel.SmartState.Failed ->
                    StatusLine("Smart search unavailable (${(smart as OrbitViewModel.SmartState.Failed).message.take(60)}) · ${countLabel(results.size)} by name")
                else -> StatusLine(if (results.isEmpty()) "No matches" else countLabel(results.size))
            }
            ChannelGrid(
                channels = results,
                favorites = favorites,
                onChannel = onChannel,
                emptyTitle = "No channels found",
                emptyBody = "Nothing matched “$query”. Try a shorter word.",
                modifier = Modifier.fillMaxSize(),
                firstItemFocus = firstResult,
            )
        }
    }
}

private fun countLabel(n: Int) = "$n result${if (n == 1) "" else "s"}"

@Composable
private fun StatusLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    )
}

/** "✨ Hindi Movie Channels" plus the filters the request turned into. */
@Composable
private fun SmartHeader(result: SmartResult, catalog: Catalog) {
    val q = result.query
    val chips = remember(q, catalog) {
        buildList {
            q.languages.forEach { add("🗣 " + languageName(it)) }
            q.countries.forEach { code ->
                val c = catalog.countries.firstOrNull { it.code == code }
                add((c?.flag?.plus(" ") ?: "") + (c?.name ?: code))
            }
            q.categories.forEach { id -> add(catalog.categories.firstOrNull { it.id == id }?.name ?: id) }
            q.names.forEach { add("“$it”") }
        }
    }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "✨ " + q.title.ifBlank { "Results" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.width(10.dp))
            Text(countLabel(result.channels.size), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        }
        if (chips.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { chips.forEach { Pill(it) } }
        }
        result.relaxedNote?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

private fun languageName(code: String): String =
    runCatching { Locale(code).getDisplayLanguage(Locale.ENGLISH) }.getOrNull()
        ?.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
        ?: code.uppercase()

