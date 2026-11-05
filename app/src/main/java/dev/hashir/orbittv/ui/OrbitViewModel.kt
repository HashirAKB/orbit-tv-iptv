package dev.hashir.orbittv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.hashir.orbittv.Graph
import dev.hashir.orbittv.data.Catalog
import dev.hashir.orbittv.data.CatalogState
import dev.hashir.orbittv.data.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
