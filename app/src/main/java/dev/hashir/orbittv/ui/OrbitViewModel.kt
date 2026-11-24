package dev.hashir.orbittv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.hashir.orbittv.Graph
import dev.hashir.orbittv.data.Catalog
import dev.hashir.orbittv.data.CatalogState
import dev.hashir.orbittv.data.Channel
import dev.hashir.orbittv.data.ChannelQuery
import dev.hashir.orbittv.data.SmartResult
import dev.hashir.orbittv.data.SmartSearch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class OrbitViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Graph.catalog
    private val prefs = Graph.prefs
    private val health = Graph.health

    val catalogState: StateFlow<CatalogState> = repo.state

    val favorites: StateFlow<Set<String>> =
        prefs.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val recents: StateFlow<List<String>> =
        prefs.recents.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hideOffline: StateFlow<Boolean> =
        prefs.hideOffline.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val showAdult: StateFlow<Boolean> =
        prefs.showAdult.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val healthVersion: StateFlow<Int> = health.version

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Device country, used to put something familiar at the top of Home. */
    val deviceCountry: String = runCatching {
        Locale.getDefault().country.uppercase(Locale.ROOT)
    }.getOrDefault("")

    init {
        viewModelScope.launch {
            health.restore()
            repo.load()
            warmUpHealthChecks()
        }
    }

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            repo.load(force = true)
            _refreshing.value = false
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    // ---- natural-language / voice search ---------------------------------

    sealed interface SmartState {
        data object Idle : SmartState
        data class Thinking(val text: String) : SmartState
        data class Done(val text: String, val result: SmartResult) : SmartState
        data class Failed(val text: String, val message: String) : SmartState
    }

    private val smartSearch = SmartSearch()
    val smartAvailable: Boolean = smartSearch.isAvailable

    private val _smart = MutableStateFlow<SmartState>(SmartState.Idle)
    val smart: StateFlow<SmartState> = _smart.asStateFlow()
    private var smartJob: Job? = null
    private val parsed = HashMap<String, ChannelQuery>()

    /** Bumped whenever voice text arrives, so the UI jumps to Search. */
    private val _voiceTick = MutableStateFlow(0)
    val voiceTick: StateFlow<Int> = _voiceTick.asStateFlow()

    /** True while the search box has focus, so typed keys go to it and not to the voice catcher. */
    @Volatile var searchFieldFocused: Boolean = false

    /** Start of a burst of typed text from the voice bridge: replace the query. */
    fun beginVoice(first: String) {
        _query.value = first
        _voiceTick.update { it + 1 }
    }

    fun appendVoice(more: String) {
        _query.update { it + more }
    }

    fun voiceQuery(text: String) {
        _query.value = text
        _voiceTick.update { it + 1 }
    }

    fun runSmart(catalog: Catalog, text: String) {
        val t = text.trim()
        smartJob?.cancel()
        if (!smartAvailable || t.length < 2) {
            _smart.value = SmartState.Idle
            return
        }
        val current = _smart.value
        if (current is SmartState.Done && current.text == t) return
        smartJob = viewModelScope.launch {
            _smart.value = SmartState.Thinking(t)
            try {
                val q = parsed[t] ?: smartSearch.parse(t, catalog.categories.map { it.id }).also {
                    parsed[t] = it
                    android.util.Log.i("OrbitSmart", "\"$t\" -> $it")
                }
                val result = withContext(Dispatchers.Default) { SmartSearch.apply(q, visible(catalog.channels)) }
                _smart.value = SmartState.Done(t, result)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("OrbitSmart", "smart search failed for \"$t\"", e)
                _smart.value = SmartState.Failed(t, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun clearSmart() {
        smartJob?.cancel()
        _smart.value = SmartState.Idle
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch { prefs.toggleFavorite(id) }
    }

    fun noteWatched(id: String) {
        viewModelScope.launch { prefs.noteWatched(id) }
    }

    fun setHideOffline(value: Boolean) {
        viewModelScope.launch { prefs.setHideOffline(value) }
    }

    fun setShowAdult(value: Boolean) {
        viewModelScope.launch { prefs.setShowAdult(value) }
    }

    /**
     * Probes a bounded slice of the list the user is currently looking at, so
     * the offline filter gets sharper the more they browse. Cheap and lazy on
     * purpose -- no attempt is made to sweep the whole catalogue.
     */
    fun probe(channels: List<Channel>, limit: Int = 60) {
        if (channels.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { health.probe(channels, limit) }
        }
    }

    private fun warmUpHealthChecks() {
        val ready = repo.state.value as? CatalogState.Ready ?: return
        val catalog = ready.catalog
        val pinned = favorites.value
        val recent = recents.value.toSet()
        val priority = catalog.channels.filter { it.id in pinned || it.id in recent }
        val home = catalog.byCountry[deviceCountry].orEmpty().take(80)
        probe(priority + home, limit = 100)
    }

    // ---- shared filtering helpers ----------------------------------------

    fun visible(channels: List<Channel>): List<Channel> {
        val adult = showAdult.value
        val offline = hideOffline.value
        return channels.filter { ch ->
            (adult || !ch.isNsfw) && (!offline || !health.isChannelOffline(ch))
        }
    }

    fun search(catalog: Catalog, raw: String, limit: Int = 300): List<Channel> {
        val q = raw.trim().lowercase()
        if (q.length < 2) return emptyList()
        val terms = q.split(' ').filter { it.isNotBlank() }
        val scored = ArrayList<Pair<Channel, Int>>()
        for (ch in visible(catalog.channels)) {
            if (!terms.all { ch.search.contains(it) }) continue
            val name = ch.name.lowercase()
            val score = when {
                name == q -> 0
                name.startsWith(q) -> 1
                name.contains(q) -> 2
                else -> 3
            }
            scored += ch to score
            if (scored.size > limit * 4) break
        }
        return scored.sortedWith(compareBy({ it.second }, { it.first.name.length }))
            .take(limit)
            .map { it.first }
    }
}
